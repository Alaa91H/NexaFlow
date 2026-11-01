package com.nexaflow.feature.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schema renderer is only as good as its vocabulary: a storage key printed
 * as a label, or an enum token left upper-case, reads as a bug on every screen
 * that shares the renderer. These tests pin the mapping and the unit maths.
 */
class SchemaFieldPresentationTest {

    private fun text(raw: String, fieldId: String, target: String? = null): String =
        schemaValueText(
            raw = raw,
            fieldId = fieldId,
            schemaTarget = target,
            percentPattern = "%d%%",
            celsiusPattern = "%s°C",
            megabytePattern = "%s MB",
            gigabytePattern = "%s GB",
            secondsPattern = "%d s",
            minutesPattern = "%d min",
            barsPattern = "%d/4",
        )

    @Test
    fun knownTokensResolveToTranslatedResources() {
        assertEquals(R.string.schema_token_normal, schemaTokenLabel("NORMAL"))
        assertEquals(R.string.state_connected, schemaTokenLabel("CONNECTED"))
        assertEquals(R.string.charger_usb, schemaTokenLabel("USB"))
        assertEquals(R.string.volume_stream_music, schemaTokenLabel("MUSIC"))
        assertEquals(R.string.schema_token_posted, schemaTokenLabel("POSTED"))
    }

    @Test
    fun unknownTokensAreHumanisedInsteadOfShownAsConstants() {
        assertNull(schemaTokenLabel("FIRST_DAY"))
        assertEquals("First Day", humanizeSchemaToken("FIRST_DAY"))
        assertEquals("Some Value", humanizeSchemaToken("SOME_VALUE"))
    }

    @Test
    fun brightnessIsShownAsAPercentageOfTheRomScale() {
        assertEquals("100%", text("255", "threshold", "core.display.brightness"))
        assertEquals("50%", text("128", "threshold", "core.display.brightness"))
    }

    @Test
    fun storageSwitchesToGigabytesAboveOneThousandTwentyFour() {
        assertEquals("512 MB", text("512", "threshold", "core.storage.low"))
        assertEquals("2 GB", text("2048", "threshold", "core.storage.low"))
    }

    @Test
    fun temperatureKeepsOneDecimal() {
        assertEquals("45.5°C", text("45.5", "threshold", "core.battery.temperature"))
        assertEquals("40°C", text("40", "threshold", "core.battery.temperature"))
    }

    @Test
    fun signalThresholdUsesBars() {
        assertEquals("3/4", text("3", "threshold", "core.connectivity.wifi.signal"))
    }

    @Test
    fun screenTimeoutFormatUsesMinutesWhenWhole() {
        assertEquals("1 min", formatScreenTimeout(60, "%d min", "%d s"))
        assertEquals("15 s", formatScreenTimeout(15, "%d min", "%d s"))
    }

    @Test
    fun sliderBoundsFollowTheDeclaredSchemaRange() {
        assertEquals(0f..255f, schemaSliderBounds("threshold", null, 0, 255))
        assertEquals(0f..100f, schemaSliderBounds("threshold", "core.battery.level", null, null))
        assertEquals(0f..4f, schemaSliderBounds("threshold", "core.wifi.signal", null, null))
    }

    @Test
    fun legacySingleStreamVolumeIsReadAsThatStream() {
        val settings = volumeStreamSettings(mapOf("stream" to "RING", "threshold" to "20"))
        val enabled = settings.filter { it.enabled }

        assertEquals(volumeStreams, settings.map { it.stream })
        assertEquals(listOf("RING"), enabled.map { it.stream })
        assertEquals(20, enabled.single().threshold)
    }

    @Test
    fun perStreamLevelsAreWrittenAndMirroredOnTheLegacyKeys() {
        val settings = listOf(
            VolumeStreamSetting("MUSIC", enabled = true, threshold = 60, direction = "ABOVE"),
            VolumeStreamSetting("RING", enabled = true, threshold = 20, direction = "BELOW"),
            VolumeStreamSetting("ALARM", enabled = false, threshold = 50, direction = "ABOVE"),
            VolumeStreamSetting("NOTIFICATION", enabled = false, threshold = 50, direction = "ABOVE"),
        )
        val config = emptyMap<String, String>().withVolumeStreamSettings(settings)

        assertEquals("MUSIC,RING", config["streams"])
        assertEquals("60", config["threshold_MUSIC"])
        assertEquals("BELOW", config["direction_RING"])
        // The engine and older readers still see the first enabled stream.
        assertEquals("MUSIC", config["stream"])
        assertEquals("60", config["threshold"])
        assertEquals("ABOVE", config["direction"])
    }

    @Test
    fun notificationKindsMapToPlatformCategoriesAndBack() {
        val config = emptyMap<String, String>().withNotificationKinds(setOf("MESSAGES", "CALLS"))

        assertEquals("msg,email,call,missed_call,incoming_call", config["categories"])
        assertEquals(
            setOf("MESSAGES", "CALLS"),
            notificationKindsFor(notificationKindCategories(config)),
        )
    }

    @Test
    fun clearingEveryKindRemovesTheFilter() {
        val config = mapOf("categories" to "msg")
        assertTrue("categories" !in config.withNotificationKinds(emptySet()))
    }
}
