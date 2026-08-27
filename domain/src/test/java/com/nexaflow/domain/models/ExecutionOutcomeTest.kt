package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionOutcomeTest {

    @Test
    fun `route outcome parsing accepts supported values regardless of case`() {
        assertEquals(ExecutionHistoryOutcome.FAILED, ExecutionHistoryOutcome.fromRoute("FAILED"))
        assertEquals(ExecutionHistoryOutcome.SKIPPED, ExecutionHistoryOutcome.fromRoute(" skipped "))
        assertNull(ExecutionHistoryOutcome.fromRoute("completed"))
        assertNull(ExecutionHistoryOutcome.fromRoute(null))
    }

    @Test
    fun `classifier separates skipped completed and failed records`() {
        val skipped = record(success = true, message = "Skipped: constraint not met")
        val completed = record(success = true, message = "Completed")
        val failed = record(success = false, message = "network unavailable")

        assertTrue(ExecutionOutcomeClassifier.isSkipped(skipped))
        assertEquals(ExecutionHistoryOutcome.SKIPPED, ExecutionOutcomeClassifier.classify(skipped))
        assertFalse(ExecutionOutcomeClassifier.isSkipped(completed))
        assertNull(ExecutionOutcomeClassifier.classify(completed))
        assertEquals(ExecutionHistoryOutcome.FAILED, ExecutionOutcomeClassifier.classify(failed))
    }

    private fun record(success: Boolean, message: String) = ExecutionRecord(
        id = "run",
        automationId = "routine",
        automationName = "Routine",
        success = success,
        message = message,
        executedAt = 1L
    )
}
