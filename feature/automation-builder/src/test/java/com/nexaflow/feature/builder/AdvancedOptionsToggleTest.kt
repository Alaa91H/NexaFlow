package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdvancedOptionsToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun advancedSectionsCanBeExpandedAndCollapsedIndependently() {
        composeRule.setContent {
            MaterialTheme {
                var triggersExpanded by remember { mutableStateOf(false) }
                var actionsExpanded by remember { mutableStateOf(false) }
                Column {
                    AdvancedOptionsHeader(
                        title = "Advanced triggers",
                        expanded = triggersExpanded,
                        onToggle = { triggersExpanded = !triggersExpanded }
                    )
                    if (triggersExpanded) Text("Trigger option")
                    AdvancedOptionsHeader(
                        title = "Advanced actions",
                        expanded = actionsExpanded,
                        onToggle = { actionsExpanded = !actionsExpanded }
                    )
                    if (actionsExpanded) Text("Action option")
                }
            }
        }

        composeRule.onNodeWithText("Advanced triggers").performClick()
        composeRule.onNodeWithText("Trigger option").assertIsDisplayed()
        composeRule.onAllNodesWithText("Action option").assertCountEquals(0)
        composeRule.onNodeWithText("Advanced actions").performClick()
        composeRule.onNodeWithText("Action option").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced triggers").performClick()
        composeRule.onAllNodesWithText("Trigger option").assertCountEquals(0)
        composeRule.onNodeWithText("Action option").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced actions").performClick()
        composeRule.onAllNodesWithText("Action option").assertCountEquals(0)
    }
}
