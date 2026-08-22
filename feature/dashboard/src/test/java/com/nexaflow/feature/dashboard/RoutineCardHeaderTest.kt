package com.nexaflow.feature.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexaflow.domain.models.Automation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineCardHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerControlsRemainVisibleAndExpandedDetailsDoNotDuplicateToggle() {
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                RoutineCard(
                    row = AutomationRow(
                        automation = Automation(
                            id = "header-contract",
                            name = "Header contract",
                            description = "",
                            icon = "bolt",
                            iconColor = 0xFFFFFFFF,
                            backgroundColor = 0xFF3A3A3A,
                            category = "custom",
                            priority = 0,
                            enabled = true,
                            triggers = emptyList(),
                            actions = emptyList(),
                            createdAt = 0,
                            updatedAt = 0
                        ),
                        lastRunAt = null
                    ),
                    summary = "Task summary",
                    nextRun = null,
                    isRunning = false,
                    containerColor = Color.DarkGray,
                    expanded = expanded,
                    menuExpanded = false,
                    onRun = {},
                    onEdit = {},
                    onDelete = {},
                    onToggle = {},
                    onExpandedChange = { expanded = !expanded },
                    onLongClick = {},
                    onDismissMenu = {},
                    modifier = Modifier
                )
            }
        }

        assertPersistentHeaderAndSingleToggle()

        composeRule.onNodeWithTag(RoutineCardTestTags.HeaderExpand).performClick()
        composeRule.waitForIdle()

        assertPersistentHeaderAndSingleToggle()
        composeRule.onNodeWithText("Task summary").assertIsDisplayed()
    }

    private fun assertPersistentHeaderAndSingleToggle() {
        composeRule.onNodeWithTag(
            RoutineCardTestTags.HeaderIcon,
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutineCardTestTags.HeaderToggle).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutineCardTestTags.HeaderExpand).assertIsDisplayed()
        composeRule.onAllNodesWithTag(RoutineCardTestTags.HeaderToggle).assertCountEquals(1)
    }
}
