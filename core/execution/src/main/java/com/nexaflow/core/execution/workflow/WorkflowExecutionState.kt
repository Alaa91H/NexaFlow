package com.nexaflow.core.execution.workflow

/** Canonical durable-facing state for one workflow run. */
enum class WorkflowExecutionState {
    ADMITTED,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    RECOVERY_REQUIRED
}

/** Canonical durable-facing state for one node visit. */
enum class NodeExecutionState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    COMPENSATING,
    COMPENSATED
}

/** Strict state transition contract shared by adapters and durable coordinators. */
fun WorkflowExecutionState.canTransitionTo(next: WorkflowExecutionState): Boolean = when (this) {
    WorkflowExecutionState.ADMITTED -> next == WorkflowExecutionState.RUNNING
    WorkflowExecutionState.RUNNING -> next in setOf(
        WorkflowExecutionState.WAITING,
        WorkflowExecutionState.SUCCEEDED,
        WorkflowExecutionState.FAILED,
        WorkflowExecutionState.UNKNOWN
    )
    WorkflowExecutionState.WAITING -> next in setOf(
        WorkflowExecutionState.RUNNING,
        WorkflowExecutionState.FAILED,
        WorkflowExecutionState.UNKNOWN
    )
    WorkflowExecutionState.UNKNOWN -> next == WorkflowExecutionState.RECOVERY_REQUIRED
    WorkflowExecutionState.RECOVERY_REQUIRED -> next == WorkflowExecutionState.RECOVERY_REQUIRED
    WorkflowExecutionState.SUCCEEDED -> next == WorkflowExecutionState.SUCCEEDED
    WorkflowExecutionState.FAILED -> next == WorkflowExecutionState.FAILED
}

fun NodeExecutionState.canTransitionTo(next: NodeExecutionState): Boolean = when (this) {
    NodeExecutionState.PENDING -> next == NodeExecutionState.RUNNING
    NodeExecutionState.RUNNING -> next in setOf(
        NodeExecutionState.SUCCEEDED,
        NodeExecutionState.FAILED,
        NodeExecutionState.UNKNOWN,
        NodeExecutionState.COMPENSATING
    )
    NodeExecutionState.FAILED -> next == NodeExecutionState.COMPENSATING
    NodeExecutionState.COMPENSATING -> next == NodeExecutionState.COMPENSATED
    NodeExecutionState.SUCCEEDED -> next == NodeExecutionState.SUCCEEDED
    NodeExecutionState.UNKNOWN -> next == NodeExecutionState.UNKNOWN
    NodeExecutionState.COMPENSATED -> next == NodeExecutionState.COMPENSATED
}

/** Repository-wide adapter for truthful result reporting. */
fun WorkflowExecutionResult.canonicalState(): WorkflowExecutionState = when {
    success -> WorkflowExecutionState.SUCCEEDED
    nodeResults.any { it.message.contains("unknown", ignoreCase = true) } -> WorkflowExecutionState.UNKNOWN
    else -> WorkflowExecutionState.FAILED
}

fun NodeResult.canonicalState(): NodeExecutionState = when {
    success -> NodeExecutionState.SUCCEEDED
    message.contains("unknown", ignoreCase = true) -> NodeExecutionState.UNKNOWN
    else -> NodeExecutionState.FAILED
}
