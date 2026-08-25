package com.nexaflow.domain.diagnostics

import kotlinx.serialization.Serializable

/**
 * Execution Timeline — a per-run ordered journal of all execution events.
 *
 * Each run of a workflow produces a [ExecutionTimeline] containing
 * [ExecutionTimelineEntry] records that the Execution Inspector UI displays
 * to answer: "What exactly happened, in what order, and why?"
 *
 * The timeline is append-only during a run and becomes immutable on completion.
 */

@Serializable
enum class TimelineEventKind {
    TRIGGER_RECEIVED,
    POLICY_EVALUATED,
    CAPABILITY_RESOLVED,
    BACKEND_SELECTED,
    ACTION_STARTED,
    ACTION_COMPLETED,
    ACTION_FAILED,
    VERIFICATION_STARTED,
    VERIFICATION_COMPLETED,
    VERIFICATION_FAILED,
    RETRY_SCHEDULED,
    RETRY_STARTED,
    RECOVERY_STARTED,
    RECOVERY_COMPLETED,
    SUBWORKFLOW_STARTED,
    SUBWORKFLOW_COMPLETED,
    HUMAN_APPROVAL_REQUESTED,
    HUMAN_APPROVAL_RECEIVED,
    HUMAN_APPROVAL_TIMEOUT,
    PARALLEL_BRANCH_STARTED,
    PARALLEL_BRANCH_COMPLETED,
    CHECKPOINT_SAVED,
    CHECKPOINT_RESTORED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    WORKFLOW_CANCELLED
}

/**
 * One entry in the execution timeline.
 * All fields are serializable primitives — no Android objects or secrets allowed.
 */
@Serializable
data class ExecutionTimelineEntry(
    val timestampMs: Long,
    val kind: TimelineEventKind,
    val runId: String,
    val workflowId: String,
    val nodeId: String? = null,
    val actionId: String? = null,
    val actionType: String? = null,
    val backendId: String? = null,
    val durationMs: Long? = null,
    val retryCount: Int = 0,
    val success: Boolean? = null,
    val errorCode: String? = null,
    /** Human-readable summary for the Inspector UI — MUST NOT contain sensitive data. */
    val summary: String,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(runId.isNotBlank()) { "runId must not be blank" }
        require(workflowId.isNotBlank()) { "workflowId must not be blank" }
        require(summary.length <= 512) { "summary must not exceed 512 chars" }
    }
}

/**
 * Complete timeline for one workflow run.
 */
data class ExecutionTimeline(
    val runId: String,
    val workflowId: String,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val entries: List<ExecutionTimelineEntry>
) {
    val durationMs: Long?
        get() = completedAtMs?.minus(startedAtMs)

    val success: Boolean?
        get() = entries.lastOrNull()?.let { last ->
            last.kind == TimelineEventKind.WORKFLOW_COMPLETED
        }
}

/**
 * Mutable builder accumulating timeline entries during workflow execution.
 * Thread-safe: append() may be called from parallel branch coroutines.
 */
class ExecutionTimelineBuilder(
    val runId: String,
    val workflowId: String,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val entries = mutableListOf<ExecutionTimelineEntry>()
    private val startedAt = nowMs()

    fun append(
        kind: TimelineEventKind,
        summary: String,
        nodeId: String? = null,
        actionId: String? = null,
        actionType: String? = null,
        backendId: String? = null,
        durationMs: Long? = null,
        retryCount: Int = 0,
        success: Boolean? = null,
        errorCode: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val entry = ExecutionTimelineEntry(
            timestampMs = nowMs(),
            kind = kind,
            runId = runId,
            workflowId = workflowId,
            nodeId = nodeId,
            actionId = actionId,
            actionType = actionType,
            backendId = backendId,
            durationMs = durationMs,
            retryCount = retryCount,
            success = success,
            errorCode = errorCode,
            summary = summary,
            metadata = metadata
        )
        synchronized(entries) { entries.add(entry) }
    }

    fun build(completedAtMs: Long = nowMs()): ExecutionTimeline = ExecutionTimeline(
        runId = runId,
        workflowId = workflowId,
        startedAtMs = startedAt,
        completedAtMs = completedAtMs,
        entries = synchronized(entries) { entries.toList() }
    )
}

/**
 * Repository for persisting and querying execution timelines.
 * The in-memory implementation is used for testing and initial integration;
 * a Room-backed implementation should be wired in for production.
 */
interface ExecutionTimelineStore {
    suspend fun save(timeline: ExecutionTimeline)
    suspend fun findByRunId(runId: String): ExecutionTimeline?
    suspend fun recentTimelines(limit: Int = 50): List<ExecutionTimeline>
    suspend fun pruneOlderThan(beforeEpochMs: Long)
}

class InMemoryExecutionTimelineStore : ExecutionTimelineStore {
    private val timelines = mutableListOf<ExecutionTimeline>()
    private val lock = Any()

    override suspend fun save(timeline: ExecutionTimeline) {
        synchronized(lock) { timelines.removeAll { it.runId == timeline.runId }; timelines.add(timeline) }
    }

    override suspend fun findByRunId(runId: String): ExecutionTimeline? =
        synchronized(lock) { timelines.find { it.runId == runId } }

    override suspend fun recentTimelines(limit: Int): List<ExecutionTimeline> =
        synchronized(lock) { timelines.sortedByDescending { it.startedAtMs }.take(limit) }

    override suspend fun pruneOlderThan(beforeEpochMs: Long) {
        synchronized(lock) { timelines.removeAll { it.startedAtMs < beforeEpochMs } }
    }
}
