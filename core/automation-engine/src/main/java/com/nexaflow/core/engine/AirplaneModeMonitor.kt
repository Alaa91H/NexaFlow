package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stateful AIRPLANE_MODE trigger.
 *
 * Durable ownership is held in [AutomationRuntimeStore]. [ActiveTriggerStore]
 * remains only as a compatibility mirror for pre-runtime-ledger installs.
 */
@Singleton
class AirplaneModeMonitor @Inject constructor(
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                scope.launch {
                    reconcileState(intent.getBooleanExtra("state", false))
                }
            }
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        }.onFailure {
            registered = false
            return
        }
        reconcileAutomations()
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * Re-evaluates saved/edited tasks against the current airplane-mode state.
     * Restart recovery happens before the current state is allowed to activate
     * or exit anything.
     */
    fun reconcileAutomations() {
        scope.launch {
            val on = runCatching {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) == 1
            }.getOrNull() ?: return@launch
            reconcileState(on)
        }
    }

    /** Deterministic lifecycle boundary shared by platform callbacks and tests. */
    internal suspend fun reconcileState(on: Boolean) = evaluationMutex.withLock {
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        rearmFromLedger(byId)

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = byId[state.automationId]
                when {
                    automation == null -> {
                        // The immutable definition is unavailable, so no safe
                        // end behavior can be reconstructed. Keep durable
                        // evidence and drop only the compatibility mirror.
                        clearLegacyState(state.automationId)
                    }
                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.AIRPLANE_MODE } -> {
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
                    trigger.type == TriggerType.AIRPLANE_MODE
                }
            }
            .forEach { automation ->
                val trigger = automation.triggers.first {
                    it.type == TriggerType.AIRPLANE_MODE
                }
                val wantOn = (trigger.config["state"] ?: "ON") == "ON"
                val state = runtimeStore.current(automation.id)

                if (on == wantOn) {
                    when {
                        state?.source == SOURCE -> markLegacyActive(state)
                        state != null -> clearLegacyState(automation.id)
                        else -> activate(automation, wantOn)
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

    /**
     * Promotes old ActiveTriggerStore markers into durable occurrence ownership
     * before the current state is evaluated.
     */
    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                if (automations[state.automationId] != null) {
                    markLegacyActive(state)
                } else {
                    clearLegacyState(state.automationId)
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
                val wantOn = automation.triggers
                    .firstOrNull { it.type == TriggerType.AIRPLANE_MODE }
                    ?.config
                    ?.get("state")
                    ?.let { it == "ON" }
                    ?: true
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = key.takeIf { it.contains('|') }
                            ?: "$automationId|${if (wantOn) "ON" else "OFF"}",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val state = runtimeStore.current(automationId)
            if (state?.source == SOURCE) {
                markLegacyActive(state)
            } else {
                // Another stateful trigger owns this automation. A stale
                // airplane marker must never authorize an exit for it.
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activate(automation: Automation, wantOn: Boolean) {
        val occurrenceId = "airplane:${automation.id}:${UUID.randomUUID()}"
        val sourceKey = "${automation.id}|${if (wantOn) "ON" else "OFF"}"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
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
        const val SOURCE = "airplane"
    }
}
