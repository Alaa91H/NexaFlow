package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.admitDebouncedTrigger
import com.nexaflow.core.execution.evaluateTriggerThreshold
import com.nexaflow.core.execution.evaluateTriggerStability
import com.nexaflow.core.execution.observeDebouncedTrigger
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.core.execution.TriggerStabilityRecheckQueue
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.schedule.TriggerFilterReason
import com.nexaflow.domain.schedule.TriggerFilterDecision
import com.nexaflow.domain.schedule.TriggerTemporalFilterConfigParser
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stateful VOLUME_CHANGED trigger.
 *
 * The user-facing threshold is a percentage (0..100), not AudioManager's raw
 * stream index. Durable ownership lives in [AutomationRuntimeStore];
 * [ActiveTriggerStore] remains only as a compatibility mirror.
 */
@Singleton
class VolumeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val evaluationMutex = Mutex()
    private val stabilityRechecks = TriggerStabilityRecheckQueue(scope)
    private val debounceRechecks = TriggerStabilityRecheckQueue(scope)

    private val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handleVolumeChanged()
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        scope.launch {
            // Restore earned ownership before any platform callback can be
            // interpreted as a fresh activation after process death.
            rearmFromLedger()
            if (!registered) return@launch

            val callbackRegistered = runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    observer
                )
                true
            }.getOrDefault(false)
            if (!callbackRegistered) {
                registered = false
                return@launch
            }

            // Reconcile immediately: the threshold may have been crossed while
            // the process was dead, and a newly enabled routine should not wait
            // for the next settings notification.
            reconcileCurrentState()
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    /** Re-evaluates saved/edited routines against the current stream levels. */
    fun reconcileAutomations() {
        scope.launch { reconcileCurrentState() }
    }

    private fun handleVolumeChanged() {
        scope.launch { reconcileCurrentState() }
    }

    private suspend fun reconcileCurrentState() {
        reconcileSnapshot(readCurrentSnapshot())
    }

    /**
     * Deterministic lifecycle boundary used by production callbacks and tests.
     * A null percentage means the platform state was unreadable; unreadable
     * state must never be interpreted as the condition ending.
     */
    internal suspend fun reconcileSnapshot(percentages: Map<String, Int?>) =
        evaluationMutex.withLock {
            val automations = repository.getAutomations().first()
            val byId = automations.associateBy { it.id }
            val retainedDebounceKeys = automations.asSequence()
                .filter { it.enabled }
                .flatMap { automation ->
                    automation.triggers.mapIndexedNotNull { index, trigger ->
                        val config = (TriggerTemporalFilterConfigParser.parse(trigger.config)
                            as? com.nexaflow.domain.schedule.TriggerFilterConfigParse.Valid)?.config
                        if (trigger.type == TriggerType.VOLUME_CHANGED && config?.debounceMs != null) {
                            debounceKey(automation, index)
                        } else null
                    }
                }
                .toSet()
            debounceRechecks.cancelExcept(retainedDebounceKeys)

            // Resolve definitions that changed while a volume occurrence was
            // active before evaluating the current threshold.
            runtimeStore.activeStates()
                .filter { it.source == SOURCE }
                .forEach { state ->
                    val automation = byId[state.automationId]
                    when {
                        automation == null -> {
                            // Without the immutable definition there is no safe
                            // exit to invent. Keep the durable evidence visible.
                            clearLegacyState(state.automationId)
                            debounceRechecks.cancelPrefix("${state.automationId}:")
                        }
                        !automation.enabled ||
                            automation.triggers.none { it.type == TriggerType.VOLUME_CHANGED } -> {
                            debounceRechecks.cancelPrefix("${automation.id}:")
                            requestExit(
                                automation = automation,
                                reason = ExitReason.AUTOMATION_DISABLED,
                                occurrenceId = state.occurrenceId
                            )
                        }
                        else -> markLegacyActive(state)
                    }
                }

            automations
                .filter {
                    it.enabled && it.triggers.any { trigger ->
                        trigger.type == TriggerType.VOLUME_CHANGED
                    }
                }
                .forEach { automation ->
                    val triggerIndex = automation.triggers.indexOfFirst { it.type == TriggerType.VOLUME_CHANGED }
                    val trigger = automation.triggers[triggerIndex]
                    val streamName = configuredStream(trigger)
                    val percentage = percentages[streamName]
                    val state = runtimeStore.current(automation.id)
                    val recheckKey = "${automation.id}:$triggerIndex"

                    if (percentage == null) {
                        stabilityRechecks.cancel(recheckKey)
                        debounceRechecks.cancel(recheckKey)
                        executionEngine.evaluateTriggerStability(automation, triggerIndex, ConditionResult.Unknown)
                        if (state?.source == SOURCE) markLegacyActive(state)
                        else if (state == null) clearLegacyState(automation.id)
                        return@forEach
                    }

                    val threshold = (trigger.config["threshold"] ?: DEFAULT_THRESHOLD.toString())
                        .toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: DEFAULT_THRESHOLD.toDouble()
                    val above = (trigger.config["direction"] ?: "ABOVE") != "BELOW"
                    val observation = if ("hysteresis" in trigger.config) {
                        executionEngine.evaluateTriggerThreshold(
                            automation, triggerIndex, percentage.toDouble(), threshold, above,
                        )
                    } else if (matches(trigger, percentage)) ConditionResult.Satisfied
                    else ConditionResult.Unsatisfied
                    val stable = executionEngine.evaluateTriggerStability(automation, triggerIndex, observation)
                    val stableForMs = (TriggerTemporalFilterConfigParser.parse(trigger.config)
                        as? com.nexaflow.domain.schedule.TriggerFilterConfigParse.Valid)?.config?.stableForMs
                    if (stable.reason == TriggerFilterReason.STABILITY_PENDING && stableForMs != null) {
                        stabilityRechecks.schedule(recheckKey, stableForMs) { reconcileAutomations() }
                    } else {
                        stabilityRechecks.cancel(recheckKey)
                    }
                    if (stable.result != ConditionResult.Satisfied) debounceRechecks.cancel(recheckKey)

                    when (stable.result) {
                        ConditionResult.Satisfied -> when {
                            state?.source == SOURCE -> markLegacyActive(state)
                            state != null -> clearLegacyState(automation.id)
                            else -> activate(automation, streamName, triggerIndex)
                        }
                        ConditionResult.Unsatisfied -> if (state?.source == SOURCE) {
                            requestExit(automation, ExitReason.TRIGGER_FALSE, state.occurrenceId)
                        } else clearLegacyState(automation.id)
                        else -> if (state?.source == SOURCE) markLegacyActive(state)
                    }
                }
        }

    private suspend fun activate(automation: Automation, streamName: String, triggerIndex: Int) {
        val occurrenceId = "volume:${automation.id}:${UUID.randomUUID()}"
        val temporalConfig = TriggerTemporalFilterConfigParser.parse(automation.triggers[triggerIndex].config)
            as? com.nexaflow.domain.schedule.TriggerFilterConfigParse.Valid
        val debounceMs = temporalConfig?.config?.debounceMs
        if (debounceMs != null) {
            if (executionEngine.observeDebouncedTrigger(automation, triggerIndex) != TriggerFilterDecision.Allowed) return
            val key = debounceKey(automation, triggerIndex)
            debounceRechecks.replace(key, debounceMs) {
                runDebouncedIfStillSatisfied(automation, streamName, triggerIndex, occurrenceId)
            }
            return
        }
        runVolumeAutomation(automation, streamName, triggerIndex, occurrenceId)
    }

    private fun debounceKey(automation: Automation, triggerIndex: Int): String {
        val fingerprint = automation.triggers[triggerIndex].config.toSortedMap().hashCode().toUInt().toString(16)
        return "${automation.id}:$triggerIndex:$fingerprint"
    }

    private suspend fun runDebouncedIfStillSatisfied(
        scheduledAutomation: Automation,
        streamName: String,
        triggerIndex: Int,
        occurrenceId: String,
    ) {
        val automation = repository.getAutomations().first()
            .firstOrNull { it.id == scheduledAutomation.id && it.enabled } ?: return
        val scheduledTrigger = scheduledAutomation.triggers.getOrNull(triggerIndex) ?: return
        val trigger = automation.triggers.getOrNull(triggerIndex)
            ?.takeIf { it.type == TriggerType.VOLUME_CHANGED && it == scheduledTrigger } ?: return
        val percentage = readCurrentSnapshot()[streamName]
        if (percentage == null) {
            executionEngine.evaluateTriggerStability(automation, triggerIndex, ConditionResult.Unknown)
            return
        }
        val threshold = (trigger.config["threshold"] ?: DEFAULT_THRESHOLD.toString())
            .toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: DEFAULT_THRESHOLD.toDouble()
        val above = (trigger.config["direction"] ?: "ABOVE") != "BELOW"
        val observation = if ("hysteresis" in trigger.config) {
            executionEngine.evaluateTriggerThreshold(automation, triggerIndex, percentage.toDouble(), threshold, above)
        } else if (matches(trigger, percentage)) ConditionResult.Satisfied else ConditionResult.Unsatisfied
        if (executionEngine.evaluateTriggerStability(automation, triggerIndex, observation).result != ConditionResult.Satisfied) return
        if (executionEngine.admitDebouncedTrigger(automation, triggerIndex) == TriggerFilterDecision.Allowed) {
            scope.launch {
                runVolumeAutomation(automation, streamName, triggerIndex, occurrenceId, debounceAdmitted = true)
            }
        }
    }

    private suspend fun runVolumeAutomation(
        automation: Automation,
        streamName: String,
        triggerIndex: Int,
        occurrenceId: String,
        debounceAdmitted: Boolean = false,
    ) {
        val sourceKey = "${automation.id}|$streamName"
        executionEngine.runAutomation(
            automation = automation,
            triggerOccurrence = TriggerOccurrence.single(
                triggerIndex = triggerIndex,
                occurredAtEpochMs = System.currentTimeMillis(),
                sourceId = SOURCE,
                eventId = occurrenceId,
                debounceAdmitted = debounceAdmitted,
            ),
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            )
        )
        val state = runtimeStore.current(automation.id)
        if (state?.source == SOURCE && state.occurrenceId == occurrenceId) {
            markLegacyActive(state)
        } else {
            clearLegacyState(automation.id)
        }
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { markLegacyActive(it) }
            }
        }
    }

    /**
     * Restores runtime-ledger ownership and upgrades old volume markers once.
     * Legacy markers contain no captured restore state, so migration never
     * fabricates one.
     */
    internal suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> clearLegacyState(state.automationId)
                    automation.enabled &&
                        automation.triggers.any { it.type == TriggerType.VOLUME_CHANGED } ->
                        markLegacyActive(state)
                    else -> requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = state.occurrenceId
                    )
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                clearLegacyState(automationId)
                return@forEach
            }

            if (runtimeStore.current(automationId) == null) {
                val streamName = automation.triggers
                    .firstOrNull { it.type == TriggerType.VOLUME_CHANGED }
                    ?.let(::configuredStream)
                    ?: DEFAULT_STREAM
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = key.takeIf { it.contains('|') }
                            ?: "$automationId|$streamName",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                markLegacyActive(state)
                if (!automation.enabled ||
                    automation.triggers.none { it.type == TriggerType.VOLUME_CHANGED }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = state.occurrenceId
                    )
                }
            } else {
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun markLegacyActive(state: AutomationRuntimeState) {
        activeStore.markActive(SOURCE, state.sourceKey)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStore.clearAutomation(SOURCE, automationId)
    }

    private fun readCurrentSnapshot(): Map<String, Int?> {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return STREAMS.associateWith { null }
        return STREAMS.associateWith { streamName ->
            val stream = streamType(streamName)
            runCatching {
                val level = audio.getStreamVolume(stream)
                val minimum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audio.getStreamMinVolume(stream)
                } else {
                    0
                }
                val maximum = audio.getStreamMaxVolume(stream)
                normalizedVolumePercent(level, minimum, maximum)
            }.getOrNull()
        }
    }

    private fun matches(trigger: Trigger, percentage: Int): Boolean {
        val threshold = trigger.config["threshold"]
            ?.toIntOrNull()
            ?.coerceIn(0, 100)
            ?: DEFAULT_THRESHOLD
        return if ((trigger.config["direction"] ?: "ABOVE") == "BELOW") {
            percentage <= threshold
        } else {
            percentage >= threshold
        }
    }

    private fun configuredStream(trigger: Trigger): String =
        (trigger.config["stream"] ?: DEFAULT_STREAM)
            .takeIf { it in STREAMS }
            ?: DEFAULT_STREAM

    private fun streamType(streamName: String): Int = when (streamName) {
        "RING" -> AudioManager.STREAM_RING
        "ALARM" -> AudioManager.STREAM_ALARM
        "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
        else -> AudioManager.STREAM_MUSIC
    }

    private companion object {
        const val SOURCE = "volume"
        const val DEFAULT_STREAM = "MUSIC"
        const val DEFAULT_THRESHOLD = 50
        val STREAMS = listOf("MUSIC", "RING", "ALARM", "NOTIFICATION")
    }
}

/** Converts an AudioManager stream index to the user-facing 0..100 scale. */
internal fun normalizedVolumePercent(level: Int, minimum: Int, maximum: Int): Int? {
    if (maximum <= minimum) return null
    val bounded = level.coerceIn(minimum, maximum)
    return (((bounded - minimum).toDouble() / (maximum - minimum)) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}
