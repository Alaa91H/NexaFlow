package com.nexaflow.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.NexaFlowCategory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the shared Ink components, not only their pure helpers.
 *
 * The component previews are the visual contract, but nothing executed them on
 * the JVM, so a Compose change could compile, pass the logic tests and still
 * crash at composition. Every showcase below is composed for real (Robolectric,
 * native graphics) and asserted through its text or content description, so the
 * composition path, the theme locals and the layout code are exercised on every
 * run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InkComponentsRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconTileRendersRoutineAndPickerVariants() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                IconTile(
                    icon = Icons.Filled.Timer,
                    category = NexaFlowCategory.TIME,
                    contentDescription = "time"
                )
                IconTile(
                    icon = Icons.Filled.LocationOn,
                    category = NexaFlowCategory.LOCATION,
                    contentDescription = "location"
                )
                IconTile(
                    icon = Icons.Filled.Bolt,
                    category = NexaFlowCategory.POWER,
                    size = Dimens.PickerTile,
                    radius = Dimens.PickerTileRadius,
                    picker = true,
                    contentDescription = "power"
                )
            }
        }

        composeRule.onNodeWithContentDescription("time").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("location").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("power").assertIsDisplayed()
    }

    @Test
    fun cardsRenderStatusPillsFlowRowsSearchAndChips() {
        composeRule.setContent { InkPreviewSurface(dark = true) { CardShowcase() } }

        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule.onNodeWithText("Needs permission").assertIsDisplayed()
        composeRule.onNodeWithText("Error").assertIsDisplayed()
        composeRule.onNodeWithText("At 07:30").assertIsDisplayed()
        composeRule.onNodeWithText("Search tasks").assertIsDisplayed()
        composeRule.onNodeWithText("Schedule").assertIsDisplayed()
    }

    @Test
    fun cardsRenderInTheLightScheme() {
        composeRule.setContent { InkPreviewSurface(dark = false) { CardShowcase() } }

        composeRule.onNodeWithText("Running").assertIsDisplayed()
    }

    @Test
    fun pickerGroupRendersRowsAndDescriptions() {
        composeRule.setContent { InkPreviewSurface(dark = true) { GroupShowcase() } }

        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("Runs at a fixed time on the days you pick").assertIsDisplayed()
        composeRule.onNodeWithText("Notification").assertIsDisplayed()
    }

    @Test
    fun screenHeaderAndDockRenderTheirChrome() {
        composeRule.setContent { InkPreviewSurface(dark = true) { ChromeShowcase() } }

        composeRule.onNodeWithText("Routines").assertIsDisplayed()
        composeRule.onNodeWithText("12 tasks · 9 active").assertIsDisplayed()
        composeRule.onNodeWithText("Ask NexaFlow…").assertIsDisplayed()
    }

    @Test
    fun bentoStatCardRendersValueCaptionAndSparkline() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                BentoStatCard(
                    eyebrow = "Today",
                    value = "18",
                    caption = "runs · last at 09:12",
                    sparkline = listOf(2, 0, 5, 1, 4, 3),
                    sparklineDescription = "18 runs today"
                )
            }
        }

        // The eyebrow is uppercased for Latin scripts by the type helper.
        composeRule.onNodeWithText("TODAY").assertIsDisplayed()
        composeRule.onNodeWithText("18").assertIsDisplayed()
        composeRule.onNodeWithText("runs · last at 09:12").assertIsDisplayed()
    }

    @Test
    fun emptyStateRendersItsCopy() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                EmptyState(
                    icon = Icons.Filled.Timer,
                    title = "No routines yet",
                    subtitle = "Create a task to run something automatically"
                )
            }
        }

        composeRule.onNodeWithText("No routines yet").assertIsDisplayed()
        composeRule.onNodeWithText("Create a task to run something automatically").assertIsDisplayed()
    }

    @Test
    fun loadingStateRendersItsSkeletons() {
        // The shimmer is an infinite transition: freeze the test clock so the
        // composition is asserted instead of waiting for it to settle.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            InkPreviewSurface(dark = true) { LoadingState(count = 2, contentDescription = "Loading") }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithContentDescription("Loading").assertExists()
    }

    @Test
    fun settingsRowsAndSwitchesRender() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    settingsGroup(title = "General") {
                        SettingRow(
                            icon = Icons.Filled.Timer,
                            title = "Run history",
                            subtitle = "Keep 30 days",
                            category = NexaFlowCategory.TIME,
                            onClick = {}
                        )
                        SettingRow(
                            icon = Icons.Filled.Settings,
                            title = "Advanced",
                            trailing = {
                                NexaFlowSwitch(
                                    checked = true,
                                    onCheckedChange = {},
                                    stateDescription = "On"
                                )
                            }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("General").assertIsDisplayed()
        composeRule.onNodeWithText("Run history").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced").assertIsDisplayed()
    }

    @Test
    fun dockButtonsRenderEnabledAndDisabled() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                DockPrimaryButton(label = "Add triggers", onClick = {}, enabled = false)
                DockPrimaryButton(label = "Add actions", onClick = {})
                DockTextAction(label = "Run now", onClick = {})
                DockTextAction(label = "Saving…", onClick = {}, enabled = false)
                AddNodeButton(label = "Add trigger", onClick = {})
                AddNodeButton(label = "Add action", onClick = {}, icon = Icons.Filled.Bolt)
                DockAskPill(label = "Ask NexaFlow…", onClick = {})
            }
        }

        composeRule.onNodeWithText("Add triggers").assertIsDisplayed()
        composeRule.onNodeWithText("Run now").assertIsDisplayed()
        composeRule.onNodeWithText("Add trigger").assertIsDisplayed()
    }

    @Test
    fun dockFallsBackToASolidSurfaceWhenGlassIsOff() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                CompositionLocalProvider(LocalGlassPreference provides GlassPreference.OFF) {
                    Dock(
                        leading = { DockAskPill(label = "Ask", onClick = {}) },
                        action = { DockIconButton(Icons.Filled.Bolt, "Run", onClick = {}) }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Ask").assertIsDisplayed()
    }

    @Test
    fun dockBlursWhenTheSourceIsRegistered() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                val glass = rememberGlassState()
                CompositionLocalProvider(
                    LocalGlassPreference provides GlassPreference.ON,
                    LocalGlassState provides glass
                ) {
                    Box(modifier = Modifier.fillMaxWidth().glassSource(glass)) {
                        Dock(
                            leading = { DockAskPill(label = "Ask", onClick = {}) },
                            action = { DockIconButton(Icons.Filled.Bolt, "Run", onClick = {}) }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Ask").assertIsDisplayed()
    }

    @Test
    fun rawColourStatusPillRendersItsComputedPair() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                // Call sites that already own a semantic pair (import summaries,
                // builder previews) use this overload instead of a variant.
                StatusPill(
                    text = "Imported 12 tasks",
                    background = DarkNexaFlowColors.success.copy(alpha = 0.17f),
                    contentColor = DarkNexaFlowColors.success
                )
            }
        }

        composeRule.onNodeWithText("Imported 12 tasks").assertIsDisplayed()
    }

    @Test
    fun categoryChipCoversUnselectedIconsAndPlainLabels() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                CategoryChipRow {
                    CategoryChip(
                        selected = false,
                        onClick = {},
                        label = "Apps",
                        leadingIcon = Icons.Filled.Timer
                    )
                    CategoryChip(selected = true, onClick = {}, label = "Sound", showCheck = false)
                }
            }
        }

        composeRule.onNodeWithText("Apps").assertIsDisplayed()
        composeRule.onNodeWithText("Sound").assertIsDisplayed()
    }

    @Test
    fun loadingStateWithoutDescriptionStillRendersSkeletons() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { InkPreviewSurface(dark = true) { LoadingState() } }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Routines").assertDoesNotExist()
    }

    @Test
    fun iconBadgeAndCheckableRowCoverTheirDefaults() {
        composeRule.setContent {
            InkPreviewSurface(dark = true) {
                IconBadge(icon = Icons.Filled.Bolt, containerColor = DarkNexaFlowColors.accentLabel)
                CheckableRow(selected = false, onClick = {}) { Text("Always") }
            }
        }

        composeRule.onNodeWithText("Always").assertIsDisplayed()
    }

    @Test
    fun badgeCheckableRowGlowAndSectionHeaderRender() {
        composeRule.setContent {
            InkPreviewSurface(dark = false) {
                Box(modifier = Modifier.fillMaxWidth().screenGlow(ScreenGlowVariant.Dashboard)) {
                    SectionHeader(text = "Scheduled tasks")
                }
                IconBadge(icon = Icons.Filled.Bolt, containerColor = DarkNexaFlowColors.accentLabel)
                CheckableRow(selected = true, onClick = {}) {
                    Text("Only when charging")
                }
            }
        }

        composeRule.onNodeWithText("Scheduled tasks").assertIsDisplayed()
        composeRule.onNodeWithText("Only when charging").assertIsDisplayed()
    }
}
