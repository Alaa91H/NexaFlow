package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render tests for the trigger sections that were split out of
 * `TriggerEditorCard` (NFA-19).
 *
 * They drive the production composables through their real chips and assert the
 * emitted draft, so the extraction is verified as behaviour rather than by
 * compilation alone. The section is hosted in a [Column] with the same spacing
 * the card uses; the sections are plain emitted children and do not position
 * themselves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class TriggerStateConfigEditorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

    private fun host(content: @Composable () -> Unit) {
        composeRule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }

    @Test
    fun batterySectionRendersLevelAndChargerFilters() {
        host {
            BatteryTriggerConfig(
                draft = TriggerDraft(TriggerType.BATTERY),
                onConfigChange = {},
            )
        }

        composeRule.onAllNodesWithText(string(R.string.battery_level_section)).onFirst().assertIsDisplayed()
        // Rows below the fold are checked for composition, not viewport
        // visibility: Robolectric's host window is shorter than the section.
        composeRule.onNodeWithText(string(R.string.charging_state_section)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.charger_any)).onFirst().assertExists()
    }

    @Test
    fun batteryDirectionChipWritesTheDirection() {
        var latest: TriggerDraft? = null
        host {
            BatteryTriggerConfig(
                draft = TriggerDraft(TriggerType.BATTERY, mapOf("above" to "80")),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNodeWithText("Below").performClick()
        composeRule.waitForIdle()

        assertEquals("BELOW", latest?.config?.get("direction"))
    }

    @Test
    fun signalStrengthSectionKeepsThresholdAndDirection() {
        var latest: TriggerDraft? = null
        host {
            SignalStrengthConfig(
                draft = TriggerDraft(TriggerType.WIFI_SIGNAL_STRENGTH),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNodeWithText("2").performClick()
        composeRule.waitForIdle()
        assertEquals("2", latest?.config?.get("threshold"))

        composeRule.onNodeWithText("Below").performClick()
        composeRule.waitForIdle()
        assertEquals("BELOW", latest?.config?.get("direction"))
    }

    @Test
    fun appInstalledSectionShowsTheEventChoicesAndPicker() {
        host {
            AppInstalledConfig(
                draft = TriggerDraft(TriggerType.APP_INSTALLED),
                onConfigChange = {},
                onPickApp = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.app_installed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.choose_app)).assertExists()
    }

    @Test
    fun batteryBelowDirectionSwitchesTheSubtitleAndLevelLabel() {
        composeRule.setContent {
            var draft by remember { mutableStateOf(TriggerDraft(TriggerType.BATTERY, mapOf("above" to "40"))) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryTriggerConfig(draft, { draft = it })
            }
        }

        composeRule.onNodeWithText("Below").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(string(R.string.battery_below_sub)).onFirst().assertExists()
        composeRule.onAllNodesWithText(string(R.string.maximum_battery, 40)).onFirst().assertExists()
    }

    @Test
    fun batteryAboveChipAndLevelSliderWriteTheirValues() {
        composeRule.setContent {
            var draft by remember { mutableStateOf(TriggerDraft(TriggerType.BATTERY, mapOf("direction" to "BELOW"))) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryTriggerConfig(draft, { draft = it })
            }
        }

        composeRule.onNodeWithText(string(R.string.battery_above)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(string(R.string.battery_above_sub)).onFirst().assertExists()

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(35f) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(string(R.string.minimum_battery, 35)).onFirst().assertExists()
    }

    @Test
    fun batteryChargingAndChargerFiltersWriteTheirKeys() {
        var latest: TriggerDraft? = null
        host {
            BatteryTriggerConfig(
                draft = TriggerDraft(TriggerType.BATTERY),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(string(R.string.charging_yes)).performClick()
        composeRule.waitForIdle()
        assertEquals("CHARGING", latest?.config?.get("chargingState"))

        composeRule.onNodeWithText(string(R.string.charger_wireless)).performClick()
        composeRule.waitForIdle()
        assertEquals("WIRELESS", latest?.config?.get("chargerType"))
    }

    /**
     * Volume is one trigger with a level per stream. The slider writes the
     * stream's own key and mirrors the first enabled stream onto the legacy
     * keys, so old readers and the new engine path agree.
     */
    @Test
    fun volumeSliderWritesPerStreamLevelsAndTheLegacyMirror() {
        var latest: TriggerDraft? = null
        host {
            var draft by remember { mutableStateOf(TriggerDraft(TriggerType.VOLUME_CHANGED)) }
            VolumeChangedConfig(draft) {
                draft = it
                latest = it
            }
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(65f) }
        composeRule.waitForIdle()

        assertEquals("65", latest?.config?.get("threshold_MUSIC"))
        assertEquals("MUSIC", latest?.config?.get("stream"))
        assertEquals("65", latest?.config?.get("threshold"))
    }

    @Test
    fun brightnessSliderStoresTheRomLevel() {
        var latest: TriggerDraft? = null
        host {
            BrightnessLevelConfig(
                draft = TriggerDraft(TriggerType.BRIGHTNESS_LEVEL),
                onConfigChange = { latest = it },
            )
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(255f) }
        composeRule.waitForIdle()

        assertEquals("255", latest?.config?.get("threshold"))
    }

    @Test
    fun storageSliderStoresMegabytes() {
        var latest: TriggerDraft? = null
        host {
            StorageLowConfig(
                draft = TriggerDraft(TriggerType.STORAGE_LOW),
                onConfigChange = { latest = it },
            )
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(2048f) }
        composeRule.waitForIdle()

        assertEquals("2048", latest?.config?.get("threshold"))
    }

    @Test
    fun batteryTemperatureSliderStoresCelsius() {
        var latest: TriggerDraft? = null
        host {
            BatteryTemperatureConfig(
                draft = TriggerDraft(TriggerType.BATTERY_TEMPERATURE),
                onConfigChange = { latest = it },
            )
        }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(45.5f) }
        composeRule.waitForIdle()

        assertEquals("45.5", latest?.config?.get("threshold"))
    }

    @Test
    fun screenTimeoutPresetWritesSeconds() {
        var latest: TriggerDraft? = null
        host {
            ScreenTimeoutChangedConfig(
                draft = TriggerDraft(TriggerType.SCREEN_TIMEOUT_CHANGED),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onAllNodesWithText(string(R.string.schema_duration_minutes, 1L)).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals("60", latest?.config?.get("seconds"))
    }

    @Test
    fun chargerSourceChipWritesTheFilter() {
        var latest: TriggerDraft? = null
        host {
            ChargerTriggerConfig(
                draft = TriggerDraft(TriggerType.CHARGER),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNodeWithText(string(R.string.charger_source_usb)).performClick()
        composeRule.waitForIdle()

        assertEquals("USB", latest?.config?.get("chargerType"))
    }

    @Test
    fun notificationKindChipsWriteTheCategoryFilter() {
        var latest: TriggerDraft? = null
        host {
            NotificationTriggerConfig(
                draft = TriggerDraft(TriggerType.NOTIFICATION),
                onConfigChange = { latest = it },
                onPickApp = {},
                onExplainSpecial = {},
                refreshKey = 0,
            )
        }

        composeRule.onNodeWithText(string(R.string.notification_kind_calls)).performClick()
        composeRule.waitForIdle()

        val categories = latest?.config?.get("categories").orEmpty()
        assertEquals(true, categories.contains("call"))
        assertEquals(true, categories.contains("missed_call"))
    }

    @Test
    fun wearEventFieldTrimsTheWatchIdentity() {
        var latest: TriggerDraft? = null
        host {
            WearEventConfig(
                draft = TriggerDraft(TriggerType.WEAR_EVENT),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput(" watch-1 ")

        assertEquals("watch-1", latest?.config?.get("watchInstallId"))
    }

    @Test
    fun wearEventSectionWritesTheConnectionState() {
        var latest: TriggerDraft? = null
        host {
            WearEventConfig(
                draft = TriggerDraft(TriggerType.WEAR_EVENT),
                onConfigChange = { latest = it },
            )
        }

        composeRule.onNodeWithText("Disconnected").performClick()
        composeRule.waitForIdle()

        assertEquals("DISCONNECTED", latest?.config?.get("state"))
    }
}
