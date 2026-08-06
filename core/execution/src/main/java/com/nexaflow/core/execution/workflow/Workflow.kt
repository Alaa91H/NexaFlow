package com.nexaflow.core.execution.workflow

import com.nexaflow.domain.models.Action

/**
 * A node in a workflow tree. The interpreter walks this tree and delegates each
 * leaf to an [ActionExecutor]; the tree itself is pure data, so workflows can be
 * built, validated and unit-tested without Android.
 */
sealed interface WorkflowNode {
    val id: String

    /** Executes a single action via the registry. */
    data class ActionNode(
        override val id: String,
        val action: Action
    ) : WorkflowNode

    /**
     * Runs children in order and stops at the first failure. When
     * [rollbackOnFailure] is true and a child fails, already-executed action
     * siblings are rolled back (in reverse order) through the interpreter's
     * [RollbackHandler] — the workflow-level generalization of revert-on-exit.
     */
    data class SequenceNode(
        override val id: String,
        val children: List<WorkflowNode>,
        val rollbackOnFailure: Boolean = false
    ) : WorkflowNode

    /**
     * Runs children concurrently (fan-out, no early bail) and succeeds only
     * when every child succeeds. Unlike [SequenceNode], a failing child does not
     * cancel its siblings — they run to completion so partial work is visible.
     */
    data class ParallelNode(
        override val id: String,
        val children: List<WorkflowNode>
    ) : WorkflowNode

    /** Picks [whenTrue] or [whenFalse] based on [condition]. */
    data class BranchNode(
        override val id: String,
        val condition: WorkflowCondition,
        val whenTrue: WorkflowNode,
        val whenFalse: WorkflowNode? = null
    ) : WorkflowNode

    /** Repeats [body] [iterations] times; stops early on the first failure. */
    data class LoopNode(
        override val id: String,
        val iterations: Int,
        val body: WorkflowNode
    ) : WorkflowNode
}

/** A boolean gate evaluated before a branch. Pure and testable. */
fun interface WorkflowCondition {
    suspend fun evaluate(): Boolean
}

/** A named workflow: the root of a node tree. */
data class Workflow(
    val id: String,
    val name: String,
    val root: WorkflowNode
)
