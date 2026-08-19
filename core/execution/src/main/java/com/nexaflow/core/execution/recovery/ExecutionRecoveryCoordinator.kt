package com.nexaflow.core.execution.recovery

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus

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
    private val epochMillis: EpochMillis = EpochMillis.System
) {

    suspend fun reconcileStartup(): RecoveryReport {
        val now = epochMillis.now()
        val claimed = activeExecutionStore.claimRecoveryCandidates(now)
        val items = claimed.map { checkpoint ->
            val item = classify(checkpoint)
            activeExecutionStore.markRecoveryRequired(
                runId = checkpoint.runId,
                message = item.reason,
                updatedAt = now
            )
            item
        }
        return RecoveryReport(items)
    }

    private fun classify(checkpoint: DurableExecutionCheckpoint): RecoveryItem {
        val source = checkpoint.recoverySourceStatus
        val (disposition, reason) = when (source) {
            DurableExecutionStatus.STARTED,
            DurableExecutionStatus.ACTION_COMPLETED -> RecoveryDisposition.SAFE_RESUME_CANDIDATE to
                "Run ended after a durable boundary; workflow-aware resume may continue at action ${checkpoint.nextActionIndex}"

            DurableExecutionStatus.ACTION_STARTED,
            DurableExecutionStatus.ACTION_UNKNOWN -> RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED to
                "Action ${checkpoint.nextActionIndex} may have side effects; verify or compensate before any replay"

            DurableExecutionStatus.EXIT_PENDING -> RecoveryDisposition.EXIT_RECONCILIATION_REQUIRED to
                "Exit lifecycle was pending; reconcile trigger state before running end behavior"

            DurableExecutionStatus.RECOVERY_REQUIRED,
            DurableExecutionStatus.RECOVERY_CLAIMED,
            DurableExecutionStatus.COMPLETED,
            null -> RecoveryDisposition.MANUAL_DIAGNOSTICS_REQUIRED to
                "Checkpoint state requires diagnostic review"
        }
        return RecoveryItem(checkpoint, disposition, reason)
    }
}
