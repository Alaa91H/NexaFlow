package com.nexaflow.feature.builder

import androidx.compose.ui.graphics.Color
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionDraftTest {

    @Test
    fun duplicateActionTypes_keepIndependentConfigsAndEndBehavior() {
        val option = actionOptions.first { it.actionType == ActionType.SYSTEM_VOLUME }
        val first = ActionDraft(
            id = "first-volume",
            option = option,
            config = mapOf("value" to "20"),
            endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("value" to "10"))
        )
        val second = ActionDraft(
            id = "second-volume",
            option = option,
            config = mapOf("value" to "80")
        )

        val actions = listOf(first, second).map { it.toAction() }

        assertEquals(2, actions.size)
        assertEquals(ActionType.SYSTEM_VOLUME, actions[0].type)
        assertEquals(ActionType.SYSTEM_VOLUME, actions[1].type)
        assertEquals("20", actions[0].config["value"])
        assertEquals("80", actions[1].config["value"])
        assertEquals(EndMode.SET_VALUE, actions[0].endBehavior?.mode)
        assertEquals(null, actions[1].endBehavior)
    }

    @Test
    fun googlePlayUpdateDraft_startsWithConservativePersistedConfig() {
        val option = actionOptions.first { it.actionType == ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS }

        val action = ActionDraft(id = "play-update", option = option).toAction()

        assertEquals("true", action.config["includeGoogleApps"])
        assertEquals("false", action.config["includeUserApps"])
        assertEquals("true", action.config["wifiOnly"])
        assertEquals("true", action.config["chargingOnly"])
        assertEquals("true", action.config["requireSilentInstall"])
        assertEquals("true", action.config["dryRun"])
        assertEquals("1", action.config["maxConcurrentDownloads"])
        assertEquals("0", action.config["retryCount"])
    }

    @Test
    fun specializedActions_startFromCatalogDefaults() {
        val http = defaultActionConfig(ActionType.SYSTEM_HTTP_REQUEST)
        assertEquals("GET", http["method"])
        assertEquals("10000", http["timeoutMs"])
        assertEquals("0", http["retryAttempts"])
        assertEquals("false", http["allowPrivateNetwork"])
        assertEquals(null, http["timeoutSeconds"])

        val density = defaultActionConfig(ActionType.SYSTEM_DISPLAY_DENSITY)
        assertEquals("440", density["dpi"])

        val batterySaver = defaultActionConfig(ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD)
        assertEquals("20", batterySaver["percent"])

        val bluetooth = defaultActionConfig(ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY)
        assertEquals("300", bluetooth["timeoutSeconds"])

        val romStatus = defaultActionConfig(ActionType.ROM_STATUS_BAR)
        assertEquals("0", romStatus["battery_percent"])
        assertEquals("0", romStatus["clock_seconds"])

        assertEquals("GLOBAL", defaultActionConfig(ActionType.SYSTEM_SET_SETTING)["namespace"])
        assertEquals("SECURE", defaultActionConfig(ActionType.ROM_CUSTOM_SETTING)["namespace"])
        assertEquals("10", defaultActionConfig(ActionType.SYSTEM_SCREENSAVER_TIMEOUT)["minutes"])
        assertEquals("WIFI", defaultActionConfig(ActionType.SYSTEM_OPEN_SETTINGS)["page"])
        assertEquals("GMT", defaultActionConfig(ActionType.SYSTEM_SET_TIMEZONE)["zone"])
        assertEquals(
            "0,200,100,200",
            defaultActionConfig(ActionType.SYSTEM_VIBRATE_PATTERN)["pattern"]
        )
        assertEquals("NexaFlow timer", defaultActionConfig(ActionType.SYSTEM_SET_TIMER)["message"])
        assertEquals("NexaFlow", defaultActionConfig(ActionType.SYSTEM_SEND_NOTIFICATION)["title"])
        assertEquals(
            "Automation executed",
            defaultActionConfig(ActionType.SYSTEM_SEND_NOTIFICATION)["text"]
        )
    }

    @Test
    fun cardAccent_cycleDistinguishesAdjacentCards_andRepeatsOnlyAfterPalette() {
        assertNotEquals(builderCardAccent(0), builderCardAccent(1))
        assertNotEquals(builderCardAccent(1), builderCardAccent(2))
        assertEquals(
            builderCardAccent(0),
            builderCardAccent(builderCardAccentPalette.size)
        )
    }

    @Test
    fun cardContainer_cycleUsesGreyAndDarkGrey_withLightReadableContent() {
        assertEquals(Color(0xFF2F3336), builderCardContainerColor(0))
        assertEquals(Color(0xFF1E2022), builderCardContainerColor(1))
        assertNotEquals(builderCardContainerColor(0), builderCardContainerColor(1))
        assertEquals(builderCardContainerColor(0), builderCardContainerColor(2))
        assertEquals(Color(0xFFF5F7F8), builderCardContentColor)
    }
}
