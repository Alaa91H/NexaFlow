package com.nexaflow.feature.history

import androidx.compose.material3.MaterialTheme
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
class ActivitySectionTabsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun exposesExecutionsBlockedCallsAndSmsSections() {
        var selected = ActivitySection.EXECUTIONS
        composeRule.setContent {
            MaterialTheme {
                ActivitySectionTabs(selected) { selected = it }
            }
        }
        composeRule.onNodeWithText("Executions").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked calls").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(ActivitySection.BLOCKED_CALLS, selected)
        composeRule.onNodeWithText("SMS events").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(ActivitySection.SMS, selected)
    }
}
