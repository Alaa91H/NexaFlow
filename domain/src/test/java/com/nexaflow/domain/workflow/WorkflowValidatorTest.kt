package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowValidatorTest {
    private fun automation(
        actionConfig: Map<String, String> = emptyMap(),
        version: Int = Automation.CURRENT_WORKFLOW_VERSION,
    ) = Automation(
        id = "workflow-1",
        name = "Workflow",
        description = "",
        icon = "bolt",
        iconColor = 1L,
        backgroundColor = 2L,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, actionConfig)),
        createdAt = 1L,
        updatedAt = 1L,
        workflowVersion = version
    )

    @Test
    fun acceptsCurrentBoundedWorkflow() {
        assertTrue(WorkflowValidator.validate(automation()).isValid)
    }

    @Test
    fun flagsOversizedConfig() {
        val invalid = automation(
            actionConfig = mapOf("body" to "x".repeat(WorkflowValidator.MAX_CONFIG_VALUE_LENGTH + 1))
        )
        val result = WorkflowValidator.validate(invalid)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == WorkflowValidationCode.CONFIG_VALUE_TOO_LONG })
    }

    @Test
    fun legacyWorkflowVersionRemainsReadableWhileFutureVersionIsRejected() {
        assertTrue(
            WorkflowValidator.validate(
                automation(version = Automation.LEGACY_TRIGGER_SEMANTICS_VERSION)
            ).isValid
        )

        // Automation owns the hard model invariant: a future semantics version
        // must never become a runnable domain object. WorkflowValidator remains
        // the structured diagnostic boundary for objects that reach it.
        try {
            automation(version = Automation.CURRENT_WORKFLOW_VERSION + 1)
            throw AssertionError("expected future workflow version to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: unsupported future versions fail closed.
        }
    }

}
