package com.nexaflow.feature.history

import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.TracePhase
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.domain.models.ExecutionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExecutionDetailsExplanationTest {

    @Test
    fun skippedRunUsesStructuredTraceWithExactStartTime() {
        val record = skippedRecord(executedAt = 1_000L)
        val timeline = listOf(
            ExecutionTimelineEntry(
                id = record.id,
                automationId = record.automationId,
                automationName = record.automationName,
                kind = "TRIGGER_ALL_GATE_BLOCKED",
                success = true,
                message = record.message,
                startedAt = 1_000L,
                durationMs = 1L,
            ),
            ExecutionTimelineEntry(
                id = "trace-1",
                automationId = record.automationId,
                automationName = "",
                kind = "TRACE:GATE_BLOCKED",
                success = false,
                message = TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
                startedAt = 1_000L,
                durationMs = 0L,
                traceRunId = "run-1",
                traceSequence = 1,
                tracePhase = TracePhase.GATE_BLOCKED,
                traceReasonCode = TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
                traceDetail = "charger is not connected",
            )
        )

        val explanation = explanationForRecord(record, timeline)

        assertEquals("explain_trigger_all_blocked", explanation?.explanationKey)
        assertEquals("fix_check_all_conditions", explanation?.fixKey)
        assertEquals("charger is not connected", explanation?.detail)
    }


    @Test
    fun explicitRunIdCorrelatesTerminalTraceEvenWhenEventTimeDiffers() {
        val record = ExecutionRecord(
            id = "record-failed",
            automationId = "task-1",
            automationName = "Routine",
            success = false,
            message = "Failed",
            executedAt = 1_000L,
        )
        val timeline = listOf(
            ExecutionTimelineEntry(
                id = record.id,
                automationId = record.automationId,
                automationName = record.automationName,
                kind = "RUN",
                success = false,
                message = record.message,
                startedAt = 1_000L,
                durationMs = 500L,
                runId = "run-7",
            ),
            ExecutionTimelineEntry(
                id = "trace-outcome",
                automationId = record.automationId,
                automationName = "",
                kind = "TRACE:OUTCOME",
                success = false,
                message = TraceReasons.ACTION_FAILED,
                startedAt = 1_500L,
                durationMs = 500L,
                traceRunId = "run-7",
                traceSequence = 1,
                tracePhase = TracePhase.OUTCOME,
                traceReasonCode = TraceReasons.ACTION_FAILED,
                traceDetail = "SYSTEM_WIFI: permission denied",
            )
        )

        val explanation = explanationForRecord(record, timeline)

        assertEquals("explain_action_failed", explanation?.explanationKey)
        assertEquals("fix_review_action_config", explanation?.fixKey)
        assertEquals("SYSTEM_WIFI: permission denied", explanation?.detail)
    }

    @Test
    fun nearbyTraceIsNotGuessedForAnotherRun() {
        val record = skippedRecord(executedAt = 1_000L)
        val timeline = listOf(
            ExecutionTimelineEntry(
                id = "trace-nearby",
                automationId = record.automationId,
                automationName = "",
                kind = "TRACE:GATE_BLOCKED",
                success = false,
                message = TraceReasons.CONSTRAINT_BLOCKED,
                startedAt = 1_001L,
                durationMs = 0L,
                traceRunId = "run-nearby",
                traceSequence = 1,
                tracePhase = TracePhase.GATE_BLOCKED,
                traceReasonCode = TraceReasons.CONSTRAINT_BLOCKED,
                traceDetail = "battery condition not met",
            )
        )

        assertNull(explanationForRecord(record, timeline))
    }

    @Test
    fun successfulNonSkippedRunDoesNotShowFailureExplanation() {
        val record = ExecutionRecord(
            id = "record-success",
            automationId = "task-1",
            automationName = "Routine",
            success = true,
            message = "Done",
            executedAt = 1_000L,
        )
        val timeline = listOf(
            ExecutionTimelineEntry(
                id = "trace-1",
                automationId = record.automationId,
                automationName = "",
                kind = "TRACE:GATE_BLOCKED",
                success = false,
                message = TraceReasons.CONSTRAINT_BLOCKED,
                startedAt = 1_000L,
                durationMs = 0L,
                traceRunId = "run-1",
                traceSequence = 1,
                tracePhase = TracePhase.GATE_BLOCKED,
                traceReasonCode = TraceReasons.CONSTRAINT_BLOCKED,
                traceDetail = "condition not met",
            )
        )

        assertNull(explanationForRecord(record, timeline))
    }

    private fun skippedRecord(executedAt: Long) = ExecutionRecord(
        id = "record-1",
        automationId = "task-1",
        automationName = "Routine",
        success = true,
        message = "Skipped: not all trigger conditions are true",
        executedAt = executedAt,
    )
}
