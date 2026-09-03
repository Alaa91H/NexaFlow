package com.nexaflow.core.execution.workflow

/**
 * Extends [WorkflowInterpreter] to handle [WorkflowNodeExtensions] node types:
 * SubworkflowNode, HumanApprovalNode, SagaNode, and ForEachNode.
 *
 * This interceptor pattern allows the core interpreter to remain stable while
 * new control flow constructs are progressively integrated.
 */
interface SubworkflowProvider {
    /**
     * Loads a workflow by its persistent [workflowId] and executes it via
     * the interpreter. Returns a [WorkflowExecutionResult].
     *
     * This is the source-compatible legacy entry point. New implementations
     * should override the budget-aware overload below so nested node visits and
     * the parent deadline remain one shared safety boundary.
     */
    suspend fun executeSubworkflow(
        workflowId: String,
        inputParameters: Map<String, String>,
        depth: Int
    ): WorkflowExecutionResult = error(
        "SubworkflowProvider must implement a subworkflow execution method"
    )

    /**
     * Executes a child within the parent's shared budget. The default delegates
     * to the legacy method for compatibility; such an implementation can enforce
     * only the boundary checks it performs itself and must not claim full nested
     * budget accounting until it passes [executionBudget] to its child interpreter.
     */
    suspend fun executeSubworkflow(
        workflowId: String,
        inputParameters: Map<String, String>,
        depth: Int,
        executionBudget: WorkflowExecutionBudget
    ): WorkflowExecutionResult = executeSubworkflow(workflowId, inputParameters, depth)
}

/**
 * Approval gateway that the [WorkflowInterpreter] consults for [HumanApprovalNode].
 * Implementations can use a notification, UI dialog, or remote webhook.
 */
interface ApprovalGateway {
    /**
     * Suspends until an approval decision is made or [timeoutMs] elapses.
     * Returns true if approved, false if rejected or timed out.
     */
    suspend fun requestApproval(
        nodeId: String,
        prompt: String,
        timeoutMs: Long,
        onTimeout: HumanApprovalNode.TimeoutOutcome
    ): Boolean
}

/**
 * Execution journal entry for durable replay and debugging.
 */
data class ExecutionJournalEntry(
    val runId: String,
    val workflowId: String,
    val nodeId: String,
    val actionType: String?,
    val backend: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val retryCount: Int = 0,
    val errorCode: String? = null,
    val message: String
)

/**
 * Durable journal that persists execution entries for checkpoint recovery,
 * replay debugging, and idempotency enforcement.
 */
interface ExecutionJournal {
    /** Records a completed node execution. */
    suspend fun record(entry: ExecutionJournalEntry)

    /** Returns true if this nodeId was already successfully executed in [runId]. */
    suspend fun isCompleted(runId: String, nodeId: String): Boolean

    /** Returns all entries for a given [runId] for replay/debugging. */
    suspend fun entriesForRun(runId: String): List<ExecutionJournalEntry>

    /** Clears all entries older than [beforeEpochMs]. */
    suspend fun pruneOlderThan(beforeEpochMs: Long)
}

/**
 * In-memory implementation of [ExecutionJournal] for testing and initial integration.
 * A Room-backed implementation should be added in the data module for production use.
 */
class InMemoryExecutionJournal : ExecutionJournal {
    private val entries = mutableListOf<ExecutionJournalEntry>()

    override suspend fun record(entry: ExecutionJournalEntry) {
        synchronized(entries) { entries.add(entry) }
    }

    override suspend fun isCompleted(runId: String, nodeId: String): Boolean =
        synchronized(entries) {
            entries.any { it.runId == runId && it.nodeId == nodeId && it.success }
        }

    override suspend fun entriesForRun(runId: String): List<ExecutionJournalEntry> =
        synchronized(entries) { entries.filter { it.runId == runId }.toList() }

    override suspend fun pruneOlderThan(beforeEpochMs: Long) {
        synchronized(entries) { entries.removeAll { it.startedAt < beforeEpochMs } }
    }
}
