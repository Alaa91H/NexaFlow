package com.nexaflow.core.automationcontrol.api

import com.nexaflow.domain.models.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentTaskMapperTest {

    @Test
    fun agentTaskUpdateCanPreserveNativeCanonicalNodesWhenDraftCarriesThem() {
        val field = com.nexaflow.domain.canonical.CanonicalFieldId("duration_ms")
        val duration = com.nexaflow.domain.canonical.DurationValue(8_000L)
        val schema = com.nexaflow.domain.canonical.CanonicalNativeNodeSchemaRegistry.delay
        val node = com.nexaflow.domain.canonical.CanonicalWorkflowNode(
            kind = com.nexaflow.domain.canonical.NodeSchemaKind.ACTION,
            definitionId = schema.schemaId,
            schema = schema,
            node = com.nexaflow.domain.canonical.WaitNode(
                com.nexaflow.domain.canonical.CanonicalNodeId("native.action.agent.delay"), duration,
            ),
            arguments = listOf(com.nexaflow.domain.canonical.NodeFieldValue(field, duration)),
        )
        val existing = baseAutomation(1L, 2L, null).copy(canonicalNodes = listOf(node))
        val mapped = AgentTaskMapper.toAutomation(
            draft = AgentTaskDraftV1(name = "Updated", canonicalNodes = listOf(node)),
            id = existing.id,
            nowMillis = 3L,
            existing = existing,
        )
        assertEquals(listOf(node), mapped.canonicalNodes)
    }

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
