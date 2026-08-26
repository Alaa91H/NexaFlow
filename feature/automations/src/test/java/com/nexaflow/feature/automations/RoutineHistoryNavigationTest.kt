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
}
