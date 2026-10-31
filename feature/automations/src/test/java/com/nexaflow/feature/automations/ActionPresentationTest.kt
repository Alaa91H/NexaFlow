package com.nexaflow.feature.automations

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPresentationTest {

    @Test
    fun agentRunUsesTheSharedAgentCopyAndChatIcon() {
        val (titleRes, subtitleRes, icon) = actionPresentation(ActionType.AGENT_RUN)

        assertEquals(R.string.action_agent_run, titleRes)
        assertEquals(R.string.action_agent_run_sub, subtitleRes)
        assertEquals("AutoMirrored.Filled.Chat", icon.name)
    }
}
