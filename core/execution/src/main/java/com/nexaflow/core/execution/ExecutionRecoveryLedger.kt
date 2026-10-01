package com.nexaflow.core.execution

import com.nexaflow.core.datastore.ActiveExecutionStore

/** Read/ack facade for durable recovery evidence. It never retries work. */
internal class ExecutionRecoveryLedger(
    private val activeExecutionStore: ActiveExecutionStore,
) {
    suspend fun backlogCount(automationId: String): Int =
        activeExecutionStore.recoveryRequiredCountForAutomation(automationId)

    suspend fun reviewItems(automationId: String): List<RecoveryReviewItem> =
        activeExecutionStore.recoveryRequiredForAutomation(automationId).map { checkpoint ->
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
        }

    suspend fun clear(automationId: String): Int =
        activeExecutionStore.clearRecoveryRequiredForAutomation(automationId)
}
