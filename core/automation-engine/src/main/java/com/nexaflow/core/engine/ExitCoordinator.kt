package com.nexaflow.core.engine

import android.util.Log
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitClaim
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.UUID

/** Outcome returned to trigger sources after they ask for a single logical exit. */
sealed interface ExitCoordinatorResult {
    data class Executed(val record: ExecutionRecord) : ExitCoordinatorResult
    data object NotActive : ExitCoordinatorResult
    data object StaleOccurrence : ExitCoordinatorResult
    data object AlreadyInProgress : ExitCoordinatorResult
    data class RecoveryRequired(val state: AutomationRuntimeState) : ExitCoordinatorResult
}

/**
 * The only coordinator permitted to turn a stateful automation occurrence into
 * its end behavior. It separates the durable transition from action execution:
 * `ACTIVE -> EXITING` is atomic; only its winner can call [ExecutionEngine]
 * and a failed exit remains `EXIT_FAILED` for a later explicit reconciliation.
 */
class ExitCoordinator(
    private val runtimeStore: AutomationRuntimeStore,
    private val executionEngine: ExecutionEngine,
    private val automationRepository: AutomationRepository,
    private val historyRepository: HistoryRepository,
    private val epochMillis: EpochMillis = EpochMillis.System
) {

    suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String? = null
    ): ExitCoordinatorResult {
        val now = epochMillis.now()
        return when (val claim = runtimeStore.claimExit(automation.id, occurrenceId, reason, now)) {
            is ExitClaim.Claimed -> executeClaimedExit(automation, claim.state, reason)
            ExitClaim.NoActiveOccurrence -> ExitCoordinatorResult.NotActive
            ExitClaim.OccurrenceMismatch -> ExitCoordinatorResult.StaleOccurrence
            ExitClaim.AlreadyExiting -> ExitCoordinatorResult.AlreadyInProgress
            is ExitClaim.RecoveryRequired -> ExitCoordinatorResult.RecoveryRequired(claim.state)
        }
    }

    /**
     * Reconciles only provable lifecycle facts. A known elapsed expected end and
     * a previously failed exit can be resumed without evaluating the current
     * trigger; an unknown trigger state never becomes an implicit end event.
     */
    suspend fun reconcile(reason: ExitReason): List<ExitCoordinatorResult> {
        val now = epochMillis.now()
        return runtimeStore.activeStates().mapNotNull { state ->
            val shouldExit = when (state.lifecycleState) {
                AutomationRuntimeLifecycleState.ACTIVE ->
                    state.expectedEndAt?.let { it <= now } == true
                AutomationRuntimeLifecycleState.EXIT_FAILED -> state.exitAttempt < MAX_EXIT_ATTEMPTS
                AutomationRuntimeLifecycleState.EXITING -> false
            }
            if (!shouldExit) {
                if (state.lifecycleState == AutomationRuntimeLifecycleState.EXIT_FAILED) {
                    Log.w(
                        TAG,
                        "Exit recovery limit reached for ${state.automationId}; retaining visible failed state"
                    )
                    return@mapNotNull ExitCoordinatorResult.RecoveryRequired(state)
                }
                return@mapNotNull null
            }

            val automation = automationRepository.getAutomationById(state.automationId)
            if (automation == null) {
                // The immutable automation definition is gone, so executing a
                // stale exit would be unsafe. Keep no orphaned active runtime.
                runtimeStore.clear(state.automationId, state.occurrenceId)
                Log.w(TAG, "Dropped runtime lifecycle for deleted automation")
                return@mapNotNull ExitCoordinatorResult.NotActive
            }

            if (state.lifecycleState == AutomationRuntimeLifecycleState.EXIT_FAILED) {
                when (val claim = runtimeStore.claimFailedExitForRecovery(
                    automationId = state.automationId,
                    occurrenceId = state.occurrenceId,
                    reason = reason,
                    now = now
                )) {
                    is ExitClaim.Claimed -> executeClaimedExit(automation, claim.state, reason)
                    ExitClaim.NoActiveOccurrence -> ExitCoordinatorResult.NotActive
                    ExitClaim.OccurrenceMismatch -> ExitCoordinatorResult.StaleOccurrence
                    ExitClaim.AlreadyExiting -> ExitCoordinatorResult.AlreadyInProgress
                    is ExitClaim.RecoveryRequired -> ExitCoordinatorResult.RecoveryRequired(claim.state)
                }
            } else {
                requestExit(automation, reason, state.occurrenceId)
            }
        }
    }

    private suspend fun executeClaimedExit(
        automation: Automation,
        state: AutomationRuntimeState,
        reason: ExitReason
    ): ExitCoordinatorResult {
        lifecycleLog(
            automationId = automation.id,
            occurrenceId = state.occurrenceId,
            event = "exit_started",
            previous = AutomationRuntimeLifecycleState.ACTIVE,
            next = AutomationRuntimeLifecycleState.EXITING,
            reason = reason
        )
        return try {
            // `forceConfiguredEnd` lets a recovered process execute the exact
            // occurrence it durably claimed even when its legacy memory marker
            // was lost. The runtime snapshot is local and bounded.
            val record = executionEngine.runExit(
                automation = automation,
                forceConfiguredEnd = true,
                runtimeSnapshotJson = state.snapshotJson
            )
            if (record.success) {
                runtimeStore.completeExit(automation.id, state.occurrenceId)
                lifecycleLog(
                    automationId = automation.id,
                    occurrenceId = state.occurrenceId,
                    event = "exit_completed",
                    previous = AutomationRuntimeLifecycleState.EXITING,
                    next = null,
                    reason = reason
                )
                ExitCoordinatorResult.Executed(record)
            } else {
                runtimeStore.failExit(
                    automationId = automation.id,
                    occurrenceId = state.occurrenceId,
                    reason = reason,
                    error = record.message,
                    now = epochMillis.now()
                )
                lifecycleLog(
                    automationId = automation.id,
                    occurrenceId = state.occurrenceId,
                    event = "exit_failed",
                    previous = AutomationRuntimeLifecycleState.EXITING,
                    next = AutomationRuntimeLifecycleState.EXIT_FAILED,
                    reason = reason
                )
                runtimeStore.current(automation.id)
                    ?.let { ExitCoordinatorResult.RecoveryRequired(it) }
                    ?: ExitCoordinatorResult.NotActive
            }
        } catch (failure: Throwable) {
            val message = "Exit execution failed: ${failure.message?.take(160) ?: failure.javaClass.simpleName}"
            runtimeStore.failExit(
                automationId = automation.id,
                occurrenceId = state.occurrenceId,
                reason = reason,
                error = message,
                now = epochMillis.now()
            )
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = false,
                message = message,
                executedAt = epochMillis.now()
            )
            runCatching { historyRepository.recordExecution(record) }
            lifecycleLog(
                automationId = automation.id,
                occurrenceId = state.occurrenceId,
                event = "exit_failed",
                previous = AutomationRuntimeLifecycleState.EXITING,
                next = AutomationRuntimeLifecycleState.EXIT_FAILED,
                reason = reason
            )
            val failed = runtimeStore.current(automation.id)
            if (failed != null) ExitCoordinatorResult.RecoveryRequired(failed)
            else ExitCoordinatorResult.NotActive
        }
    }

    private fun lifecycleLog(
        automationId: String,
        occurrenceId: String,
        event: String,
        previous: AutomationRuntimeLifecycleState?,
        next: AutomationRuntimeLifecycleState?,
        reason: ExitReason
    ) {
        // Intentionally records only opaque ids/state/reason; never trigger
        // payloads, action configs, notification content, or captured settings.
        Log.i(
            TAG,
            "[AutomationLifecycle] automationId=$automationId occurrenceId=$occurrenceId " +
                "event=$event previousState=${previous ?: "NONE"} newState=${next ?: "INACTIVE"} " +
                "reason=$reason timestamp=${epochMillis.now()}"
        )
    }

    private companion object {
        /** Initial delivery plus one bounded automatic recovery attempt. */
        const val MAX_EXIT_ATTEMPTS = 2
        const val TAG = "AutomationLifecycle"
    }
}
