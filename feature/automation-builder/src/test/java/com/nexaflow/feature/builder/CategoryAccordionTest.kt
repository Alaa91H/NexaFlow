package com.nexaflow.feature.builder

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests (Robolectric) for [CategoryAccordion] — the Google-2026
 * single-open category picker used by the execution step, the trigger step
 * and the "when the task ends" step.
 *
 * The harness below reproduces the builder's exact wiring: each category
 * renders its options, tapping an option adds it to the selected list (the
 * "card") and collapses the accordion. Assertions cover the two contracts the
 * builder depends on:
 *  1. Only ONE category is open at a time (tapping a chip closes the other).
 *  2. Picking an option adds the card AND folds the accordion away.
 *
 * Animations are disabled via the system animator scale so expand/collapse is
 * instant and deterministic (the reduced-motion path of
 * [NexaFlowAnimatedVisibility]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Compose-UI tests stay pinned to SDK 35 (see SpecialPermissionStatusRowTest
// for the Espresso/InputManager rationale); every non-Compose test runs on
// the real SDK 37 via the Java 21 toolchain.
@Config(sdk = [35])
class CategoryAccordionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun disableAnimations() {
        // Reduced-motion path: content appears/disappears instantly, so the
        // single-open and collapse assertions are deterministic.
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    /**
     * Renders the accordion with three categories (each with options) plus the
     * "selected card" list below, wired exactly like the builder: picking an
     * option adds it and folds the accordion.
     */
    private fun setHarness() {
        composeRule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf<Int?>(null) }
                val selected = remember { mutableStateListOf<String>() }
                CategoryAccordion(
                    tabs = listOf("Cat A" to null, "Cat B" to null, "Cat C" to null),
                    expandedIndex = expanded,
                    onExpandedChange = { expanded = it }
                ) { index ->
                    val options = when (index) {
                        0 -> listOf("A1", "A2")
                        1 -> listOf("B1", "B2")
                        else -> listOf("C1")
                    }
                    Column {
                        options.forEach { option ->
                            Text(
                                text = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (option !in selected) selected.add(option)
                                        // Collapse so the user lands on the new card's config.
                                        expanded = null
                                    }
                            )
                        }
                    }
                }
                // The "selected card" list, rendered below the accordion.
                selected.forEach { s ->
                    Text(text = "Card: $s")
                }
            }
        }
    }

    // --- Single-open invariant ---------------------------------------------

    @Test
    fun initially_noCategoryIsOpen_noOptionsShown() {
        setHarness()

        composeRule.onNodeWithText("A1").assertDoesNotExist()
        composeRule.onNodeWithText("B1").assertDoesNotExist()
        composeRule.onNodeWithText("C1").assertDoesNotExist()
        // The category chips themselves are always visible.
        composeRule.onNodeWithText("Cat A").assertIsDisplayed()
        composeRule.onNodeWithText("Cat B").assertIsDisplayed()
    }

    @Test
    fun tappingAChip_opensOnlyItsOptions() {
        setHarness()

        composeRule.onNodeWithText("Cat B").performClick()

        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("B2").assertIsDisplayed()
        composeRule.onNodeWithText("A1").assertDoesNotExist()
        composeRule.onNodeWithText("C1").assertDoesNotExist()
    }

    @Test
    fun switchingChip_closesThePreviousCategory_keepsOnlyOneOpen() {
        setHarness()

        composeRule.onNodeWithText("Cat A").performClick()
        composeRule.onNodeWithText("A1").assertIsDisplayed()

        // Open a second category: the first must close — only ONE open chip.
        composeRule.onNodeWithText("Cat B").performClick()

        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("A1").assertDoesNotExist()
        composeRule.onNodeWithText("A2").assertDoesNotExist()
    }

    @Test
    fun tappingTheOpenChipAgain_collapsesIt() {
        setHarness()

        composeRule.onNodeWithText("Cat C").performClick()
        composeRule.onNodeWithText("C1").assertIsDisplayed()

        composeRule.onNodeWithText("Cat C").performClick()

        composeRule.onNodeWithText("C1").assertDoesNotExist()
    }

    // --- Pick → add card + collapse -----------------------------------------

    @Test
    fun pickingAnOption_addsTheCard_andCollapsesTheAccordion() {
        setHarness()

        composeRule.onNodeWithText("Cat A").performClick()
        composeRule.onNodeWithText("A1").assertIsDisplayed()

        composeRule.onNodeWithText("A1").performClick()

        // The card for the picked option is added below the accordion.
        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        // The accordion folds away: no options are visible anymore.
        composeRule.onNodeWithText("A1").assertDoesNotExist()
        composeRule.onNodeWithText("A2").assertDoesNotExist()
        // Only one card — the option was added, not duplicated.
        composeRule.onNodeWithText("Card: A2").assertDoesNotExist()
    }

    @Test
    fun pickingOptionsFromTwoCategories_addsTwoCards() {
        setHarness()

        composeRule.onNodeWithText("Cat A").performClick()
        composeRule.onNodeWithText("A1").performClick()

        composeRule.onNodeWithText("Cat B").performClick()
        composeRule.onNodeWithText("B2").performClick()

        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        composeRule.onNodeWithText("Card: B2").assertIsDisplayed()
    }
}
