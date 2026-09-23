package com.nexaflow.core.logging

/** A single execution run on the timeline. */
data class ExecutionTimelineEntry(
    val id: String,
    val automationId: String,
    val automationName: String,
    val kind: String,          // RUN | EXIT | ERROR
    val success: Boolean,
    val message: String,
    val startedAt: Long,
    val durationMs: Long,
    /** Execution provider that ran the actions ("ROOT", "SHIZUKU", ...); null when not selected. */
    val channel: String? = null,
    /**
     * Correlation id shared by the durable history row's in-memory timeline
     * companion and its structured trace events. Null on legacy entries.
     */
    val runId: String? = null,
    /** Structured trace metadata; sequence/phase/reason stay null on ordinary rows. */
    val traceRunId: String? = null,
    val traceSequence: Int? = null,
    val tracePhase: TracePhase? = null,
    val traceReasonCode: String? = null,
    /** Already redacted before this value crosses the trace boundary. */
    val traceDetail: String? = null,
    val traceNodeId: String? = null,
)

/** A framework error (crash, provider failure, unsupported action). */
data class ErrorLogEntry(
    val id: String,
    val source: String,
    val message: String,
    val stackTrace: String?,
    val timestamp: Long
)

/** A performance measurement (execution time, queue wait, provider latency). */
data class PerformanceMetric(
    val name: String,
    val valueMs: Long,
    val timestamp: Long
)
