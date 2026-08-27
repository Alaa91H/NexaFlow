package com.nexaflow.feature.automations

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
            routineHistoryRoute("routine-42", failuresOnly = true)
        )
    }
}
