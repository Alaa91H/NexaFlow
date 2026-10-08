package com.nexaflow.core.execution

import com.nexaflow.core.datastore.ActiveExecutionStore

/** Read/ack facade for durable recovery evidence. It never retries work. */
internal class ExecutionRecoveryLedger(
    private val activeExecutionStore: ActiveExecutionStore,
) {
    suspend fun backlogCount(automationId: String): Int =
        activeExecutionStore.recoveryRequiredCountForAutomation(automationId) +
            activeExecutionStore.corruptCheckpointCount()

    suspend fun reviewItems(automationId: String): List<RecoveryReviewItem> {
        val items = activeExecutionStore.recoveryRequiredForAutomation(automationId).map { checkpoint ->
            val currentNode = checkpoint.currentNodeId?.let { nodeId ->
                checkpoint.nodeExecutions.lastOrNull { it.nodeId == nodeId }
            } ?: checkpoint.nodeExecutions.lastOrNull()
            RecoveryReviewItem(
                runId = checkpoint.runId,
                startedAt = checkpoint.startedAt,
                updatedAt = checkpoint.updatedAt,
                sourceStatus = (checkpoint.recoverySourceStatus ?: checkpoint.status).name,
                nodeId = currentNode?.nodeId ?: checkpoint.currentNodeId,
                nodeState = currentNode?.state?.name,
                backend = currentNode?.backend,
                verificationState = currentNode?.verificationState?.name
                    ?: checkpoint.verificationState.name,
                failureCode = currentNode?.failureCode,
                message = checkpoint.message,
            )
        }.toMutableList()
        if (activeExecutionStore.hasCorruptCheckpoints()) {
            items += RecoveryReviewItem(
                runId = "corrupt-checkpoint-store",
                startedAt = 0L,
                updatedAt = 0L,
                sourceStatus = "CORRUPT_CHECKPOINT",
                nodeId = null,
                nodeState = "UNKNOWN",
                backend = null,
                verificationState = "UNKNOWN",
                failureCode = "CHECKPOINT_DECODE_FAILED",
                message = "Some recovery evidence is corrupt and is preserved. Review or restore the app data backup before clearing it."
                )
        }
        return items
    }

    suspend fun clear(automationId: String): Int =
        activeExecutionStore.clearRecoveryRequiredForAutomation(automationId) +
            activeExecutionStore.clearCorruptCheckpointsAfterReview()
}
