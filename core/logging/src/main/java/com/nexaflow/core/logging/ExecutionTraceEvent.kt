package com.nexaflow.core.logging

/**
 * Typed, machine-readable execution trace events (roadmap P0.4, NF-P0-040).
 * Every final outcome — run, skip, failure, uncertain — carries a stable
 * [reasonCode], so the "Why didn't this run?" surface renders explanations
 * from data instead of parsing free-form messages.
 *
 * Redaction contract: any [detail] value that could carry user content goes
 * through [SecretRedactor] at the record boundary ([TraceRecorder.record]),
 * not only in the UI. Secrets never enter the trace model.
 */
data class ExecutionTraceEvent(
    /** Unique event id (UUID). */
    val id: String,
    /** Owning run — one admission of one automation. */
    val runId: String,
    val automationId: String,
    /** Monotonic sequence within the run for stable ordering. */
    val sequence: Int,
    val phase: TracePhase,
    /** Stable machine-readable reason, e.g. TRIGGER_ALL_GATE_BLOCKED. */
    val reasonCode: String,
    /** Optional redacted human explanation detail. */
    val detail: String? = null,
    /** Backend/strategy that executed, when the event reaches execution. */
    val backend: String? = null,
    /** Node identity inside the workflow graph, when applicable. */
    val nodeId: String? = null,
    val atEpochMs: Long,
    val durationMs: Long = 0L,
)

/** Trace lifecycle phases, ordered as they occur within a run. */
enum class TracePhase {
    /** Event admission (trigger fired, run started). */
    ADMISSION,

    /** A gate blocked the run: constraints, trigger-match ALL, admission, maintenance. */
    GATE_BLOCKED,

    /** A node attempt inside the workflow (action, branch, retry…). */
    NODE_ATTEMPT,

    /** Post-condition verification of a side effect. */
    VERIFICATION,

    /** Exit/rollback/compensation path. */
    EXIT,

    /** Terminal outcome of the run. */
    OUTCOME,
}

/**
 * Canonical reason codes. UI maps each code to a localized explanation;
 * unknown codes still render (with the raw detail) so the trace can never
 * become un-explainable as the engine evolves.
 */
object TraceReasons {
    const val RUN_STARTED = "RUN_STARTED"
    const val CONSTRAINT_BLOCKED = "CONSTRAINT_BLOCKED"
    const val TRIGGER_ALL_GATE_BLOCKED = "TRIGGER_ALL_GATE_BLOCKED"
    const val TRIGGER_AND_UNSATISFIED = "TRIGGER_AND_UNSATISFIED"
    const val TRIGGER_STATE_UNKNOWN = "TRIGGER_STATE_UNKNOWN"
    const val TRIGGER_STATE_UNAVAILABLE = "TRIGGER_STATE_UNAVAILABLE"
    const val TRIGGER_STATE_ERROR = "TRIGGER_STATE_ERROR"
    const val MAINTENANCE_WAITING = "MAINTENANCE_WAITING"
    const val MAINTENANCE_DUPLICATE = "MAINTENANCE_DUPLICATE"
    const val ADMISSION_REJECTED = "ADMISSION_REJECTED"
    const val CAPABILITY_BLOCKED = "CAPABILITY_BLOCKED"
    const val CONFIGURATION_BLOCKED = "CONFIGURATION_BLOCKED"
    const val ACTION_FAILED = "ACTION_FAILED"
    const val VERIFICATION_FAILED = "VERIFICATION_FAILED"
    const val OUTCOME_UNCERTAIN = "OUTCOME_UNCERTAIN"
    const val RUN_COMPLETED = "RUN_COMPLETED"
    const val RUN_FAILED = "RUN_FAILED"
    const val EXIT_COMPLETED = "EXIT_COMPLETED"
}

/**
 * Bounded sink for typed trace events. Sits on the existing [LogStore]
 * timeline (no parallel logging system): events are recorded as
 * [ExecutionTimelineEntry] rows with `kind = "TRACE:<phase>"` and the reason
 * code embedded, so today's history UI keeps working while the debugger gains
 * structured data. Retention is delegated to the LogStore's existing bounds.
 */
class TraceRecorder(private val logStore: LogStore) {

