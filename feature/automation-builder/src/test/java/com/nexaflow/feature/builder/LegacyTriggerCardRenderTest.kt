package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
 * Render tests for the trigger families the canonical schema editor does not
 * own yet (NFA-19).
 *
 * `TriggerEditorCard` is a dispatcher: every legacy `when` arm has to reach the
 * section that renders its fields. The routing gate only proves which arm the
 * card *would* take, so these tests open the card for each family and assert
 * the section's own control is on screen — an arm that points at nothing, or a
 * section whose fields stopped composing, fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h900dp")
class LegacyTriggerCardRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun string(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)

    /** Text can also appear in the card header summary, so take the first match. */
    private fun assertText(resId: Int, vararg formatArgs: Any) {
        composeRule.onAllNodesWithText(string(resId, *formatArgs)).onFirst().assertExists()
    }

    private fun render(
        type: TriggerType,
        config: Map<String, String> = emptyMap(),
        onConfigChange: (TriggerDraft) -> Unit = {},
        onPickApp: () -> Unit = {},
        onUseCurrentLocation: () -> Unit = {},
        onRequestPermission: (Array<String>) -> Unit = {},
    ) {
        composeRule.setContent {
            TriggerEditorCard(
                draft = TriggerDraft(type, config),
                index = 0,
                total = 1,
                onConfigChange = onConfigChange,
                onRemove = {},
                expanded = true,
                onExpandedChange = {},
                onPickApp = onPickApp,
                onUseCurrentLocation = onUseCurrentLocation,
                onRequestPermission = onRequestPermission,
                onExplainSpecial = {},
            )
        }
    }

    @Test
    fun timeTriggerOpensTheScheduleSection() {
        render(TriggerType.TIME)

        assertText(R.string.time_single)
        assertText(R.string.repeat_label)
        assertText(R.string.time_zone_section)
    }

    @Test
    fun batteryTriggerOpensTheLevelAndChargingSection() {
        render(TriggerType.BATTERY)

        assertText(R.string.battery_level_section)
        assertText(R.string.charging_state_section)
    }

    @Test
    fun applicationTriggerSummarisesTheConfiguredPackages() {
        render(
            TriggerType.APPLICATION,
            config = mapOf("packages" to "com.example.one, com.example.two"),
        )

        assertText(R.string.trigger_app)
        assertText(R.string.selected_apps_count, 2)
        assertText(R.string.choose_apps)
    }

    @Test
    fun applicationTriggerWithoutPackagesAsksForThem() {
        render(TriggerType.APPLICATION)

        assertText(R.string.no_apps_selected)
    }

    @Test
    fun locationTriggerOffersCurrentAndPickedCoordinates() {
        render(TriggerType.LOCATION)

        assertText(R.string.use_current_location)
        assertText(R.string.pick_on_map)
        assertText(R.string.location_not_set)
    }

    @Test
    fun locationTriggerShowsTheSelectedRadiusSummary() {
        render(
            TriggerType.LOCATION,
            config = mapOf("lat" to "24.7136", "lng" to "46.6753", "radius" to "250", "source" to "selected"),
        )

        assertText(R.string.location_coordinates, "24.7136", "46.6753")
        assertText(R.string.map_radius_summary, 250)
    }

    @Test
    fun locationTriggerSliderWritesTheRadius() {
        var latest: TriggerDraft? = null
        render(TriggerType.LOCATION, onConfigChange = { latest = it })

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(300f) }
        composeRule.waitForIdle()

        assertEquals("300", latest?.config?.get("radius"))
    }

    @Test
    fun locationTriggerAsksForBothLocationPermissions() {
        var requested: Array<String>? = null
        render(TriggerType.LOCATION, onRequestPermission = { requested = it })

        composeRule.onAllNodesWithText(string(R.string.grant)).onFirst().performClick()

        assertEquals(
            listOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            requested?.toList()
        )
    }

    @Test
    fun appInstalledTriggerOpensTheInstallEvents() {
        render(TriggerType.APP_INSTALLED)

        assertText(R.string.app_installed)
        assertText(R.string.choose_app)
    }

    @Test
    fun volumeTriggerOpensTheStreamAndDirectionSection() {
        render(TriggerType.VOLUME_CHANGED)

        assertText(R.string.volume_stream)
        assertText(R.string.volume_direction)
    }

    @Test
    fun brightnessTriggerOpensItsDirectionAndThreshold() {
        render(TriggerType.BRIGHTNESS_LEVEL)

        assertText(R.string.trigger_brightness_direction)
        assertText(R.string.trigger_brightness_threshold)
    }

    @Test
    fun storageTriggerOpensItsDirectionAndThreshold() {
        render(TriggerType.STORAGE_LOW)

        assertText(R.string.trigger_storage_direction)
        assertText(R.string.trigger_storage_threshold)
    }

    @Test
    fun wifiSignalTriggerOpensTheThresholdChips() {
        render(TriggerType.WIFI_SIGNAL_STRENGTH)

        assertText(R.string.trigger_signal_threshold)
    }

    @Test
    fun cellSignalTriggerOpensTheThresholdChips() {
        render(TriggerType.CELL_SIGNAL_STRENGTH)

        assertText(R.string.trigger_signal_threshold)
    }

    @Test
    fun batteryTemperatureTriggerOpensItsThreshold() {
        render(TriggerType.BATTERY_TEMPERATURE)

        assertText(R.string.trigger_temperature_threshold)
    }

    @Test
    fun clipboardTriggerOpensTheContentFilter() {
        render(TriggerType.CLIPBOARD_CHANGED)

        assertText(R.string.trigger_desc_clipboard)
    }

    @Test
    fun screenTimeoutTriggerOpensItsSecondsField() {
        render(TriggerType.SCREEN_TIMEOUT_CHANGED)

        assertText(R.string.trigger_desc_screen_timeout)
    }

    @Test
    fun nfcTagTriggerOpensTheTagFilter() {
        render(TriggerType.NFC_TAG_SCANNED)

        assertText(R.string.trigger_desc_nfc_tag)
    }

    @Test
    fun wearEventTriggerOpensTheWatchAndStateSection() {
        render(TriggerType.WEAR_EVENT)

        assertText(R.string.trigger_wear_event_sub)
        assertText(R.string.state)
    }

    @Test
    fun pluginEventTriggerIsReadOnlyInTheCard() {
        render(TriggerType.PLUGIN_EVENT)

        assertText(R.string.plugin_no_edit)
    }
}
