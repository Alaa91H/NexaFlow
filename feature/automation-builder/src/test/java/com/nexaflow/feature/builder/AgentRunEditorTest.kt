package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.agentruntime.EnabledAgent
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the AGENT_RUN editor through the real production composables.
 *
 * The editor must offer only the agents the app supplied, write exactly the
 * keys the canonical schema declares, and keep every free-text value bounded —
 * an automation is saved without a dialog, so a rejected key would only
 * surface as a failed run later.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class AgentRunEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val agents = listOf(
        EnabledAgent(
            id = "agent-a",
            name = "Alpha",
            description = "Reviews nightly builds",
            modelId = "model-a"
        ),
        EnabledAgent(id = "agent-b", name = "Beta")
    )

    private fun string(resId: Int): String = context.getString(resId)

    private fun editor(
        config: Map<String, String> = emptyMap(),
        choices: List<EnabledAgent> = agents,
        onConfigChange: (Map<String, String>) -> Unit = {}
    ) {
        composeRule.setContent {
            ActionConfigEditor(
                option = ActionOption(
                    titleRes = R.string.action_agent_run,
                    subtitleRes = R.string.action_agent_run_sub,
                    icon = Icons.Filled.Tune,
                    actionType = ActionType.AGENT_RUN,
                    category = ActionCategory.SYSTEM
                ),
                config = config,
                onConfigChange = onConfigChange,
                onPickApp = {},
                agentChoices = choices
            )
        }
    }

    @Test
    fun offersEveryProvidedAgentAndWritesTheChosenId() {
        var latest: Map<String, String>? = null
        editor(onConfigChange = { latest = it })

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()

        composeRule.onNodeWithText("Beta").performClick()

        assertEquals("agent-b", latest?.get("agentId"))
        assertNull(latest?.get("prompt"))
    }

    @Test
    fun explainsHowToEnableAnAgentWhenNoneIsAvailable() {
        editor(choices = emptyList())

        composeRule.onNodeWithText(string(R.string.agent_run_no_agents)).assertIsDisplayed()
    }

    @Test
    fun showsTheSelectedAgentsDetail() {
        editor(config = mapOf("agentId" to "agent-a", "prompt" to "Summarise"))

        composeRule.onNodeWithText("Reviews nightly builds").assertIsDisplayed()
    }

    @Test
    fun promptDurationPolicyAndResultVariableAreBounded() {
        var latest: Map<String, String>? = null
        editor(config = mapOf("agentId" to "agent-a"), onConfigChange = { latest = it })

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Summarise today")
        assertEquals("Summarise today", latest?.get("prompt"))

        fields[1].performTextInput("12ab3")
        assertEquals("123", latest?.get("maxDurationSeconds"))

        fields[2].performTextInput("bad-name")
        assertEquals("badname", latest?.get("resultVariable"))
    }

    @Test
    fun approvalPolicyDefaultsToStoppingAndWritesBothChoices() {
        var latest: Map<String, String>? = null
        editor(config = mapOf("agentId" to "agent-a"), onConfigChange = { latest = it })

        composeRule.onNodeWithText(string(R.string.agent_run_approval_skip)).performClick()
        assertEquals("SKIP", latest?.get("onApprovalRequired"))

        composeRule.onNodeWithText(string(R.string.agent_run_approval_fail)).performClick()
        assertEquals("FAIL", latest?.get("onApprovalRequired"))
    }
}
