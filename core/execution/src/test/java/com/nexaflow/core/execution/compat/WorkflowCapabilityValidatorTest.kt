package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCapabilityValidatorTest {
    @Test
    fun `accepts workflow whose public command requirements are available`() {
        val automation = automation(ActionType.SYSTEM_OPEN_URL)
        val snapshot = CapabilitySnapshot(
            reports = mapOf(
                CapabilityId.INTENT_LAUNCH to CapabilityAvailabilityReport(
                    CapabilityId.INTENT_LAUNCH, CapabilityAvailability.AVAILABLE, emptyList()
                )
            )
        )

        assertTrue(WorkflowCapabilityValidator.validate(automation, snapshot).admissible)
    }

    @Test
    fun `blocks workflow when documented public capability is absent`() {
        val result = WorkflowCapabilityValidator.validate(automation(ActionType.SYSTEM_OPEN_URL), CapabilitySnapshot())

        assertFalse(result.admissible)
        assertTrue(CapabilityId.INTENT_LAUNCH in result.missingCapabilities)
    }

    @Test
    fun `admits legacy elevated action for the concrete handler to verify root`() {
        val result = WorkflowCapabilityValidator.validate(automation(ActionType.SYSTEM_REBOOT), CapabilitySnapshot())

        assertTrue(result.admissible)
    }

    private fun automation(action: ActionType) = Automation(
        id = "validator",
        name = "Validator",
        description = "Test workflow",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.TIME, emptyMap())),
        actions = listOf(Action(action, emptyMap())),
        createdAt = 1L,
        updatedAt = 1L
    )
}
