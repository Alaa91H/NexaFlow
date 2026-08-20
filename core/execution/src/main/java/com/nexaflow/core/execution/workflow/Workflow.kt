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
    ) : WorkflowNode {
        init {
            require(iterations in 0..MAX_ITERATIONS) {
                "Workflow loop iterations must be in 0..$MAX_ITERATIONS"
            }
        }

        companion object {
            /** Execution guard against malformed imports and accidental runaway loops. */
            const val MAX_ITERATIONS = 1_000
        }
    }

    /** Cooperative delay bounded to five minutes; cancellation remains prompt. */
    data class DelayNode(override val id: String, val delayMs: Long) : WorkflowNode {
        init { require(delayMs in 0L..MAX_DELAY_MS) { "delayMs must be in 0..$MAX_DELAY_MS" } }
    }

    /** Retries [body] with a bounded, deterministic linear backoff. */
    data class RetryNode(
        override val id: String,
        val body: WorkflowNode,
        val maxAttempts: Int,
        val backoffMs: Long = 0L
    ) : WorkflowNode {
        init {
            require(maxAttempts in 1..MAX_RETRY_ATTEMPTS) { "maxAttempts must be in 1..$MAX_RETRY_ATTEMPTS" }
            require(backoffMs in 0L..MAX_DELAY_MS) { "backoffMs must be in 0..$MAX_DELAY_MS" }
        }
    }

    /** Fails safely if [body] has not completed by its bounded timeout. */
    data class TimeoutNode(
        override val id: String,
        val body: WorkflowNode,
        val timeoutMs: Long
    ) : WorkflowNode {
        init { require(timeoutMs in 1L..MAX_DELAY_MS) { "timeoutMs must be in 1..$MAX_DELAY_MS" } }
    }

    /** Runs branches concurrently and completes with the first completed branch. */
    data class RaceNode(override val id: String, val children: List<WorkflowNode>) : WorkflowNode {
        init { require(children.isNotEmpty()) { "Race requires at least one child" } }
    }

    /** Re-evaluates a pure condition before every iteration with a strict guard. */
    data class WhileNode(
        override val id: String,
        val condition: WorkflowCondition,
        val body: WorkflowNode,
        val maxIterations: Int = LoopNode.MAX_ITERATIONS
    ) : WorkflowNode {
        init { require(maxIterations in 0..LoopNode.MAX_ITERATIONS) { "maxIterations is out of range" } }
    }

    /** Handles a failed body and always runs [finallyNode] when one is supplied. */
    data class TryNode(
        override val id: String,
        val body: WorkflowNode,
        val catchNode: WorkflowNode? = null,
        val finallyNode: WorkflowNode? = null
    ) : WorkflowNode

    /** Waits for a pure condition under bounded polling and workflow cancellation. */
    data class WaitUntilNode(
        override val id: String,
        val condition: WorkflowCondition,
        val timeoutMs: Long,
        val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ) : WorkflowNode {
        init {
            require(timeoutMs in 1L..MAX_DELAY_MS) { "timeoutMs must be in 1..$MAX_DELAY_MS" }
            require(pollIntervalMs in 10L..timeoutMs) { "pollIntervalMs must be in 10..timeoutMs" }
        }
    }

    companion object {
        const val MAX_DELAY_MS = 300_000L
        const val MAX_RETRY_ATTEMPTS = 100
        const val DEFAULT_POLL_INTERVAL_MS = 100L
    }
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
