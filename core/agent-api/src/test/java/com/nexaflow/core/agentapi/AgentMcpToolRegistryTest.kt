package com.nexaflow.core.agentapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMcpToolRegistryTest {

    @Test
    fun toolNamesAreDeterministicUniqueAndProtocolSafe() {
        val tools = AgentMcpToolRegistry.tools
        val names = tools.map { it.name }

        assertEquals(names.sorted(), names)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { TOOL_NAME.matches(it) })
    }

    @Test
    fun requiredPhaseSixToolsArePresent() {
        val names = AgentMcpToolRegistry.tools.mapTo(mutableSetOf()) { it.name }

        REQUIRED_TOOLS.forEach { name ->
            assertTrue("Missing MCP tool $name", name in names)
            assertNotNull(AgentMcpToolRegistry.find(name))
        }
    }

    @Test
    fun mutatingToolsAdvertiseIdempotency() {
        val mutations = AgentMcpToolRegistry.tools.filterNot { it.readOnly }

        assertTrue(mutations.isNotEmpty())
        assertTrue(mutations.all { it.idempotent })
        assertTrue(
            AgentMcpToolRegistry.find("nexaflow.delete_task")?.destructive == true
        )
    }

    private companion object {
        val TOOL_NAME = Regex("[A-Za-z0-9_.-]{1,128}")
        val REQUIRED_TOOLS = setOf(
            "nexaflow.get_capabilities",
            "nexaflow.list_triggers",
            "nexaflow.list_actions",
            "nexaflow.list_tasks",
            "nexaflow.get_task",
            "nexaflow.validate_task",
            "nexaflow.preview_schedule",
            "nexaflow.dry_run_task",
            "nexaflow.create_task",
            "nexaflow.update_task",
            "nexaflow.clone_task",
            "nexaflow.enable_task",
            "nexaflow.disable_task",
            "nexaflow.delete_task",
            "nexaflow.run_task",
            "nexaflow.get_history"
        )
    }
}
