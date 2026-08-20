package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BuilderBottomPrimaryActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun lowerNavigation_advancesFromTriggersToActions() {
        var advancedTo: Int? = null

        composeRule.setContent {
            MaterialTheme {
                BuilderBottomPrimaryAction(
                    step = 0,
                    triggerCount = 1,
                    actionCount = 0,
                    onAdvance = { advancedTo = it },
                    onSave = {}
                )
            }
        }

        composeRule
            .onNodeWithText(
                context.getString(R.string.permission_continue),
                useUnmergedTree = true
            )
            .performClick()

        assertEquals(1, advancedTo)
    }

    @Test
    fun lowerNavigation_advancesFromActionsToReview() {
        var advancedTo: Int? = null

        composeRule.setContent {
            MaterialTheme {
                BuilderBottomPrimaryAction(
                    step = 1,
                    triggerCount = 1,
                    actionCount = 1,
                    onAdvance = { advancedTo = it },
                    onSave = {}
                )
            }
        }

        composeRule
            .onNodeWithText(
                context.getString(R.string.quick_save),
                useUnmergedTree = true
            )
            .performClick()

        assertEquals(2, advancedTo)
    }

    @Test
    fun lowerNavigation_savesOnlyAtReviewWithRequiredDrafts() {
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                BuilderBottomPrimaryAction(
                    step = 2,
                    triggerCount = 1,
                    actionCount = 1,
                    onAdvance = {},
                    onSave = { saved = true }
                )
            }
        }

        composeRule
            .onNodeWithText(
                context.getString(R.string.create_task),
                useUnmergedTree = true
            )
            .performClick()

        assertTrue(saved)
    }

    @Test
    fun lowerNavigation_hidesWhenTheCurrentStepIsIncomplete() {
        var advanced = false
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                BuilderBottomPrimaryAction(
                    step = 0,
                    triggerCount = 0,
                    actionCount = 0,
                    onAdvance = { advanced = true },
                    onSave = { saved = true }
                )
            }
        }

        assertFalse(advanced)
        assertFalse(saved)
        assertTrue(
            composeRule
                .onAllNodesWithText(
                    context.getString(R.string.permission_continue),
                    useUnmergedTree = true
                )
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }
}
