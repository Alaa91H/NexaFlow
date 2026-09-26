package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stateful MEDIA_PLAYING monitor.
 *
 * The durable runtime ledger owns whether a media occurrence still has an exit
 * to execute. [ActiveTriggerStore] is only a compatibility mirror and is never
 * cleared before [ExitCoordinator] confirms the exit completed or was already
 * inactive.
 */
@Singleton
class MediaMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    private val runtimeStore: AutomationRuntimeStore,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Same-process mirror only; durable ownership lives in [runtimeStore]. */
    private val activeStates: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val evaluationMutex = Mutex()

    @Volatile
    private var lastPlaying: Boolean? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
            // A config callback is not itself proof of a start/stop transition.
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val playing = runCatching { audio.isMusicActive }.getOrNull() ?: return
            if (playing == lastPlaying) return
            lastPlaying = playing
            handleState(playing)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        scope.launch {
            // Restore durable ownership before consuming the current playback
            // state. A process restart must not turn an earned exit into a new
            // main-action run.
            rearmFromLedger()
            if (!registered) return@launch

            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio == null) {
                registered = false
                return@launch
            }
            val callbackRegistered = runCatching {
                audio.registerAudioPlaybackCallback(playbackCallback, null)
                true
            }.getOrDefault(false)
            if (!callbackRegistered) {
                registered = false
                return@launch
            }

            // Evaluate the current level immediately. Playback may have ended
            // while the process was down, and enabling a task while its desired
            // state already holds should not wait for another transition.
            runCatching { audio.isMusicActive }.getOrNull()?.let { playing ->
                lastPlaying = playing
                reconcilePlaybackState(playing)
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        lastPlaying = null
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audio.unregisterAudioPlaybackCallback(playbackCallback) }
    }

    /** Re-evaluates edits/toggles against the current playback state. */
    fun reconcileAutomations() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val playing = runCatching { audio.isMusicActive }.getOrNull() ?: return
        lastPlaying = playing
        handleState(playing)
    }

    private fun handleState(playing: Boolean) {
        scope.launch { reconcilePlaybackState(playing) }
    }

    /**
     * Deterministic lifecycle boundary used by the Android callback and unit
     * tests. Calls are serialized so an opposite playback callback cannot race
     * activation and clear ownership before it is durably admitted.
     */
    internal suspend fun reconcilePlaybackState(playing: Boolean) = evaluationMutex.withLock {
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        // First reconcile already-owned media lifecycles that were disabled,
        // deleted, or edited to remove the trigger.
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = byId[state.automationId]
                when {
                    automation == null -> {
                        runtimeStore.clear(state.automationId, state.occurrenceId)
                        clearLegacyState(state.automationId)
                    }
                    !automation.enabled || automation.triggers.none { it.type == TriggerType.MEDIA_PLAYING } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                    else -> markLegacyActive(state.automationId)
                }
            }

        automations
            .filter { it.enabled && it.triggers.any { trigger -> trigger.type == TriggerType.MEDIA_PLAYING } }
            .forEach { automation ->
                val trigger = automation.triggers.first { it.type == TriggerType.MEDIA_PLAYING }
                val wantPlaying = (trigger.config["event"] ?: "STARTED") == "STARTED"
                val conditionSatisfied = playing == wantPlaying
                val state = runtimeStore.current(automation.id)

                if (conditionSatisfied) {
                    when {
                        state?.source == SOURCE -> markLegacyActive(automation.id)
                        state != null -> clearLegacyState(automation.id)
                        else -> activate(automation)
                    }
                } else if (state?.source == SOURCE) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.TRIGGER_FALSE,
                        occurrenceId = state.occurrenceId
                    )
                } else {
                    clearLegacyState(automation.id)
                }
            }
    }

    private suspend fun activate(automation: Automation) {
        val occurrenceId = "media:${automation.id}:${UUID.randomUUID()}"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = automation.id
            )
        )
        val admitted = runtimeStore.current(automation.id)?.let { state ->
            state.occurrenceId == occurrenceId && state.source == SOURCE
        } == true
        if (admitted) {
            markLegacyActive(automation.id)
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
            is ExitCoordinatorResult.RecoveryRequired -> markLegacyActive(automation.id)
        }
    }

    /**
     * Restores durable state and promotes the old ActiveTriggerStore marker
     * once. Legacy markers do not contain a restore snapshot, so no snapshot is
     * invented during migration.
     */
    private suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> {
                        runtimeStore.clear(state.automationId, state.occurrenceId)
                        clearLegacyState(state.automationId)
                    }
                    automation.enabled && automation.triggers.any { it.type == TriggerType.MEDIA_PLAYING } ->
                        markLegacyActive(state.automationId)
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
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = automationId,
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                markLegacyActive(automationId)
                if (!automation.enabled || automation.triggers.none { it.type == TriggerType.MEDIA_PLAYING }) {
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

    private suspend fun markLegacyActive(automationId: String) {
        activeStates.add(automationId)
        activeStore.markActive(SOURCE, automationId)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStates.remove(automationId)
        activeStore.clearAutomation(SOURCE, automationId)
    }

    private companion object {
        const val SOURCE = "media"
    }
}
