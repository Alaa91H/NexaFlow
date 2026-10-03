package com.nexaflow.core.execution

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableVerificationState
import com.nexaflow.core.execution.canonical.CanonicalNodeDispatcher
import com.nexaflow.core.execution.canonical.CanonicalNodeExecutionContext
import com.nexaflow.core.execution.workflow.WorkflowExecutionBudget
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation

internal data class CanonicalActionSequenceResult(
    val actions: List<ActionExecutionResult>,
    val checkpointRequiresRecovery: Boolean,
)

/** Owns ordered execution and durable checkpoints for canonical action nodes. */
internal class CanonicalActionSequenceExecutor(
    private val activeExecutionStore: ActiveExecutionStore,
    private val runProgressNotifier: TaskRunProgressNotifier,
    private val epochMillis: EpochMillis,
    private val dispatcher: CanonicalNodeDispatcher,
) {
    suspend fun execute(
        automation: Automation,
        nodes: List<CanonicalWorkflowNode>,
        runId: String,
        firstActionIndex: Int,
        totalActions: Int,
        progressOutcomes: MutableList<Boolean>,
        onCurrentActionChanged: (Int?) -> Unit,
    ): CanonicalActionSequenceResult {
        val results = mutableListOf<ActionExecutionResult>()
        var checkpointRequiresRecovery = false
        val budget = WorkflowExecutionBudget.create()

        for ((nodeIndex, node) in nodes.withIndex()) {
            val actionIndex = firstActionIndex + nodeIndex
            onCurrentActionChanged(actionIndex)
            val startedAt = epochMillis.now()
            runCatching {
                runProgressNotifier.update(automation, totalActions, actionIndex, progressOutcomes)
            }
            val idempotencyKey = "$runId:$actionIndex:${node.definitionId}:${node.node.id.value}"
            activeExecutionStore.markActionStarted(
                runId = runId,
                actionIndex = actionIndex,
                idempotencyKey = idempotencyKey,
                nodeId = node.node.id.value,
                updatedAt = startedAt,
            ) ?: error("Missing canonical node checkpoint")

            val outcome = dispatcher.execute(
                node,
                CanonicalNodeExecutionContext(automation.id, runId, budget),
            )
            val uncertain = outcome.status == CapabilityStatus.PARTIAL ||
                outcome.status == CapabilityStatus.PENDING_USER_ACTION
            if (uncertain) {
                checkpointRequiresRecovery = true
                activeExecutionStore.markActionUnknown(
                    runId,
                    "Canonical node ${node.node.id.value} has an unconfirmed outcome",
                    epochMillis.now(),
                ) ?: error("Unable to preserve canonical node recovery state")
            } else if (outcome.status == CapabilityStatus.SUCCESS) {
                activeExecutionStore.markActionCompleted(
                    runId = runId,
                    actionIndex = actionIndex,
                    updatedAt = epochMillis.now(),
                    verificationState = DurableVerificationState.NOT_REQUIRED,
                ) ?: error("Unable to commit canonical node completion")
            } else {
                activeExecutionStore.markActionFailed(
                    runId = runId,
                    actionIndex = actionIndex,
                    updatedAt = epochMillis.now(),
                    failureCode = "CANONICAL_NODE_FAILED",
                    verificationState = DurableVerificationState.UNKNOWN,
                ) ?: error("Unable to commit canonical node failure")
            }

            onCurrentActionChanged(null)
            results += ActionExecutionResult(
                actionType = node.definitionId,
                success = outcome.status == CapabilityStatus.SUCCESS,
                message = when (outcome.status) {
                    CapabilityStatus.SUCCESS -> "Canonical operation completed"
                    CapabilityStatus.UNSUPPORTED -> "Canonical operation is unsupported"
                    else -> "Canonical operation failed or needs recovery"
                },
                durationMs = epochMillis.now() - startedAt,
                outcomeUncertain = uncertain,
            )
            progressOutcomes += outcome.status == CapabilityStatus.SUCCESS
            if (outcome.status != CapabilityStatus.SUCCESS) break
        }

        return CanonicalActionSequenceResult(results, checkpointRequiresRecovery)
    }
}
