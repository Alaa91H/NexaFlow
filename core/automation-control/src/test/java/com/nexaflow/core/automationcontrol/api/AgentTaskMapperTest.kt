package com.nexaflow.core.automationcontrol.api

import com.nexaflow.domain.models.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentTaskMapperTest {

    @Test
    fun newAgentDraftDefaultsToDisabledAutomation() {
        val automation = AgentTaskMapper.toAutomation(
            draft = AgentTaskDraftV1(name = "Night routine"),
            id = "generated-id",
            nowMillis = 42L
        )

        assertEquals("generated-id", automation.id)
        assertEquals("Night routine", automation.name)
        assertFalse(automation.enabled)
        assertEquals(42L, automation.createdAt)
        assertEquals(42L, automation.updatedAt)
        assertEquals(Automation.CURRENT_WORKFLOW_VERSION, automation.workflowVersion)
    }

    @Test
    fun updatePreservesInternalIdentityFields() {
        val existing = baseAutomation(
            createdAt = 10L,
            updatedAt = 20L,
            deepLinkToken = "private-token"
        )

        val mapped = AgentTaskMapper.toAutomation(
            draft = AgentTaskDraftV1(name = "Updated"),
            id = existing.id,
            nowMillis = 30L,
            existing = existing
        )

        assertEquals(10L, mapped.createdAt)
        assertEquals(30L, mapped.updatedAt)
        assertEquals("private-token", mapped.deepLinkToken)
    }

    @Test
    fun unknownEnumProducesStableMappingError() {
        val exception = assertThrows(AgentTaskMappingException::class.java) {
            AgentTaskMapper.toAutomation(
                draft = AgentTaskDraftV1(
                    name = "Invalid",
                    triggers = listOf(AgentTriggerDraftV1(type = "NOT_A_TRIGGER"))
                ),
                id = "id",
                nowMillis = 1L
            )
        }

        assertEquals("unknown_enum_value", exception.error.code)
        assertEquals("triggers[0].type", exception.error.path)
    }

    private fun baseAutomation(
        createdAt: Long,
        updatedAt: Long,
        deepLinkToken: String?
    ) = Automation(
        id = "existing",
        name = "Existing",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "custom",
        priority = 1,
        enabled = false,
        triggers = emptyList(),
        actions = emptyList(),
        cooldownSeconds = 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deepLinkToken = deepLinkToken
    )
}
