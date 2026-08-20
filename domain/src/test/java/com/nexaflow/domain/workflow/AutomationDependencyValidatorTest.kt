package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDependencyValidatorTest {
    private fun automation(id: String, dependencies: List<String> = emptyList()) = Automation(
        id = id,
        name = id,
        description = "",
        icon = "bolt",
        iconColor = 1L,
        backgroundColor = 2L,
        category = "maintenance",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION)),
        createdAt = 1L,
        updatedAt = 1L,
        maintenanceProfile = MaintenanceProfile(
            kind = MaintenanceKind.AUTOMATION,
            dependencyAutomationIds = dependencies
        )
    )

    @Test
    fun acceptsAcyclicKnownDependencies() {
        val result = AutomationDependencyValidator.validate(
            listOf(automation("backup"), automation("report", listOf("backup")))
        )

        assertTrue(result.isValid)
        assertTrue(result.issuesFor("report").isEmpty())
    }

    @Test
    fun reportsMissingAndSelfDependencies() {
        val result = AutomationDependencyValidator.validate(
            listOf(automation("report", listOf("report", "missing")))
        )

        assertFalse(result.isValid)
        assertTrue(result.issuesFor("report").any { it.code == WorkflowValidationCode.SELF_DEPENDENCY })
        assertTrue(result.issuesFor("report").any { it.code == WorkflowValidationCode.MISSING_DEPENDENCY })
    }

    @Test
    fun reportsEveryParticipantInCycle() {
        val result = AutomationDependencyValidator.validate(
            listOf(
                automation("a", listOf("b")),
                automation("b", listOf("c")),
                automation("c", listOf("a")),
                automation("independent")
            )
        )

        assertTrue(result.issuesFor("a").any { it.code == WorkflowValidationCode.CIRCULAR_DEPENDENCY })
        assertTrue(result.issuesFor("b").any { it.code == WorkflowValidationCode.CIRCULAR_DEPENDENCY })
        assertTrue(result.issuesFor("c").any { it.code == WorkflowValidationCode.CIRCULAR_DEPENDENCY })
        assertTrue(result.issuesFor("independent").isEmpty())
    }
}
