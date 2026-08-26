package com.nexaflow.feature.automations

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineHistoryNavigationTest {

    @Test
    fun `routine history route carries the selected routine identifier`() {
        assertEquals(
            "history?automationId=routine-42",
            routineHistoryRoute("routine-42")
        )
    }
}
