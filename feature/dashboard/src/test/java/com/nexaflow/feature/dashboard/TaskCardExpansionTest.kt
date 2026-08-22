package com.nexaflow.feature.dashboard

import org.junit.Assert.assertEquals
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import org.junit.Assert.assertNull
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
    fun nfcTurnOffAtEndIsCountedAsAnEndAction() {
        val automation = Automation(
            id = "nfc-task",
            name = "NFC",
            description = "",
            icon = "nfc",
            iconColor = 0,
            backgroundColor = 0,
            category = "custom",
            priority = 0,
            enabled = true,
            triggers = emptyList(),
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_NFC,
                    config = mapOf("enabled" to "true"),
                    endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "false"))
                )
            ),
            createdAt = 0,
            updatedAt = 0
        )

        assertEquals(1, automation.exitBehaviorItemCount())
    }
}
