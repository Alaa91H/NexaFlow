package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationHealthReportTest {

    @Test
    fun `skipped maintenance does not count as completed success`() {
        val report = AutomationHealthAnalyzer.analyze(
            automationId = "maintenance",
            records = listOf(
                record(success = true, message = "Skipped: maintenance waiting for CHARGING_REQUIRED", at = 3L),
                record(success = true, message = "Completed", at = 2L)
            )
        )

        assertEquals(1, report.completedRuns)
        assertEquals(1, report.skippedRuns)
        assertEquals(AutomationHealthStatus.HEALTHY, report.status)
    }

    @Test
    fun `three consecutive failures need attention`() {
        val report = AutomationHealthAnalyzer.analyze(
            automationId = "maintenance",
            records = listOf(
                record(success = false, message = "network unavailable", at = 3L),
                record(success = false, message = "network unavailable", at = 2L),
                record(success = false, message = "network unavailable", at = 1L)
            )
        )

        assertEquals(3, report.consecutiveFailures)
        assertEquals(AutomationHealthStatus.NEEDS_ATTENTION, report.status)
    }

    @Test
    fun `legacy recovery queue deferrals are repaired to skipped outcomes`() {
        val report = AutomationHealthAnalyzer.analyze(
            automationId = "maintenance",
            records = List(3) { index ->
                record(
                    success = false,
                    message = "Deferred: recovery queue is full; resolve interrupted runs before retrying",
                    at = (3 - index).toLong()
                )
            }
        )

        assertEquals(3, report.skippedRuns)
        assertEquals(0, report.failedRuns)
        assertEquals(0, report.consecutiveFailures)
        assertEquals(null, report.latestFailureMessage)
        assertEquals(true, report.recoveryReviewPending)
        assertEquals(AutomationHealthStatus.NEEDS_ATTENTION, report.status)
    }

    @Test
    fun `ordinary skip does not hide recovery but admitted run clears warning`() {
        val blocked = record(true, "Skipped: recovery queue awaits review before this routine can run", 1L)
        val skipped = record(true, "Skipped: constraint not met", 2L)
        val pending = AutomationHealthAnalyzer.analyze("maintenance", listOf(skipped, blocked))
        assertEquals(true, pending.recoveryReviewPending)
        assertEquals(0, pending.failedRuns)
        val resolved = AutomationHealthAnalyzer.analyze(
            "maintenance", listOf(record(true, "Completed", 3L), skipped, blocked)
        )
        assertEquals(false, resolved.recoveryReviewPending)
        assertEquals(AutomationHealthStatus.HEALTHY, resolved.status)
    }

    @Test
    fun `no history reports no executions`() {
        assertEquals(
            AutomationHealthStatus.NO_EXECUTIONS,
            AutomationHealthAnalyzer.analyze("maintenance", emptyList()).status
        )
    }

    private fun record(success: Boolean, message: String, at: Long) = ExecutionRecord(
        id = "run-$at",
        automationId = "maintenance",
        automationName = "Maintenance",
        success = success,
        message = message,
        executedAt = at
    )
}
