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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
 * Compose UI tests (Robolectric) for [CategoryAccordion] — the fixed category
 * menu used by the trigger step, the execution step and the "when the task
 * ends" step.
 *
 * The menu follows the strict no-collapse contract: the chips stay pinned at
 * the top and the options of EVERY category are always rendered below,
 * stacked downwards — nothing is hidden behind a tap and nothing ever folds
 * away, even after an option is picked. (The builder hides the whole trigger
 * picker via its own outer gate once a trigger is chosen; the accordion
 * itself never collapses, and the execution picker stays fully visible while
 * actions are added.)
 *
 * Animations are disabled via the system animator scale so the assertions are
 * deterministic.
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
        // no-collapse assertions are deterministic.
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    /**
     * Renders the accordion with three categories (each with options) plus the
     * "selected card" list below, wired exactly like the execution step:
     * picking an option adds it as a card while the picker stays fully open.
     */
    private fun setHarness() {
        composeRule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf<Int?>(0) }
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
                                    }
                            )
                        }
                    }
                }
                // The "selected card" list, rendered below the picker.
                selected.forEach { s ->
                    Text(text = "Card: $s")
                }
            }
        }
    }

    // --- Strict no-collapse: everything is visible immediately -------------

    @Test
    fun enteringThePicker_showsAllCategoriesOptions_immediately() {
        setHarness()

        // No tap needed: every category's options are on screen from the start.
        composeRule.onNodeWithText("A1").assertIsDisplayed()
        composeRule.onNodeWithText("A2").assertIsDisplayed()
        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("B2").assertIsDisplayed()
        composeRule.onNodeWithText("C1").assertIsDisplayed()
        // The category chips are always visible too.
        composeRule.onNode(hasText("Cat A") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("Cat B") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("Cat C") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun tappingAChip_neverHidesAnyCategorysOptions() {
        setHarness()

        composeRule.onNode(hasText("Cat B") and hasClickAction()).performClick()

        // Selecting another chip must not fold anything away.
        composeRule.onNodeWithText("A1").assertIsDisplayed()
        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("C1").assertIsDisplayed()
    }

    @Test
    fun selectingOneCategory_keepsTheOtherCategoriesOpen() {
        setHarness()

        composeRule.onNode(hasText("Cat A") and hasClickAction()).performClick()

        composeRule.onNodeWithText("A1").assertIsDisplayed()
        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("C1").assertIsDisplayed()
    }

    // --- Pick → add card, picker stays open ---------------------------------

    @Test
    fun pickingAnOption_addsTheCard_andKeepsTheOptionsVisible() {
        setHarness()

        composeRule.onNodeWithText("A1").performClick()

        // The card for the picked option is added below the picker.
        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        // Only one card — the option was added, not duplicated.
        composeRule.onNodeWithText("Card: A2").assertDoesNotExist()
        // Strict no-collapse: the picker never folds away after a pick.
        composeRule.onNodeWithText("A1").assertIsDisplayed()
        composeRule.onNodeWithText("B1").assertIsDisplayed()
    }

    @Test
    fun pickingOptionsFromTwoCategories_addsTwoCards() {
        setHarness()

        composeRule.onNodeWithText("A1").performClick()
        composeRule.onNodeWithText("B2").performClick()

        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        composeRule.onNodeWithText("Card: B2").assertIsDisplayed()
        // The picker stayed fully open the whole time.
        composeRule.onNodeWithText("C1").assertIsDisplayed()
    }
}
