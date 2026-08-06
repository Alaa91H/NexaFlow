package com.nexaflow.core.execution.workflow

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Executes one action; abstracts the real registry so the interpreter stays pure. */
fun interface ActionExecutor {
    suspend fun execute(action: Action): SystemControlResult
}

/** Undoes the effect of an already-executed action node (revert-on-exit at workflow level). */
fun interface RollbackHandler {
    suspend fun rollback(node: WorkflowNode.ActionNode)
}

/** Outcome of one node execution, kept for the execution timeline. */
data class NodeResult(
    val nodeId: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long,
    /** ActionType.name for action nodes; null for structural nodes. */
    val actionType: String? = null
)

/** Outcome of running a whole workflow tree. */
data class WorkflowExecutionResult(
    val success: Boolean,
    val message: String,
    val nodeResults: List<NodeResult>,
    val durationMs: Long
)

/**
 * Executes a [WorkflowNode] tree through an [ActionExecutor], supporting
 * sequential, parallel, conditional and loop nodes, per-node timeouts,
 * cooperative cancellation and workflow-level rollback.
 *
 * The interpreter is independent of how actions are executed (the executor may
 * wrap `ActionRegistry`, a plugin provider, a test double, ...) — the core of
 * the Phase 3 workflow engine.
 */
class WorkflowInterpreter(
    private val executor: ActionExecutor,
    private val rollbackHandler: RollbackHandler? = null,
    private val epochMillis: EpochMillis = EpochMillis.System,
    private val defaultNodeTimeoutMs: Long? = null
) {

    suspend fun execute(root: WorkflowNode): WorkflowExecutionResult {
        val startedAt = epochMillis.now()
        val results = Collections.synchronizedList(mutableListOf<NodeResult>())
        // Cancellation must propagate (structured concurrency); only genuine
        // failures are converted into a failed result.
        val ok = try {
            runNode(root, results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            results += NodeResult("workflow", false, e.message ?: "Workflow failed", 0)
            false
        }
        return WorkflowExecutionResult(
            success = ok,
            message = if (ok) "Workflow completed" else "Workflow failed",
            nodeResults = results.toList(),
            durationMs = epochMillis.now() - startedAt
        )
    }

    private suspend fun runNode(node: WorkflowNode, results: MutableList<NodeResult>): Boolean {
        return when (node) {
            is WorkflowNode.ActionNode -> runAction(node, results)
            is WorkflowNode.SequenceNode -> runSequence(node, results)
            is WorkflowNode.ParallelNode -> runParallel(node, results)
            is WorkflowNode.BranchNode -> runBranch(node, results)
            is WorkflowNode.LoopNode -> runLoop(node, results)
        }
    }

    private suspend fun runAction(node: WorkflowNode.ActionNode, results: MutableList<NodeResult>): Boolean {
        val startedAt = epochMillis.now()
        val outcome = runCatching {
            if (defaultNodeTimeoutMs != null) {
                withTimeoutOrNull(defaultNodeTimeoutMs) { executor.execute(node.action) }
                    ?: SystemControlResult.fail("Action timed out after ${defaultNodeTimeoutMs}ms")
            } else {
                executor.execute(node.action)
            }
        }.getOrElse { SystemControlResult.fail(it.message ?: "Action failed") }
        val durationMs = epochMillis.now() - startedAt
        results += NodeResult(node.id, outcome.success, outcome.message, durationMs, node.action.type.name)
        return outcome.success
    }

    private suspend fun runSequence(node: WorkflowNode.SequenceNode, results: MutableList<NodeResult>): Boolean {
        val executedActions = mutableListOf<WorkflowNode.ActionNode>()
        for (child in node.children) {
            val ok = runNode(child, results)
            if (!ok) {
                if (node.rollbackOnFailure) {
                    executedActions.asReversed().forEach { actionNode ->
                        runCatching { rollbackHandler?.rollback(actionNode) }
                    }
                }
                return false
            }
            if (child is WorkflowNode.ActionNode) executedActions += child
        }
        return true
    }

    private suspend fun runParallel(node: WorkflowNode.ParallelNode, results: MutableList<NodeResult>): Boolean {
        val children = coroutineScope {
            node.children.map { child -> async { runNode(child, results) } }
        }
        return children.all { it.await() }
    }

    private suspend fun runBranch(node: WorkflowNode.BranchNode, results: MutableList<NodeResult>): Boolean {
        val condition = runCatching { node.condition.evaluate() }.getOrDefault(false)
        return if (condition) {
            runNode(node.whenTrue, results)
        } else {
            node.whenFalse?.let { runNode(it, results) } ?: true
        }
    }

    private suspend fun runLoop(node: WorkflowNode.LoopNode, results: MutableList<NodeResult>): Boolean {
        if (node.iterations < 0) return false
        repeat(node.iterations) {
            currentCoroutineContext().ensureActive()
            if (!runNode(node.body, results)) return false
        }
        return true
    }
}
