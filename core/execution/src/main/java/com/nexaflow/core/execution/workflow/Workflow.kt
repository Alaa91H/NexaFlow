package com.nexaflow.core.execution.workflow

import com.nexaflow.domain.models.Action
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * A node-visit budget shared by one workflow tree, including nested providers.
 *
 * Providers that execute a child workflow can pass this object to the child
 * interpreter. The atomic counter makes the limit meaningful when parallel
 * branches consume it concurrently.
 */
class WorkflowExecutionBudget private constructor(
    private val maxNodeVisits: Int,
    val maxExecutionTimeMs: Long
) {
    private val consumed = AtomicInteger(0)
    private val deadlineNanos = System.nanoTime() + (maxExecutionTimeMs * NANOS_PER_MILLISECOND)

    init {
        require(maxNodeVisits > 0) { "maxNodeVisits must be positive" }
        require(maxExecutionTimeMs > 0L) { "maxExecutionTimeMs must be positive" }
    }

    /** Atomically reserves one node visit, returning false when the budget is exhausted. */
    fun tryConsumeNodeVisit(): Boolean = consumed.incrementAndGet() <= maxNodeVisits

    /** Number of node visits reserved so far. */
    val consumedNodeVisits: Int
        get() = consumed.get()

    /** Number of node visits still available, never negative. */
    val remainingNodeVisits: Int
        get() = (maxNodeVisits - consumed.get()).coerceAtLeast(0)

    /** Whether the shared monotonic execution deadline has elapsed. */
    fun isExpired(): Boolean = System.nanoTime() >= deadlineNanos

    /** Remaining time for a child interpreter, rounded up to avoid a premature zero timeout. */
    fun remainingTimeMs(): Long {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return 0L
        return ((remainingNanos + NANOS_PER_MILLISECOND - 1L) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(1L)
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        internal fun fromPolicy(policy: WorkflowExecutionPolicy): WorkflowExecutionBudget =
            WorkflowExecutionBudget(policy.maxNodeVisits, policy.maxExecutionTimeMs)
    }
}

/**
 * Global safety limits applied to one interpreter run.
 *
 * Individual nodes already carry local bounds (loop iterations, delay,
 * retries, and polling time). These limits close the multiplication gap where
 * nested control-flow nodes could otherwise make the total work unbounded.
 * The time limit is cooperative: Android and plugin handlers must honor
 * coroutine cancellation for it to stop promptly.
 */
data class WorkflowExecutionPolicy(
    val maxExecutionTimeMs: Long = DEFAULT_MAX_EXECUTION_TIME_MS,
    val maxNodeVisits: Int = DEFAULT_MAX_NODE_VISITS
) {
    init {
        require(maxExecutionTimeMs in 1L..MAX_EXECUTION_TIME_MS) {
            "maxExecutionTimeMs must be in 1..$MAX_EXECUTION_TIME_MS"
        }
        require(maxNodeVisits in 1..MAX_NODE_VISITS) {
            "maxNodeVisits must be in 1..$MAX_NODE_VISITS"
        }
    }

    companion object {
        const val DEFAULT_MAX_EXECUTION_TIME_MS = 15 * 60 * 1_000L
        const val MAX_EXECUTION_TIME_MS = 24 * 60 * 60 * 1_000L
        const val DEFAULT_MAX_NODE_VISITS = 10_000
        const val MAX_NODE_VISITS = 1_000_000
    }
}
