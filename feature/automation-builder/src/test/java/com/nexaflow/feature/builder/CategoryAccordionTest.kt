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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Compose UI tests for [CategoryAccordion]. The picker keeps all categories
 * discoverable, but renders only the currently selected category's options.
 * This prevents the trigger/action builder from stacking long unrelated lists
 * while preserving a single-tap path to every option.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CategoryAccordionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

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
                selected.forEach { selectedOption ->
                    Text(text = "Card: $selectedOption")
                }
            }
        }
    }

    @Test
    fun enteringThePicker_showsOnlyTheDefaultCategoryOptions() {
        setHarness()

        composeRule.onNodeWithText("A1").assertIsDisplayed()
        composeRule.onNodeWithText("A2").assertIsDisplayed()
        composeRule.onAllNodesWithText("B1").assertCountEquals(0)
        composeRule.onAllNodesWithText("C1").assertCountEquals(0)
        composeRule.onNode(hasText("Cat A") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("Cat B") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("Cat C") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun selectingAnotherCategory_replacesTheVisibleOptions() {
        setHarness()

        composeRule.onNode(hasText("Cat B") and hasClickAction()).performClick()

        composeRule.onAllNodesWithText("A1").assertCountEquals(0)
        composeRule.onNodeWithText("B1").assertIsDisplayed()
        composeRule.onNodeWithText("B2").assertIsDisplayed()
        composeRule.onAllNodesWithText("C1").assertCountEquals(0)
    }

    @Test
    fun tappingTheSelectedCategory_collapsesThePickerOptions() {
        setHarness()

        composeRule.onNode(hasText("Cat A") and hasClickAction()).performClick()

        composeRule.onAllNodesWithText("A1").assertCountEquals(0)
        composeRule.onAllNodesWithText("A2").assertCountEquals(0)
        composeRule.onNode(hasText("Cat A") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun pickingAnOption_addsItsCardWithoutDuplicatingIt() {
        setHarness()

        composeRule.onNodeWithText("A1").performClick()

        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Card: A2").assertCountEquals(0)
        composeRule.onNodeWithText("A1").assertIsDisplayed()
    }

    @Test
    fun pickingOptionsFromDifferentCategories_addsBothCards() {
        setHarness()

        composeRule.onNodeWithText("A1").performClick()
        composeRule.onNode(hasText("Cat B") and hasClickAction()).performClick()
        composeRule.onNodeWithText("B2").performClick()

        composeRule.onNodeWithText("Card: A1").assertIsDisplayed()
        composeRule.onNodeWithText("Card: B2").assertIsDisplayed()
        composeRule.onAllNodesWithText("C1").assertCountEquals(0)
    }
}
