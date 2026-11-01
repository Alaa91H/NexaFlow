package com.nexaflow.feature.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared vocabulary of the schema renderer.
 *
 * Every field id the catalog can emit must resolve to a label, and every token
 * an enum can carry must resolve to a translated word — a missing entry is what
 * used to print `FIRST_DAY` or `POSTED` into the builder. The tables below are
 * the contract, so a new field or token that forgets its copy fails here rather
 * than on a user's screen, and every unit keeps the display, bounds and step it
 * promises.
 */
class SchemaFieldVocabularyTest {

    private val labelledFields = listOf(
        "event", "state", "mode", "direction", "threshold", "stream", "streams",
        "contains", "package", "packages", "seconds", "minutes", "beforeMinutes",
        "radius", "zone", "zoneId", "deviceName", "deviceAddress", "from", "category",
        "matchMode", "calendar", "key", "value", "namespace", "operator", "sensor",
        "level", "percent", "percentage", "brightness", "temperature",
    )

    private val tokens = listOf(
        "ON", "OFF", "CONNECTED", "DISCONNECTED", "ABOVE", "BELOW", "NORMAL", "VIBRATE",
        "SILENT", "LOCKED", "UNLOCKED", "PORTRAIT", "LANDSCAPE", "POSTED", "REMOVED",
        "STARTED", "STOPPED", "INSTALLED", "UPDATED", "ANY", "YES", "NO", "MUSIC",
        "RING", "ALARM", "NOTIFICATION", "INCOMING", "OUTGOING", "ENDED", "CONTAINS",
        "EXACT", "SYSTEM", "SECURE", "GLOBAL", "EQUALS", "NOT_EQUALS", "AUTO",
        "AUTOMATIC", "SET", "CLEARED", "SCREEN_ON", "SCREEN_OFF", "CHARGING",
        "NOT_CHARGING", "AC", "USB", "WIRELESS", "WIFI", "MOBILE", "CONTACT", "PRIVATE",
        "UNKNOWN", "EVENT_START", "EVENT_END",
    )

    @Test
    fun everyKnownFieldIdResolvesToALabel() {
        labelledFields.forEach { fieldId ->
            assertNotNull("$fieldId has no label resource", schemaFieldView(fieldId).labelRes)
        }
        assertNull(schemaFieldView("a_field_no_screen_shows").labelRes)
    }

    @Test
    fun fieldUnitsFollowTheFieldTheyDescribe() {
        assertEquals(SchemaValueUnit.SECONDS, schemaFieldView("seconds").unit)
        assertEquals(SchemaValueUnit.MINUTES, schemaFieldView("minutes").unit)
        assertEquals(SchemaValueUnit.MINUTES, schemaFieldView("beforeMinutes").unit)
        assertEquals(SchemaValueUnit.PERCENT, schemaFieldView("percent").unit)
        assertEquals(SchemaValueUnit.PERCENT, schemaFieldView("percentage").unit)
        assertEquals(SchemaValueUnit.BRIGHTNESS, schemaFieldView("brightness").unit)
        assertEquals(SchemaValueUnit.CELSIUS, schemaFieldView("temperature").unit)
        assertEquals(SchemaValueUnit.NONE, schemaFieldView("threshold").unit)
    }

    @Test
    fun everyEnumTokenIsTranslated() {
        tokens.forEach { token ->
            assertNotNull("$token has no label resource", schemaTokenLabel(token))
        }
        // Matching is case-insensitive: the schema stores upper-case tokens, but
        // a saved task may carry a mixed-case value.
        assertEquals(schemaTokenLabel("NORMAL"), schemaTokenLabel("normal"))
        assertNull(schemaTokenLabel("FIRST_DAY"))
    }

