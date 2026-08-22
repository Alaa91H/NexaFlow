package com.nexaflow.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCardExpansionTest {

    @Test
    fun tappingCollapsedTaskExpandsThatTask() {
        assertEquals(
            "task-a",
            nextExpandedAutomationId(currentExpandedId = null, tappedAutomationId = "task-a")
        )
    }

    @Test
    fun tappingExpandedTaskCollapsesIt() {
        assertNull(
            nextExpandedAutomationId(currentExpandedId = "task-a", tappedAutomationId = "task-a")
        )
    }

    @Test
    fun tappingAnotherTaskReplacesTheExpandedTask() {
        assertEquals(
            "task-b",
            nextExpandedAutomationId(currentExpandedId = "task-a", tappedAutomationId = "task-b")
        )
    }

    @Test
    fun taskConfigDetailRetainsEveryPersistedSettingInStableOrder() {
        val detail = taskConfigDetail(
            mapOf(
                "threshold" to "20",
                "direction" to "BELOW",
                "package" to "com.example.app",
                "event" to "CONNECTED"
            )
        )

        assertEquals(
            "direction: BELOW · event: CONNECTED · package: com.example.app · threshold: 20",
            detail
        )
        assertTrue(detail.contains("threshold: 20"))
    }
}