    private val counters = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Records one event. Never throws: tracing must not break execution.
     * [ExecutionTraceEvent.detail] is redacted at this boundary.
     */
    suspend fun record(event: ExecutionTraceEvent) {
        try {
            val seq = counters.compute(event.runId) { _, current ->
                if (event.sequence > 0) {
                    maxOf(current ?: 0, event.sequence)
                } else {
                    (current ?: 0) + 1
                }
            } ?: 1
            val stamped = if (event.sequence == 0) event.copy(sequence = seq) else event
            // Redact here even when the supplied LogStore is not wrapped in
            // RedactingLogStore. TraceRecorder's public contract is that raw
            // user content never crosses this boundary.
            val redactedDetail = SecretRedactor.redact(stamped.detail)
            logStore.recordExecution(
                ExecutionTimelineEntry(
                    id = stamped.id,
                    automationId = stamped.automationId,
                    automationName = "", // joined by the history layer when rendering
                    kind = "TRACE:${stamped.phase.name}",
                    success = stamped.isSuccessfulTimelineEvent(),
                    message = "${stamped.reasonCode}${redactedDetail?.let { "|$it" }.orEmpty()}",
                    startedAt = stamped.atEpochMs,
                    durationMs = stamped.durationMs,
                    channel = stamped.backend,
                    runId = stamped.runId,
                    traceRunId = stamped.runId,
                    traceSequence = stamped.sequence,
                    tracePhase = stamped.phase,
                    traceReasonCode = stamped.reasonCode,
                    traceDetail = redactedDetail,
                    traceNodeId = stamped.nodeId,
                )
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Cancellation is structured-concurrency control flow, not a
            // logging failure. Never turn cancellation into a successful run.
            throw cancellation
        } catch (_: Throwable) {
            // Ordinary tracing failures must never break execution.
        } finally {
            // OUTCOME is terminal. Releasing its sequence state here prevents
            // a unique runId from remaining in memory forever.
            if (event.phase == TracePhase.OUTCOME) forgetRun(event.runId)
        }
    }

    /** Convenience builder for gate-blocked runs (the "Why didn't this run?" data). */
    suspend fun recordGateBlocked(
        runId: String,
        automationId: String,
        reasonCode: String,
        detail: String,
        atEpochMs: Long,
    ) {
        try {
            record(
                ExecutionTraceEvent(
                    id = java.util.UUID.randomUUID().toString(),
                    runId = runId,
                    automationId = automationId,
                    sequence = 0,
                    phase = TracePhase.GATE_BLOCKED,
                    reasonCode = reasonCode,
                    detail = detail,
                    atEpochMs = atEpochMs,
                )
            )
        } finally {
            // A blocked gate is terminal for this run admission; it has no
            // later OUTCOME event that could release the counter.
            forgetRun(runId)
        }
    }

    /** Clears the per-run sequence counters for runs that have ended. */
    fun forgetRun(runId: String) {
        counters.remove(runId)
    }

    /** Test-visible size of the bounded-per-run sequencing state. */
    internal fun activeRunCountForTesting(): Int = counters.size
}

/** False for blocked, failed or uncertain trace rows; true for progress/success rows. */
private fun ExecutionTraceEvent.isSuccessfulTimelineEvent(): Boolean =
    phase != TracePhase.GATE_BLOCKED &&
        reasonCode !in setOf(
            TraceReasons.ACTION_FAILED,
            TraceReasons.VERIFICATION_FAILED,
            TraceReasons.OUTCOME_UNCERTAIN,
            TraceReasons.RUN_FAILED,
        )

/**
 * Rehydrates a trace row without parsing its free-form message. Rows written
 * before structured trace metadata existed safely return null.
 */
fun ExecutionTimelineEntry.toTraceEventOrNull(): ExecutionTraceEvent? {
    val runId = traceRunId ?: return null
    val sequence = traceSequence ?: return null
    val phase = tracePhase ?: return null
    val reasonCode = traceReasonCode ?: return null
    return ExecutionTraceEvent(
        id = id,
        runId = runId,
        automationId = automationId,
        sequence = sequence,
        phase = phase,
        reasonCode = reasonCode,
        detail = traceDetail,
        backend = channel,
        nodeId = traceNodeId,
        atEpochMs = startedAt,
        durationMs = durationMs,
    )
}
