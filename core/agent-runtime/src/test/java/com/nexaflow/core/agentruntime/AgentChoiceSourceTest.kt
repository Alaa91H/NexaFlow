package com.nexaflow.core.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentChoiceSourceTest {

    @Test
    fun pickerProjectionKeepsOnlyOptionalDetailOptional() {
        val agent = EnabledAgent(id = "agent-a", name = "Alpha")

        assertEquals("agent-a", agent.id)
        assertEquals("Alpha", agent.name)
        assertEquals("", agent.description)
        assertNull(agent.modelId)
    }

    @Test
    fun pickerProjectionCarriesTheDetailTheEditorShows() {
        val agent = EnabledAgent(
            id = "agent-b",
            name = "Beta",
            description = "Reviews builds",
            modelId = "model-b"
        )

        assertEquals("Reviews builds", agent.description)
        assertEquals("model-b", agent.modelId)
    }
}
