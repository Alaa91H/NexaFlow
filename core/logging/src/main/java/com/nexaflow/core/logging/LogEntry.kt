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
    val channel: String? = null
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
