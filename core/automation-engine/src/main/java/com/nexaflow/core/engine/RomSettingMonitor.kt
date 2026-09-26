package com.nexaflow.core.engine

import android.content.Context
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.rom.CustomSettingsBridge
import com.nexaflow.core.rom.CustomSettingsBridge.Namespace
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Polls real ROM settings while keeping stateful ownership durable across
 * process/service restarts.
 *
 * A nullable settings-provider read is UNKNOWN, never a false condition. This
 * matters especially for NOT_EQUALS: a missing/unreadable value must not be
 * interpreted as "different" and must not activate or end a lifecycle.
 */
@Singleton
class RomSettingMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var pollingJob: Job? = null

    private val lastRunAt = mutableMapOf<String, Long>()
    private val pollMutex = Mutex()

    @Synchronized
    fun initialize() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Reconcile enable/disable/edit changes without waiting for the next poll. */
    fun reconcileAutomations() {
        scope.launch { pollOnce() }
    }

    @Synchronized
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Test seam for verifying that service shutdown cancels the polling loop. */
    internal fun isPollingForTest(): Boolean = pollingJob?.isActive == true

    private suspend fun pollOnce() = pollMutex.withLock {
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        // Restore authoritative runtime ownership and migrate legacy markers
        // before interpreting the current provider snapshot.
        rearmFromLedger(byId)

        // Unsupported ROM family means the provider state is not a usable
        // signal. Preserve any existing lifecycle; do not infer false.
        if (!runCatching { CustomSettingsBridge.supportsCustomSettings(context) }.getOrDefault(false)) {
            return@withLock
        }

        val now = System.currentTimeMillis()
        automations
            .filter {
                it.enabled && it.triggers.any { trigger ->
                    trigger.type == TriggerType.ROM_SETTING
                }
            }
            .forEach { automation ->
                val triggers = automation.triggers.filter { it.type == TriggerType.ROM_SETTING }
                val state = evaluateRomSettingTriggers(triggers) { namespace, key ->
                    CustomSettingsBridge.read(context, namespace, key)
                }
                val current = runtimeStore.current(automation.id)

                when (state) {
                    RomSettingReadState.MATCHED -> when {
                        current?.source == SOURCE -> markLegacyActive(current)
                        current != null -> clearLegacyState(automation.id)
                        else -> {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > automation.cooldownMillis) {
                                lastRunAt[automation.id] = now
                                activate(automation)
                            }
                        }
                    }

                    RomSettingReadState.NOT_MATCHED -> {
                        if (current?.source == SOURCE) {
                            requestExit(
                                automation = automation,
                                reason = ExitReason.TRIGGER_FALSE,
                                occurrenceId = current.occurrenceId
                            )
                        } else if (current == null) {
                            clearLegacyState(automation.id)
                        }
                    }

                    RomSettingReadState.UNKNOWN -> {
                        // Transient provider/permission/OEM read failure:
                        // preserve earned ownership and never fabricate an exit.
                        if (current?.source == SOURCE) {
                            markLegacyActive(current)
                        } else if (current == null) {
                            clearLegacyState(automation.id)
                        }
                    }
                }
            }
    }

    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> {
                        // No immutable definition means no safe exit can be
                        // reconstructed. Keep durable evidence for recovery.
                        clearLegacyState(state.automationId)
                    }

                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.ROM_SETTING } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }

                    else -> markLegacyActive(state)
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

            val current = runtimeStore.current(automationId)
            if (current?.source == SOURCE) {
                if (!automation.enabled ||
                    automation.triggers.none { it.type == TriggerType.ROM_SETTING }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = current.occurrenceId
                    )
                } else {
                    markLegacyActive(current)
                }
            } else {
                // Another stateful source owns the routine. A stale ROM-setting
                // marker must not authorize that foreign occurrence's exit.
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activate(automation: Automation) {
        val occurrenceId = "rom-setting:${automation.id}:${UUID.randomUUID()}"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = automation.id
            )
        )
        runtimeStore.current(automation.id)
            ?.takeIf { it.source == SOURCE && it.occurrenceId == occurrenceId }
            ?.let { markLegacyActive(it) }
            ?: clearLegacyState(automation.id)
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

    private suspend fun markLegacyActive(state: AutomationRuntimeState) {
        activeStore.markActive(SOURCE, state.sourceKey)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStore.clearAutomation(SOURCE, automationId)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val SOURCE = "rom_setting"
    }
}

/** Parses the configured namespace; missing/bad values default to SYSTEM. */
internal fun romSettingNamespaceOf(config: Map<String, String>): Namespace =
    Namespace.entries.firstOrNull { it.name == (config["namespace"] ?: "SYSTEM") } ?: Namespace.SYSTEM

/** The target value the ROM setting must reach for the trigger to fire. */
internal fun romSettingTargetOf(config: Map<String, String>): String? =
    config["value"]?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Pure matcher for a known provider value. Null is UNKNOWN, never a match —
 * including NOT_EQUALS — because null also represents a failed/unavailable
 * provider read.
 */
internal fun romSettingMatches(trigger: Trigger, actual: String?): Boolean {
    val target = romSettingTargetOf(trigger.config) ?: return false
    if (actual == null) return false
    return when (trigger.config["operator"] ?: "EQUALS") {
        "NOT_EQUALS" -> actual != target
        else -> actual == target
    }
}

internal enum class RomSettingReadState {
    MATCHED,
    NOT_MATCHED,
    UNKNOWN
}

/**
 * Aggregates the legacy "any ROM_SETTING trigger may activate" semantics while
 * preserving UNKNOWN provider reads. A known match dominates; if none match
 * and at least one valid trigger could not be read, the result is UNKNOWN.
 */
internal fun evaluateRomSettingTriggers(
    triggers: List<Trigger>,
    read: (Namespace, String) -> String?
): RomSettingReadState {
    var sawUnknown = false
    var sawValid = false

    triggers.forEach { trigger ->
        val target = romSettingTargetOf(trigger.config) ?: return@forEach
        val key = trigger.config["key"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        sawValid = true
        val actual = read(romSettingNamespaceOf(trigger.config), key)
        if (actual == null) {
            sawUnknown = true
            return@forEach
        }
        if (when (trigger.config["operator"] ?: "EQUALS") {
                "NOT_EQUALS" -> actual != target
                else -> actual == target
            }
        ) {
            return RomSettingReadState.MATCHED
        }
    }

    return when {
        sawUnknown -> RomSettingReadState.UNKNOWN
        sawValid -> RomSettingReadState.NOT_MATCHED
        else -> RomSettingReadState.NOT_MATCHED
    }
}
