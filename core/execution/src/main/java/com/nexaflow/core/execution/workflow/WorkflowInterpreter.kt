package com.nexaflow.core.execution.workflow

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.variables.VariableResolver
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
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
    val durationMs: Long,
    /** Outputs explicitly returned by a subworkflow provider. */
    val outputs: Map<String, String> = emptyMap()
)

/**
 * Executes a [WorkflowNode] tree through an [ActionExecutor], supporting
 * sequential, parallel, conditional and loop nodes, per-node timeouts,
 * cooperative cancellation, workflow-level rollback, bounded subworkflows,
 * human approval gates, saga compensation, and scoped ForEach variables.
 *
 * The interpreter is independent of how actions are executed (the executor may
 * wrap `ActionRegistry`, a plugin provider, a test double, ...) — the core of
 * the workflow engine remains pure and testable. Optional collaborators are
 * explicit: if a workflow asks for a subworkflow or approval but no matching
 * provider is installed, execution fails closed with a diagnostic result.
 */
class WorkflowInterpreter(
    private val executor: ActionExecutor,
    private val rollbackHandler: RollbackHandler? = null,
    private val epochMillis: EpochMillis = EpochMillis.System,
    private val defaultNodeTimeoutMs: Long? = null,
    private val subworkflowProvider: SubworkflowProvider? = null,
    private val approvalGateway: ApprovalGateway? = null,
    private val maxSubworkflowDepth: Int = SubworkflowNode.MAX_RECURSION_DEPTH,
    private val executionPolicy: WorkflowExecutionPolicy = WorkflowExecutionPolicy(),
    /** Optional node-level audit sink; journal failures never fail the workflow. */
    private val executionJournal: ExecutionJournal? = null
) {

    init {
        require(maxSubworkflowDepth in 1..SubworkflowNode.MAX_RECURSION_DEPTH) {
            "maxSubworkflowDepth must be in 1..${SubworkflowNode.MAX_RECURSION_DEPTH}"
        }
    }

    private data class CompensationOutcome(
        val success: Boolean,
        val message: String
    )

    private data class CompensationEntry(
        val nodeId: String,
        val actionType: String?,
        val execute: suspend () -> CompensationOutcome
    )

    private class ExecutionState(
        val runId: String,
        val workflowId: String,
        val results: MutableList<NodeResult>,
        val variables: MutableMap<String, String>,
        val compensations: MutableList<CompensationEntry>,
        val budget: WorkflowExecutionBudget
    )

    suspend fun execute(
        root: WorkflowNode,
        /**
         * Depth supplied by a parent [SubworkflowProvider]. The default keeps
         * the legacy entry point unchanged; providers must pass the received
         * depth when they invoke another interpreter instance.
         */
        initialSubworkflowDepth: Int = 0,
        /**
         * Shared budget supplied by a parent provider. Passing it is required
         * for nested execution to remain inside the parent run's safety limits.
         */
        executionBudget: WorkflowExecutionBudget? = null
    ): WorkflowExecutionResult {
        require(initialSubworkflowDepth >= 0) { "initialSubworkflowDepth must not be negative" }
        val startedAt = epochMillis.now()
        val budget = executionBudget ?: WorkflowExecutionBudget.fromPolicy(executionPolicy)
        val state = ExecutionState(
            runId = UUID.randomUUID().toString(),
            workflowId = root.id,
            results = Collections.synchronizedList(mutableListOf()),
            variables = LinkedHashMap(),
            compensations = mutableListOf(),
            budget = budget
        )
        // Cancellation must propagate (structured concurrency); only genuine
        // failures are converted into a failed result. A policy timeout is a
        // classified workflow failure, not an unstructured cancellation.
        val allowedTimeMs = minOf(executionPolicy.maxExecutionTimeMs, budget.remainingTimeMs())
        val ok = try {
            if (allowedTimeMs <= 0L) {
                recordTimeBudgetFailure(state, allowedTimeMs)
                false
            } else {
                withTimeoutOrNull(allowedTimeMs) {
                    runNode(root, state, depth = initialSubworkflowDepth)
                } ?: run {
                    recordTimeBudgetFailure(state, allowedTimeMs)
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            addResult(state, NodeResult("workflow", false, e.message ?: "Workflow failed", 0))
            false
        }
        return WorkflowExecutionResult(
            success = ok,
            message = if (ok) "Workflow completed" else "Workflow failed",
            nodeResults = state.results.toList(),
            durationMs = epochMillis.now() - startedAt,
            outputs = state.variables.toMap()
        )
    }

    private suspend fun runNode(
        node: WorkflowNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val startedAt = epochMillis.now()
        if (state.budget.isExpired()) {
            val message = "Workflow execution time budget of ${state.budget.maxExecutionTimeMs}ms exceeded"
            addResult(state, NodeResult(node.id, false, message, 0))
            recordJournal(state, node, false, startedAt, "EXECUTION_TIME_LIMIT")
            return false
        }
        if (!state.budget.tryConsumeNodeVisit()) {
            val message = "Workflow node-visit limit exceeded"
            addResult(state, NodeResult(node.id, false, message, 0))
            recordJournal(state, node, false, startedAt, "NODE_VISIT_LIMIT")
            return false
        }
        val success = try {
            when (node) {
                is WorkflowNode.ActionNode -> runAction(node, state)
                is WorkflowNode.SequenceNode -> runSequence(node, state, depth)
                is WorkflowNode.ParallelNode -> runParallel(node, state, depth)
                is WorkflowNode.BranchNode -> runBranch(node, state, depth)
                is WorkflowNode.LoopNode -> runLoop(node, state, depth)
                is WorkflowNode.DelayNode -> runDelay(node, state)
                is WorkflowNode.RetryNode -> runRetry(node, state, depth)
                is WorkflowNode.TimeoutNode -> runTimeout(node, state, depth)
                is WorkflowNode.RaceNode -> runRace(node, state, depth)
                is WorkflowNode.WhileNode -> runWhile(node, state, depth)
                is WorkflowNode.TryNode -> runTry(node, state, depth)
                is WorkflowNode.WaitUntilNode -> runWaitUntil(node, state)
                is SubworkflowNode -> runSubworkflow(node, state, depth)
                is HumanApprovalNode -> runHumanApproval(node, state)
                is SagaNode -> runSaga(node, state, depth)
                is ForEachNode -> runForEach(node, state, depth)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            addResult(
                state,
                NodeResult(node.id, false, failure.message ?: "Workflow node failed", epochMillis.now() - startedAt)
            )
            false
        }
        recordJournal(state, node, success, startedAt)
        return success
    }

    private fun addResult(state: ExecutionState, result: NodeResult) {
        state.results += result
    }

    /**
     * Journaling is deliberately best effort: observability must never change
     * device state or turn a successful action into a failed workflow. The
     * caller may provide a durable Room-backed implementation; tests use the
     * in-memory implementation from [ExecutionJournal.kt].
     */
    private suspend fun recordJournal(
        state: ExecutionState,
        node: WorkflowNode,
        success: Boolean,
        startedAt: Long,
        errorCode: String? = null
    ) {
        val journal = executionJournal ?: return
        try {
            journal.record(
                ExecutionJournalEntry(
                    runId = state.runId,
                    workflowId = state.workflowId,
                    nodeId = node.id,
                    actionType = (node as? WorkflowNode.ActionNode)?.action?.type?.name,
                    backend = null,
                    startedAt = startedAt,
                    finishedAt = epochMillis.now(),
                    success = success,
                    errorCode = errorCode,
                    message = if (success) "Node completed" else "Node failed"
                )
            )
        } catch (cancellation: CancellationException) {
            // Observability must not break structured cancellation.
            throw cancellation
        } catch (_: Throwable) {
            // Journal storage is diagnostic; execution remains authoritative.
        }
    }

    private suspend fun runSubworkflow(
        node: SubworkflowNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val startedAt = epochMillis.now()
        val provider = subworkflowProvider
        if (provider == null) {
            addResult(
                state,
                NodeResult(
                    node.id,
                    false,
                    "Subworkflow execution is unavailable: no provider is configured",
                    0
                )
            )
            return false
        }
        if (depth >= maxSubworkflowDepth) {
            addResult(
                state,
                NodeResult(
                    node.id,
                    false,
                    "Subworkflow recursion depth exceeded the limit of $maxSubworkflowDepth",
                    0
                )
            )
            return false
        }

        val explicitInputs = node.inputParameters.mapValues { (_, value) ->
            resolveLocalVariables(value, state.variables)
        }
        val inputs = if (node.isolatedContext) {
            explicitInputs
        } else {
            LinkedHashMap(state.variables).apply { putAll(explicitInputs) }
        }
        val outcome = try {
            provider.executeSubworkflow(
                workflowId = node.workflowId,
                inputParameters = inputs,
                depth = depth + 1,
                executionBudget = state.budget
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            WorkflowExecutionResult(
                success = false,
                message = failure.message ?: "Subworkflow provider failed",
                nodeResults = emptyList(),
                durationMs = 0
            )
        }
        if (outcome.success) {
            node.outputParameters.forEach { name ->
                outcome.outputs[name]?.let { value -> state.variables[name] = value }
            }
        }
        addResult(
            state,
            NodeResult(
                node.id,
                outcome.success,
                "Subworkflow ${node.workflowId}: ${outcome.message}",
                epochMillis.now() - startedAt
            )
        )
        return outcome.success
    }

    private suspend fun runHumanApproval(
        node: HumanApprovalNode,
        state: ExecutionState
    ): Boolean {
        val startedAt = epochMillis.now()
        val gateway = approvalGateway
        if (gateway == null) {
            addResult(
                state,
                NodeResult(
                    node.id,
                    false,
                    "Human approval is unavailable: no approval gateway is configured",
                    0
                )
            )
            return false
        }

        val decision = try {
            withTimeoutOrNull(node.timeoutMs) {
                gateway.requestApproval(node.id, node.prompt, node.timeoutMs, node.onTimeout)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            addResult(
                state,
                NodeResult(
                    node.id,
                    false,
                    "Human approval failed: ${failure.message ?: "gateway error"}",
                    epochMillis.now() - startedAt
                )
            )
            return false
        }
        val timedOut = decision == null
        val approved = decision ?: (node.onTimeout == HumanApprovalNode.TimeoutOutcome.APPROVE)
        val message = when {
            timedOut && approved -> "Human approval timed out; policy approved the continuation"
            timedOut -> "Human approval timed out; policy rejected the continuation"
            approved -> "Human approval granted"
            else -> "Human approval rejected"
        }
        addResult(
            state,
            NodeResult(node.id, approved, message, epochMillis.now() - startedAt)
        )
        return approved
    }

    private suspend fun runSaga(
        node: SagaNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        // Isolate body compensation entries. A Saga owns one explicit
        // compensation; leaking every internal body action into the parent
        // rollback stack would double-undo the same side effect.
        val bodyState = ExecutionState(
            runId = state.runId,
            workflowId = state.workflowId,
            results = state.results,
            variables = LinkedHashMap(state.variables),
            compensations = mutableListOf(),
            budget = state.budget
        )
        val bodySucceeded = runNode(node.body, bodyState, depth)
        if (!bodySucceeded) return false
        state.variables.putAll(bodyState.variables)

        val compensation = node.compensation ?: return true
        state.compensations += CompensationEntry(node.id, null) {
            val compensationState = ExecutionState(
                runId = state.runId,
                workflowId = state.workflowId,
                results = state.results,
                variables = LinkedHashMap(state.variables),
                compensations = mutableListOf(),
                budget = state.budget
            )
            val succeeded = runNode(compensation, compensationState, depth)
            CompensationOutcome(
                succeeded,
                if (succeeded) "Saga compensation completed" else "Saga compensation failed"
            )
        }
        return true
    }

    private suspend fun runForEach(
        node: ForEachNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        var allSucceeded = true
        for ((index, item) in node.items.withIndex()) {
            val hadPrevious = state.variables.containsKey(node.itemVariable)
            val previous = state.variables[node.itemVariable]
            state.variables[node.itemVariable] = item
            // Expose a deterministic zero-based index without requiring another
            // user-configured variable. It is scoped to this iteration too.
            val indexName = "${node.itemVariable}_index"
            val hadPreviousIndex = state.variables.containsKey(indexName)
            val previousIndex = state.variables[indexName]
            state.variables[indexName] = index.toString()
            try {
                if (!runNode(node.body, state, depth)) {
                    allSucceeded = false
                    if (!node.continueOnFailure) return false
                }
            } finally {
                if (hadPrevious) state.variables[node.itemVariable] = previous.orEmpty()
                else state.variables.remove(node.itemVariable)
                if (hadPreviousIndex) state.variables[indexName] = previousIndex.orEmpty()
                else state.variables.remove(indexName)
            }
        }
        return allSucceeded
    }

    private suspend fun runAction(
        node: WorkflowNode.ActionNode,
        state: ExecutionState
    ): Boolean {
        val startedAt = epochMillis.now()
        val action = resolveActionVariables(node.action, state.variables)
        val outcome = try {
            if (defaultNodeTimeoutMs != null) {
                withTimeoutOrNull(defaultNodeTimeoutMs) { executor.execute(action) }
                    ?: SystemControlResult.fail("Action timed out after ${defaultNodeTimeoutMs}ms")
            } else {
                executor.execute(action)
            }
        } catch (cancellation: CancellationException) {
            // Cancellation is structured control flow, not an action failure.
            throw cancellation
        } catch (failure: Throwable) {
            SystemControlResult.fail(failure.message ?: "Action failed")
        }
        val durationMs = epochMillis.now() - startedAt
        addResult(
            state,
            NodeResult(node.id, outcome.success, outcome.message, durationMs, node.action.type.name)
        )
        return outcome.success
    }

    private suspend fun runSequence(
        node: WorkflowNode.SequenceNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val compensationStart = state.compensations.size
        for (child in node.children) {
            val ok = runNode(child, state, depth)
            if (!ok) {
                if (node.rollbackOnFailure) {
                    rollback(state, compensationStart)
                }
                return false
            }
            // Preserve the historical rollback contract for direct action
            // siblings while also allowing SagaNode to register explicit
            // compensation entries of its own.
            if (child is WorkflowNode.ActionNode && rollbackHandler != null) {
                state.compensations += CompensationEntry(
                    nodeId = child.id,
                    actionType = child.action.type.name
                ) {
                    try {
                        rollbackHandler.rollback(child)
                        CompensationOutcome(true, "Rollback completed")
                    } catch (failure: Throwable) {
                        CompensationOutcome(
                            false,
                            failure.message ?: "Rollback failed"
                        )
                    }
                }
            }
        }
        return true
    }

    private suspend fun rollback(state: ExecutionState, startIndex: Int) {
        val entries = state.compensations.subList(startIndex, state.compensations.size)
            .toList()
            .asReversed()
        state.compensations.subList(startIndex, state.compensations.size).clear()
        entries.forEach { entry ->
            val outcome = try {
                entry.execute()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                CompensationOutcome(false, failure.message ?: "Compensation failed")
            }
            if (!outcome.success) {
                addResult(
                    state,
                    NodeResult(
                        nodeId = "${entry.nodeId}:compensation",
                        success = false,
                        message = outcome.message,
                        durationMs = 0,
                        actionType = entry.actionType
                    )
                )
            }
        }
    }

    private suspend fun runParallel(
        node: WorkflowNode.ParallelNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val outcomes = coroutineScope {
            node.children.map { child ->
                async {
                    // Parallel branches receive isolated variable maps and
                    // compensation stacks. Results remain shared and ordered by
                    // child declaration when their jobs are joined below.
                    val childState = ExecutionState(
                        runId = state.runId,
                        workflowId = state.workflowId,
                        results = state.results,
                        variables = LinkedHashMap(state.variables),
                        compensations = mutableListOf(),
                        budget = state.budget
                    )
                    val success = runNode(child, childState, depth)
                    success to childState.compensations.toList()
                }
            }.map { it.await() }
        }
        outcomes.forEach { (_, compensations) -> state.compensations += compensations }
        return outcomes.all { it.first }
    }

    private suspend fun runBranch(
        node: WorkflowNode.BranchNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        // A failed condition is not a false condition. Treating it as false used
        // to execute whenFalse while the engine reported a successful workflow,
        // which is unsafe for automation branches that change device state.
        val condition = try {
            node.condition.evaluate()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            addResult(state, NodeResult(node.id, false, failure.message ?: "Branch condition failed", 0))
            return false
        }
        return if (condition) {
            runNode(node.whenTrue, state, depth)
        } else {
            node.whenFalse?.let { runNode(it, state, depth) } ?: true
        }
    }

    private suspend fun runLoop(
        node: WorkflowNode.LoopNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        repeat(node.iterations) {
            currentCoroutineContext().ensureActive()
            if (!runNode(node.body, state, depth)) return false
        }
        return true
    }

    private suspend fun runDelay(
        node: WorkflowNode.DelayNode,
        state: ExecutionState
    ): Boolean {
        val startedAt = epochMillis.now()
        delay(node.delayMs)
        addResult(
            state,
            NodeResult(node.id, true, "Delayed for ${node.delayMs}ms", epochMillis.now() - startedAt)
        )
        return true
    }

    private suspend fun runRetry(
        node: WorkflowNode.RetryNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val startedAt = epochMillis.now()
        repeat(node.maxAttempts) { attempt ->
            currentCoroutineContext().ensureActive()
            val attemptCompensationStart = state.compensations.size
            if (runNode(node.body, state, depth)) {
                addResult(
                    state,
                    NodeResult(
                        node.id,
                        true,
                        "Completed on attempt ${attempt + 1}",
                        epochMillis.now() - startedAt
                    )
                )
                return true
            }
            // A failed attempt may have produced a partial side effect (for
            // example, a successful Saga followed by a failed node). Reconcile
            // its declared compensations before retrying, rather than carrying
            // stale entries into a later successful attempt.
            rollback(state, attemptCompensationStart)
            if (attempt + 1 < node.maxAttempts && node.backoffMs > 0L) {
                delay(node.backoffMs * (attempt + 1))
            }
        }
        addResult(
            state,
            NodeResult(
                node.id,
                false,
                "Failed after ${node.maxAttempts} attempts",
                epochMillis.now() - startedAt
            )
        )
        return false
    }

    private suspend fun runTimeout(
        node: WorkflowNode.TimeoutNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val startedAt = epochMillis.now()
        val completed = withTimeoutOrNull(node.timeoutMs) { runNode(node.body, state, depth) }
        val ok = completed ?: false
        addResult(
            state,
            NodeResult(
                node.id,
                ok,
                if (completed == null) "Timed out after ${node.timeoutMs}ms" else "Completed within timeout",
                epochMillis.now() - startedAt
            )
        )
        return ok
    }

    private suspend fun runRace(
        node: WorkflowNode.RaceNode,
        state: ExecutionState,
        depth: Int
    ): Boolean = coroutineScope {
        val branches = node.children.map { child ->
            async {
                // A race may cancel a branch after an external side effect has
                // started. Keep branch compensation isolated and do not claim an
                // automatic rollback without a verified winner/loser contract.
                val branchState = ExecutionState(
                    runId = state.runId,
                    workflowId = state.workflowId,
                    results = state.results,
                    variables = LinkedHashMap(state.variables),
                    compensations = mutableListOf(),
                    budget = state.budget
                )
                runNode(child, branchState, depth)
            }
        }
        try {
            select<Boolean> {
                branches.forEach { branch -> branch.onAwait { it } }
            }
        } finally {
            branches.forEach { branch -> if (branch.isActive) branch.cancel() }
        }
    }

    private suspend fun runWhile(
        node: WorkflowNode.WhileNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        var iterations = 0
        while (iterations < node.maxIterations) {
            currentCoroutineContext().ensureActive()
            val shouldContinue = try {
                node.condition.evaluate()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                addResult(
                    state,
                    NodeResult(node.id, false, failure.message ?: "While condition failed", 0)
                )
                return false
            }
            if (!shouldContinue) return true
            if (!runNode(node.body, state, depth)) return false
            iterations += 1
        }
        val stillTrue = try {
            node.condition.evaluate()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            addResult(
                state,
                NodeResult(node.id, false, failure.message ?: "While condition failed", 0)
            )
            return false
        }
        if (stillTrue) {
            addResult(state, NodeResult(node.id, false, "While iteration limit reached", 0))
        }
        return !stillTrue
    }

    private suspend fun runTry(
        node: WorkflowNode.TryNode,
        state: ExecutionState,
        depth: Int
    ): Boolean {
        val bodyOk = runNode(node.body, state, depth)
        val handled = if (bodyOk) true else node.catchNode?.let { runNode(it, state, depth) } ?: false
        val finalized = node.finallyNode?.let { runNode(it, state, depth) } ?: true
        return handled && finalized
    }

    private suspend fun runWaitUntil(
        node: WorkflowNode.WaitUntilNode,
        state: ExecutionState
    ): Boolean {
        val startedAt = epochMillis.now()
        var lastConditionFailure: Throwable? = null
        while (epochMillis.now() - startedAt < node.timeoutMs) {
            currentCoroutineContext().ensureActive()
            val matched = try {
                node.condition.evaluate()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                lastConditionFailure = failure
                false
            }
            if (matched) {
                addResult(
                    state,
                    NodeResult(node.id, true, "Wait condition satisfied", epochMillis.now() - startedAt)
                )
                return true
            }
            delay(node.pollIntervalMs)
        }
        val failureDetail = lastConditionFailure?.message?.let { "; last condition error: $it" }.orEmpty()
        addResult(
            state,
            NodeResult(
                node.id,
                false,
                "Wait condition timed out after ${node.timeoutMs}ms$failureDetail",
                epochMillis.now() - startedAt
            )
        )
        return false
    }

    private suspend fun recordTimeBudgetFailure(
        state: ExecutionState,
        allowedTimeMs: Long
    ) {
        val message = if (state.budget.isExpired()) {
            "Workflow execution exceeded the shared ${state.budget.maxExecutionTimeMs}ms time budget; side effects may require verification"
        } else {
            "Workflow execution exceeded its ${allowedTimeMs}ms time budget; side effects may require verification"
        }
        addResult(state, NodeResult("workflow", false, message, 0))
        val journal = executionJournal ?: return
        try {
            journal.record(
                ExecutionJournalEntry(
                    runId = state.runId,
                    workflowId = state.workflowId,
                    nodeId = "workflow",
                    actionType = null,
                    backend = null,
                    startedAt = epochMillis.now(),
                    finishedAt = epochMillis.now(),
                    success = false,
                    errorCode = "EXECUTION_TIME_LIMIT",
                    message = message
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Journal storage is diagnostic; execution remains authoritative.
        }
    }

    private fun resolveActionVariables(
        action: Action,
        variables: Map<String, String>
    ): Action {
        if (variables.isEmpty()) return action
        return action.copy(
            config = action.config.mapValues { (key, value) ->
                if (key == "bundleJson" || key == "action_buttons") value
                else resolveLocalVariables(value, variables)
            }
        )
    }

    private fun resolveLocalVariables(value: String, variables: Map<String, String>): String =
        VariableResolver.resolve(value, variables)
}
