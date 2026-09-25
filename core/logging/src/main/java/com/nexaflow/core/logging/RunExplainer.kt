package com.nexaflow.core.logging

/**
 * The "Why didn't this run?" explainer (roadmap P0.4, NF-P0-047). Turns the
 * typed trace events already emitted by the engine into a user-facing
 * explanation with a concrete fix — no logcat, no free-form parsing. Every
 * known reason code maps to a stable explanation key the UI localizes;
 * unknown codes still render through [Explanation.fallback].
 */
object RunExplainer {

    /** What the user sees for one blocked/failed run. */
    data class Explanation(
        /** Localizable explanation key — the UI maps it to text. */
        val explanationKey: String,
        /** Localizable fix key; null when there is nothing actionable. */
        val fixKey: String? = null,
        /** Redacted supporting detail, safe to display and export. */
        val detail: String? = null,
    ) {
        companion object {
            /** Renders unknown reasons honestly instead of guessing. */
            fun fallback(reasonCode: String, detail: String?) = Explanation(
                explanationKey = "explain_unknown_reason",
                fixKey = null,
                detail = detail ?: reasonCode,
            )
        }
    }

    /**
     * Explains the most relevant gate-blocked or failure event for one run.
     * Prefers the terminal outcome, then gates, in reverse chronological
     * order — the last thing that stopped the run is what the user needs.
     */
    fun explain(events: List<ExecutionTraceEvent>): Explanation? {
        val relevant = events
            .filter { it.phase == TracePhase.GATE_BLOCKED || it.phase == TracePhase.OUTCOME }
            .sortedByDescending { it.sequence }
        val decisive = relevant.firstOrNull() ?: return null
        return explainEvent(decisive)
    }

    /**
     * Reads one run directly from the existing timeline without parsing
     * free-form messages. Ordinary timeline rows and pre-structured trace rows
     * are ignored safely.
     */
    fun explainTimeline(
        entries: List<ExecutionTimelineEntry>,
        runId: String,
    ): Explanation? = explain(
        entries
            .mapNotNull { it.toTraceEventOrNull() }
            .filter { it.runId == runId },
    )

    /** Explains a single event by its canonical reason code. */
    fun explainEvent(event: ExecutionTraceEvent): Explanation = when (event.reasonCode) {
        TraceReasons.CONSTRAINT_BLOCKED -> Explanation(
            explanationKey = "explain_constraint_blocked",
            fixKey = "fix_review_constraints",
            detail = event.detail,
        )
        TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
        TraceReasons.TRIGGER_AND_UNSATISFIED,
        TraceReasons.TRIGGER_STATE_UNKNOWN,
        TraceReasons.TRIGGER_STATE_UNAVAILABLE,
        TraceReasons.TRIGGER_STATE_ERROR,
        TraceReasons.TRIGGER_SEMANTICS_REVIEW_REQUIRED -> Explanation(
            explanationKey = "explain_trigger_all_blocked",
            fixKey = "fix_check_all_conditions",
            detail = event.detail,
        )
        TraceReasons.CAPABILITY_BLOCKED -> Explanation(
            explanationKey = "explain_capability_blocked",
            fixKey = "fix_grant_capability",
            detail = event.detail,
        )
        TraceReasons.CONFIGURATION_BLOCKED -> Explanation(
            explanationKey = "explain_configuration_blocked",
            fixKey = "fix_review_task_configuration",
            detail = event.detail,
        )
        TraceReasons.ADMISSION_REJECTED -> Explanation(
            explanationKey = "explain_admission_rejected",
            fixKey = "fix_check_running_state",
            detail = event.detail,
        )
        TraceReasons.MAINTENANCE_WAITING -> Explanation(
            explanationKey = "explain_maintenance_waiting",
            fixKey = null,
            detail = event.detail,
        )
        TraceReasons.MAINTENANCE_DUPLICATE -> Explanation(
            explanationKey = "explain_maintenance_duplicate",
            fixKey = null,
            detail = event.detail,
        )
        TraceReasons.ACTION_FAILED -> Explanation(
            explanationKey = "explain_action_failed",
            fixKey = "fix_review_action_config",
            detail = event.detail,
        )
        TraceReasons.VERIFICATION_FAILED -> Explanation(
            explanationKey = "explain_verification_failed",
            fixKey = "fix_retry_or_review",
            detail = event.detail,
        )
        TraceReasons.OUTCOME_UNCERTAIN -> Explanation(
            explanationKey = "explain_outcome_uncertain",
            fixKey = "fix_review_device_state",
            detail = event.detail,
        )
        TraceReasons.RUN_COMPLETED -> Explanation(
            explanationKey = "explain_run_completed",
            fixKey = null,
        )
        TraceReasons.RUN_FAILED -> Explanation(
            explanationKey = "explain_run_failed",
            fixKey = "fix_open_history",
            detail = event.detail,
        )
        else -> Explanation.fallback(event.reasonCode, event.detail)
    }

    /**
     * Builds a privacy-safe diagnostic report for one run: explanations plus
     * the redacted event rows. Safe to share by default — details were
     * redacted at the recorder boundary and are re-checked here.
     */
    fun buildReport(runId: String, events: List<ExecutionTraceEvent>): String {
        if (events.isEmpty()) return "Run $runId: no trace events recorded."
        val sorted = events.sortedBy { it.sequence }
        val lines = buildList {
            add("Run: $runId")
            for (event in sorted) {
                val explanation = explainEvent(event)
                val detail = SecretRedactor.redact(explanation.detail)
                add(
                    "[${event.phase.name}] ${event.reasonCode}" +
                        (detail?.let { " — $it" } ?: "") +
                        (event.backend?.let { " (via $it)" } ?: "")
                )
            }
            val decision = explain(events)
            if (decision != null) {
                add("")
                add("Why: ${decision.explanationKey}")
                decision.fixKey?.let { add("Fix: $it") }
            }
        }
        return lines.joinToString("\n")
    }
}
