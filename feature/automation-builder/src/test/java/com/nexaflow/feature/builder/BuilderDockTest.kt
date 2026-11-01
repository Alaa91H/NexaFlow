package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The builder is one page (§7.3), so its Dock has no per-step Continue button:
 * it always states what the routine already contains and always offers the one
 * action that finishes it. These tests pin that contract — the previous harness
 * asserted step-advancing labels that no longer exist.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BuilderDockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun creatingSavesInOneTap() {
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                BuilderDock(
                    isEditing = false,
                    triggerCount = 1,
                    actionCount = 1,
                    onSave = { saved = true }
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.create_task), useUnmergedTree = true)
            .performClick()

        assertTrue(saved)
    }

    @Test
    fun editingSavesInsteadOfCreating() {
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                BuilderDock(
                    isEditing = true,
                    triggerCount = 1,
                    actionCount = 1,
                    onSave = { saved = true }
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.save), useUnmergedTree = true)
            .performClick()

        assertTrue(saved)
    }

    @Test
    fun theDockStatesWhatTheRoutineContains() {
        composeRule.setContent {
            MaterialTheme {
                BuilderDock(
                    isEditing = false,
                    triggerCount = 2,
                    actionCount = 3,
                    onSave = {}
                )
            }
        }

        composeRule
            .onNodeWithText(
                context.getString(R.string.builder_dock_summary, 2, 3),
                useUnmergedTree = true
            )
            .assertExists()
    }

    @Test
    fun anEmptyRoutineStillOffersTheAction() {
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                BuilderDock(
                    isEditing = false,
                    triggerCount = 0,
                    actionCount = 0,
                    onSave = { saved = true }
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.create_task), useUnmergedTree = true)
            .performClick()

        // Save itself explains what is missing; hiding the only button would
        // leave the page without an exit other than Back.
        assertTrue(saved)
    }
}