    @Test
    fun thresholdUnitsAreInferredFromTheTarget() {
        assertEquals(
            SchemaValueUnit.BRIGHTNESS,
            schemaValueUnit("threshold", "core.display.brightness"),
        )
        assertEquals(
            SchemaValueUnit.MEGABYTES,
            schemaValueUnit("threshold", "core.storage.low"),
        )
        assertEquals(
            SchemaValueUnit.CELSIUS,
            schemaValueUnit("threshold", "core.battery.temperature"),
        )
        assertEquals(
            SchemaValueUnit.PERCENT,
            schemaValueUnit("threshold", "core.media.volume"),
        )
        assertEquals(
            SchemaValueUnit.PERCENT,
            schemaValueUnit("threshold", "core.battery.level"),
        )
        assertEquals(
            SchemaValueUnit.BARS,
            schemaValueUnit("threshold", "core.connectivity.wifi.signal"),
        )
        assertEquals(SchemaValueUnit.NONE, schemaValueUnit("threshold", null))
        assertEquals(SchemaValueUnit.NONE, schemaValueUnit("threshold", "core.timer.after"))
        // Only `threshold` is inferred: another id keeps its declared unit.
        assertEquals(SchemaValueUnit.NONE, schemaValueUnit("radius", "core.battery.level"))
    }

    private fun text(raw: String, fieldId: String, target: String? = null) = schemaValueText(
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
    fun everyUnitFormatsItsValue() {
        assertEquals("40%", text("40", "percent"))
        assertEquals("100%", text("255", "brightness"))
        assertEquals("45.5°C", text("45,5", "temperature"))
        assertEquals("512 MB", text("512", "threshold", "core.storage.low"))
        assertEquals("2.5 GB", text("2560", "threshold", "core.storage.low"))
        assertEquals("90 s", text("90", "seconds"))
        assertEquals("4 min", text("4", "minutes"))
        assertEquals("3/4", text("3", "threshold", "core.connectivity.wifi.signal"))
        assertEquals("42", text("42", "radius"))
    }

    @Test
    fun unparsableValuesAreShownVerbatimInsteadOfBlank() {
        assertEquals("", text("", "percent"))
        assertEquals("abc", text("abc", "percent"))
    }

    @Test
    fun sliderBoundsPreferTheDeclaredRange() {
        assertEquals(5f..30f, schemaSliderBounds("threshold", null, 5, 30))
        // A one-sided or inverted declaration falls back to the unit default.
        assertEquals(0f..100f, schemaSliderBounds("threshold", null, 5, null))
        assertEquals(0f..100f, schemaSliderBounds("threshold", null, 30, 5))
    }

    @Test
    fun sliderBoundsFallBackToTheUnitDefault() {
        assertEquals(0f..255f, schemaSliderBounds("brightness", null, null, null))
        assertEquals(0f..100f, schemaSliderBounds("percent", null, null, null))
        assertEquals(0f..80f, schemaSliderBounds("temperature", null, null, null))
        assertEquals(0f..8192f, schemaSliderBounds("threshold", "core.storage.low", null, null))
        assertEquals(0f..4f, schemaSliderBounds("threshold", "core.wifi.signal", null, null))
        assertEquals(0f..3600f, schemaSliderBounds("seconds", null, null, null))
        assertEquals(0f..180f, schemaSliderBounds("minutes", null, null, null))
        assertEquals(0f..100f, schemaSliderBounds("radius", null, null, null))
    }

    @Test
    fun stepSizesMatchTheUnitTheyStep() {
        assertEquals(5f, schemaStepSize("brightness", null))
        assertEquals(0.5f, schemaStepSize("temperature", null))
        assertEquals(100f, schemaStepSize("threshold", "core.storage.low"))
        assertEquals(1f, schemaStepSize("percent", null))
    }

    @Test
    fun screenTimeoutPresetsCoverTheRomChoices() {
        assertTrue(screenTimeoutPresetsSeconds.containsAll(listOf(15L, 30L, 60L, 600L, 3600L)))
        assertEquals(screenTimeoutPresetsSeconds.sorted(), screenTimeoutPresetsSeconds)
        assertEquals("2 min", formatScreenTimeout(120, "%d min", "%d s"))
        assertEquals("45 s", formatScreenTimeout(45, "%d min", "%d s"))
    }

    @Test
    fun humanisedTokensReadAsPhrases() {
        assertEquals("First Day", humanizeSchemaToken("FIRST_DAY"))
        assertEquals("Some Value", humanizeSchemaToken("SOME_VALUE"))
        assertEquals("Kebab Case", humanizeSchemaToken("kebab-case"))
        assertEquals("", humanizeSchemaToken("__"))
    }
}
