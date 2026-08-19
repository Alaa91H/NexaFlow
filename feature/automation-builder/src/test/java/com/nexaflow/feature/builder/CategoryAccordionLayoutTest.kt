package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowAnimatedVisibility
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The category picker is shared by the trigger and action catalogues. It must
 * stay a single horizontally scrollable strip; wrapping chips into a second
 * line lets the following catalogue rows paint into the same visual space on
 * compact RTL phones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CategoryAccordionLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoryTabs_doNotOverlapCatalogContent_onNarrowRtlLargeFontLayout() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 1.8f)
            ) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(260.dp)
                    ) {
                        CategoryAccordion(
                            tabs = listOf(
                                "الجدولة" to Icons.Filled.CalendarMonth,
                                "الجهاز" to Icons.Filled.PhoneAndroid,
                                "الاتصال" to Icons.Filled.Wifi,
                                "الموقع" to Icons.Filled.LocationOn,
                                "التطبيقات" to null,
                                "بلوتوث" to Icons.Filled.Bluetooth
                            ),
                            expandedIndex = 0,
                            onExpandedChange = {}
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("category_catalog_content")
                            )
                        }
                    }
                }
            }
        }

        val tabsBounds = composeRule
            .onNodeWithTag("category_tabs")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val contentBounds = composeRule
            .onNodeWithTag("category_catalog_content")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Category tabs must occupy one bounded strip instead of wrapping: $tabsBounds",
            tabsBounds.height <= 72f
        )
        assertTrue(
            "Catalog content must start below the category tabs: tabs=$tabsBounds, content=$contentBounds",
            contentBounds.top >= tabsBounds.bottom
        )
    }

    @Test
    fun conditionCatalogRows_shareExecutionCatalogGeometry_inNarrowRtlLayout() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 1.4f)
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.width(280.dp)) {
                        Column {
                            TriggerOptionRow(
                                type = TriggerType.TIME,
                                checked = false,
                                onSelect = {},
                                modifier = Modifier.testTag("condition_catalog_row")
                            )
                            ActionOptionRow(
                                option = actionOptions.first(),
                                checked = false,
                                onToggle = {},
                                modifier = Modifier.testTag("execution_catalog_row")
                            )
                        }
                    }
                }
            }
        }

        val conditionBounds = composeRule
            .onNodeWithTag("condition_catalog_row")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val executionBounds = composeRule
            .onNodeWithTag("execution_catalog_row")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Conditions and execution must use the same full-width catalog row in RTL",
            conditionBounds.width == executionBounds.width
        )
        assertTrue(
            "Condition catalog rows must retain a visible bounded height",
            conditionBounds.height > 0f
        )
    }

    @Test
    fun animatedConditionCatalog_stacksDirectChildren_inNarrowLtrLargeFontLayout() {
        assertDirectConditionCatalogIsOrdered(
            direction = LayoutDirection.Ltr,
            fontScale = 1.8f
        )
    }

    @Test
    fun animatedConditionCatalog_stacksDirectChildren_inNarrowRtlLargeFontLayout() {
        assertDirectConditionCatalogIsOrdered(
            direction = LayoutDirection.Rtl,
            fontScale = 1.8f
        )
    }

    @Test
    fun categoryTabs_boundLongArabicLabels_andIgnoreStaleSelection() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 2f)
            ) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(260.dp)
                    ) {
                        CategoryAccordion(
                            tabs = listOf(
                                "الجدولة والتقويم التفصيلية" to Icons.Filled.CalendarMonth,
                                "إعدادات الجهاز المتقدمة" to Icons.Filled.PhoneAndroid,
                                "الاتصال والشبكات اللاسلكية" to Icons.Filled.Wifi
                            ),
                            // A device capability refresh can remove categories
                            // while the builder survives configuration changes.
                            // The picker must ignore this stale saved index.
                            expandedIndex = 99,
                            onExpandedChange = {}
                        ) {
                            Box(modifier = Modifier.testTag("stale_category_content"))
                        }
                    }
                }
            }
        }

        val tabsBounds = composeRule
            .onNodeWithTag("category_tabs")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val firstTabBounds = composeRule
            .onNodeWithTag("category_tab_0")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onAllNodesWithTag("stale_category_content").assertCountEquals(0)
        assertTrue(
            "A long localised category label must remain within the capped chip width: $firstTabBounds",
            firstTabBounds.width <= 180f
        )
        assertTrue(
            "The tab strip must retain a single-row height under a 2x RTL font scale: $tabsBounds",
            tabsBounds.height <= 72f
        )
    }

    /**
     * Mirrors the production condition-picker structure: common options,
     * search, and the category catalogue are siblings inside the animated
     * container. Their bounds must remain strictly ordered in every direction.
     */
    private fun assertDirectConditionCatalogIsOrdered(
        direction: LayoutDirection,
        fontScale: Float
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalDensity provides Density(density = 1f, fontScale = fontScale)
            ) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(440.dp)
                    ) {
                        NexaFlowAnimatedVisibility(visible = true) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("condition_common_options")
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("condition_search")
                            )
                            CategoryAccordion(
                                tabs = listOf(
                                    "Schedule" to Icons.Filled.CalendarMonth,
                                    "Device" to Icons.Filled.PhoneAndroid
                                ),
                                expandedIndex = 0,
                                onExpandedChange = {}
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .testTag("condition_category_content")
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .testTag("condition_category_second_content")
                                )
                            }
                        }
                    }
                }
            }
        }

        val common = composeRule
            .onNodeWithTag("condition_common_options")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val search = composeRule
            .onNodeWithTag("condition_search")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val tabs = composeRule
            .onNodeWithTag("category_tabs")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val categoryContent = composeRule
            .onNodeWithTag("condition_category_content")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val secondCategoryContent = composeRule
            .onNodeWithTag("condition_category_second_content")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Direct animated children must not overlap: common=$common, search=$search",
            common.bottom <= search.top
        )
        assertTrue(
            "The category strip must begin after the search row: search=$search, tabs=$tabs",
            search.bottom <= tabs.top
        )
        assertTrue(
            "Category options must begin below the strip: tabs=$tabs, content=$categoryContent",
            tabs.bottom <= categoryContent.top
        )
        assertTrue(
            "Direct category options must not overlap: first=$categoryContent, second=$secondCategoryContent",
            categoryContent.bottom <= secondCategoryContent.top
        )
    }
}
