package com.nexaflow.feature.automations

import com.nexaflow.domain.models.ExecutionHistoryOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineHistoryNavigationTest {

    @Test
    fun `routine history route carries the selected routine identifier`() {
        assertEquals(
            "history?automationId=routine-42",
            routineHistoryRoute("routine-42")
        )
    }

    @Test
    fun `failure route includes the failed outcome filter`() {
        assertEquals(
            "history?automationId=routine-42&outcome=failed",
            routineHistoryRoute("routine-42", outcome = ExecutionHistoryOutcome.FAILED)
        )
    }

    @Test
    fun `skipped route includes the skipped outcome filter`() {
        assertEquals(
            "history?automationId=routine-42&outcome=skipped",
            routineHistoryRoute("routine-42", outcome = ExecutionHistoryOutcome.SKIPPED)
        )
    }
}
