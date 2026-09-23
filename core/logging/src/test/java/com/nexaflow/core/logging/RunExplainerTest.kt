package com.nexaflow.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the run explainer (P0.4 NF-P0-047/NF-P0-048): every
 * canonical reason maps to a stable explanation+fix pair, unknown reasons
 * render honestly, and reports are privacy-safe by default.
 */
class RunExplainerTest {

    private fun event(
        phase: TracePhase,
        reason: String,
        sequence: Int = 1,
        detail: String? = null,
    ) = ExecutionTraceEvent(
        id = "e$sequence",
        runId = "run-1",
        automationId = "task-1",
        sequence = sequence,
        phase = phase,
        reasonCode = reason,
        detail = detail,
        atEpochMs = 1_000L,
    )

    @Test
    fun emptyEventsYieldNoExplanation() {
        assertNull(RunExplainer.explain(emptyList()))
    }

    @Test
    fun triggerAllGateBlockedExplainsWithFix() {
        val explanation = RunExplainer.explain(
            listOf(
                event(TracePhase.GATE_BLOCKED, TraceReasons.TRIGGER_ALL_GATE_BLOCKED, detail = "time window mismatch"),
            ),
        )!!
        assertEquals("explain_trigger_all_blocked", explanation.explanationKey)
        assertEquals("fix_check_all_conditions", explanation.fixKey)
        assertEquals("time window mismatch", explanation.detail)
    }

    @Test
    fun constraintBlockedExplainsWithFix() {
        val explanation = RunExplainer.explain(
            listOf(event(TracePhase.GATE_BLOCKED, TraceReasons.CONSTRAINT_BLOCKED)),
        )!!
        assertEquals("explain_constraint_blocked", explanation.explanationKey)
        assertEquals("fix_review_constraints", explanation.fixKey)
    }

    @Test
    fun capabilityBlockedPointsToGrant() {
        val explanation = RunExplainer.explain(
            listOf(event(TracePhase.GATE_BLOCKED, TraceReasons.CAPABILITY_BLOCKED)),
        )!!
        assertEquals("explain_capability_blocked", explanation.explanationKey)
        assertEquals("fix_grant_capability", explanation.fixKey)
    }

    @Test
    fun uncertainOutcomeExplainsHonestUnknownState() {
        val explanation = RunExplainer.explain(
            listOf(event(TracePhase.OUTCOME, TraceReasons.OUTCOME_UNCERTAIN)),
        )!!
        assertEquals("explain_outcome_uncertain", explanation.explanationKey)
        assertEquals("fix_review_device_state", explanation.fixKey)
    }

    @Test
    fun latestDecisiveEventWins() {
        val explanation = RunExplainer.explain(
            listOf(
                event(TracePhase.GATE_BLOCKED, TraceReasons.CONSTRAINT_BLOCKED, sequence = 1),
                event(TracePhase.GATE_BLOCKED, TraceReasons.TRIGGER_ALL_GATE_BLOCKED, sequence = 2),
            ),
        )!!
        assertEquals("explain_trigger_all_blocked", explanation.explanationKey)
    }

    @Test
    fun unknownReasonFallsBackHonesty() {
        val explanation = RunExplainer.explain(
            listOf(event(TracePhase.GATE_BLOCKED, "SOME_FUTURE_REASON", detail = "details here")),
        )!!
        assertEquals("explain_unknown_reason", explanation.explanationKey)
        assertNull(explanation.fixKey)
        assertEquals("details here", explanation.detail)
    }

    @Test
    fun successfulRunExplainsAsCompleted() {
        val explanation = RunExplainer.explain(
            listOf(event(TracePhase.OUTCOME, TraceReasons.RUN_COMPLETED)),
        )!!
        assertEquals("explain_run_completed", explanation.explanationKey)
        assertNull(explanation.fixKey)
    }

    @Test
    fun reportIsPrivacySafeAndStructured() {
        val report = RunExplainer.buildReport(
            runId = "run-9",
            events = listOf(
                event(TracePhase.ADMISSION, TraceReasons.RUN_STARTED, sequence = 1),
                event(
                    TracePhase.GATE_BLOCKED,
                    TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
                    sequence = 2,
                    detail = "token=supersecret123",
                ),
            ),
        )
        assertTrue(report.contains("Run: run-9"))
        assertTrue(report.contains("[GATE_BLOCKED] TRIGGER_ALL_GATE_BLOCKED"))
        assertTrue(report.contains("Why: explain_trigger_all_blocked"))
        assertTrue(report.contains("Fix: fix_check_all_conditions"))
        // Redaction re-applied at the report boundary as defense in depth.
        assertTrue("supersecret123" !in report)
        assertTrue("[REDACTED]" in report)
    }

    @Test
    fun timelineTraceRetainsStructuredFieldsForExplainer() = runTest {
        val store = InMemoryLogStore()
        val recorder = TraceRecorder(store)
        recorder.record(
            ExecutionTraceEvent(
                id = "trace-structured",
                runId = "run-structured",
                automationId = "task-1",
                sequence = 0,
                phase = TracePhase.OUTCOME,
                reasonCode = TraceReasons.RUN_FAILED,
                detail = "token=supersecret123",
                backend = "SHIZUKU",
                nodeId = "node-7",
                atEpochMs = 2_000L,
                durationMs = 25L,
            ),
        )

        val row = store.timeline().first().single()
        assertTrue(!row.success)
        assertEquals("run-structured", row.traceRunId)
        assertEquals(1, row.traceSequence)
        assertEquals(TracePhase.OUTCOME, row.tracePhase)
        assertEquals(TraceReasons.RUN_FAILED, row.traceReasonCode)
        assertEquals("node-7", row.traceNodeId)
        assertTrue(row.traceDetail?.contains("supersecret123") == false)

        val event = requireNotNull(row.toTraceEventOrNull())
        assertEquals("run-structured", event.runId)
        assertEquals(1, event.sequence)
        assertEquals("node-7", event.nodeId)
        assertEquals("SHIZUKU", event.backend)

        val explanation = RunExplainer.explainTimeline(
            store.timeline().first(),
            "run-structured",
        )
        assertEquals("explain_run_failed", explanation?.explanationKey)
    }

    @Test
    fun terminalOutcomeReleasesSequenceState() = runTest {
        val recorder = TraceRecorder(InMemoryLogStore())
        recorder.record(
            event(
                phase = TracePhase.ADMISSION,
                reason = TraceReasons.RUN_STARTED,
                sequence = 0,
            ),
        )
        assertEquals(1, recorder.activeRunCountForTesting())

        recorder.record(
            event(
                phase = TracePhase.OUTCOME,
                reason = TraceReasons.RUN_COMPLETED,
                sequence = 0,
            ),
        )
        assertEquals(0, recorder.activeRunCountForTesting())
    }

    @Test
    fun gateBlockedHelperReleasesSequenceState() = runTest {
        val recorder = TraceRecorder(InMemoryLogStore())
        recorder.recordGateBlocked(
            runId = "blocked-run",
            automationId = "task-1",
            reasonCode = TraceReasons.CONSTRAINT_BLOCKED,
            detail = "battery condition",
            atEpochMs = 1_000L,
        )
        assertEquals(0, recorder.activeRunCountForTesting())
    }

    @Test
    fun emptyRunReportStatesAbsence() {
        val report = RunExplainer.buildReport("run-empty", emptyList())
        assertTrue(report.contains("no trace events"))
    }
}
