package com.nexaflow.feature.dashboard

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardFabActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun disableAnimations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @Test
    fun bothFloatingActionsAreVisibleAndNavigateIndependently() {
        var asked = 0
        var created = 0
        composeRule.setContent {
            MaterialTheme {
                DashboardFabActions(
                    askLabel = "Ask NexaFlow",
                    newTaskLabel = "New task",
                    onAsk = { asked++ },
                    onNewTask = { created++ }
                )
            }
        }
        composeRule.onNodeWithTag("ask_nexaflow_fab").performClick()
        composeRule.onNodeWithTag("new_task_fab").performClick()
        assertEquals(1, asked)
        assertEquals(1, created)
    }
}
