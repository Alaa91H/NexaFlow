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
