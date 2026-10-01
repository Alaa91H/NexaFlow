package com.nexaflow.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiSettingsTabRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providesAgentsLogsAndAddTabsAndReportsSelection() {
        var selected = AiSettingsTab.AGENTS
        composeRule.setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(AiSettingsTab.AGENTS) }
                selected = tab
                AiSettingsTabRow(tab, onSelect = { tab = it })
            }
        }

        composeRule.onNodeWithText("Agents").assertIsDisplayed()
        composeRule.onNodeWithText("Logs").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AiSettingsTab.LOGS, selected)
        composeRule.onNodeWithText("Add agent").performClick()
        composeRule.waitForIdle()
        assertEquals(AiSettingsTab.ADD_AGENT, selected)
    }
}
