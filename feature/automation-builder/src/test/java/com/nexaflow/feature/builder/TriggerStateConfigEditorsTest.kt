package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun string(resId: Int): String = context.getString(resId)

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
