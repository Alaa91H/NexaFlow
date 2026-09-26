package com.nexaflow.core.execution.recovery

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.domain.repositories.AutomationRepository

/** Recovery action selected from durable state; no action payload is replayed implicitly. */
enum class RecoveryDisposition {
    SAFE_RESUME_CANDIDATE,
    VERIFY_OR_COMPENSATE_REQUIRED,
    EXIT_RECONCILIATION_REQUIRED,
    MANUAL_DIAGNOSTICS_REQUIRED
}

data class RecoveryItem(
    val checkpoint: DurableExecutionCheckpoint,
    val disposition: RecoveryDisposition,
    val reason: String
)

data class RecoveryReport(val items: List<RecoveryItem>) {
    val claimedCount: Int get() = items.size
}

/**
 * Claims interrupted runs once at process start and converts them to explicit
 * recovery states. Action-started/unknown checkpoints never auto-replay: their
 * side effect may already have happened. A future workflow-aware resumer can
 * execute only [SAFE_RESUME_CANDIDATE] records after loading the immutable task
 * definition and validating its version/capabilities.
 */
class ExecutionRecoveryCoordinator(
    private val activeExecutionStore: ActiveExecutionStore,
    private val automationRepository: AutomationRepository? = null,
    private val epochMillis: EpochMillis = EpochMillis.System
) {

    suspend fun reconcileStartup(): RecoveryReport {
        val now = epochMillis.now()
        val claimed = activeExecutionStore.claimRecoveryCandidates(now)
        val items = ArrayList<RecoveryItem>(claimed.size)
        for (checkpoint in claimed) {
            val item = classify(checkpoint)
            activeExecutionStore.markRecoveryRequired(
                runId = checkpoint.runId,
                message = item.reason,
                updatedAt = now
            )
            items += item
        }
        return RecoveryReport(items)
    }

    private suspend fun classify(checkpoint: DurableExecutionCheckpoint): RecoveryItem {
        val source = checkpoint.recoverySourceStatus
        return when (source) {
            DurableExecutionStatus.STARTED,
            DurableExecutionStatus.ACTION_COMPLETED -> classifyResumeBoundary(checkpoint)

            DurableExecutionStatus.ACTION_STARTED,
            DurableExecutionStatus.ACTION_UNKNOWN -> RecoveryItem(
                checkpoint = checkpoint,
                disposition = RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED,
                reason = "Action ${checkpoint.nextActionIndex} may have side effects; verify or compensate before any replay"
            )

            DurableExecutionStatus.EXIT_PENDING -> RecoveryItem(
                checkpoint = checkpoint,
                disposition = RecoveryDisposition.EXIT_RECONCILIATION_REQUIRED,
                reason = "Exit lifecycle was pending; reconcile trigger state before running end behavior"
            )

            DurableExecutionStatus.RECOVERY_REQUIRED,
            DurableExecutionStatus.RECOVERY_CLAIMED,
            DurableExecutionStatus.COMPLETED,
            null -> RecoveryItem(
                checkpoint = checkpoint,
                disposition = RecoveryDisposition.MANUAL_DIAGNOSTICS_REQUIRED,
                reason = "Checkpoint state requires diagnostic review"
            )
        }
    }

    /**
     * A durable boundary is only a *candidate* for workflow resume when the
     * exact persisted automation definition that admitted the run is still
     * available. Schema compatibility alone is not sufficient: an edit can
     * change actions while leaving workflowVersion unchanged.
     */
    private suspend fun classifyResumeBoundary(
        checkpoint: DurableExecutionCheckpoint
    ): RecoveryItem {
        val repository = automationRepository
            ?: return manual(
                checkpoint,
                "Workflow definition validation is unavailable; do not resume automatically"
            )
        val automation = repository.getAutomationById(checkpoint.automationId)
            ?: return manual(
                checkpoint,
                "Automation definition is missing; keep the checkpoint for manual recovery"
            )
        val revision = checkpoint.workflowRevision
            ?: return manual(
                checkpoint,
                "Legacy checkpoint has no pinned automation revision; automatic resume is unsafe"
            )
        if (automation.workflowVersion != checkpoint.workflowVersion) {
            return manual(
                checkpoint,
                "Workflow schema version changed since execution admission"
            )
        }
        if (automation.updatedAt != revision) {
            return manual(
                checkpoint,
                "Automation definition changed since execution admission"
            )
        }
        if (automation.actions.size != checkpoint.totalActions ||
            checkpoint.nextActionIndex !in 0..automation.actions.size
        ) {
            return manual(
                checkpoint,
                "Automation action topology no longer matches the durable checkpoint"
            )
        }
        return RecoveryItem(
            checkpoint = checkpoint,
            disposition = RecoveryDisposition.SAFE_RESUME_CANDIDATE,
            reason = "Run ended after a validated durable boundary; workflow-aware resume may continue at action ${checkpoint.nextActionIndex}"
        )
    }

    private fun manual(
        checkpoint: DurableExecutionCheckpoint,
        reason: String
    ): RecoveryItem = RecoveryItem(
        checkpoint = checkpoint,
        disposition = RecoveryDisposition.MANUAL_DIAGNOSTICS_REQUIRED,
        reason = reason
    )
}
}
