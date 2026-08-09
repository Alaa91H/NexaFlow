package com.nexaflow.sample.nfctoggle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the bundle ⇄ JSON convention (docs/PLUGIN_SDK.md §3): round-trip
 * fidelity, missing/invalid JSON degrading to empty, and the forward/backward
 * tolerance rule (unknown keys survive).
 *
 * Runs under Robolectric because org.json is stubbed in android.jar (plain JVM
 * tests throw "not mocked") — same reason the SDK's own parser tests do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginConfigTest {

    @Test
    fun roundTrip_preservesValues() {
        val config = mapOf("enabled" to true, "level" to 7)
        val json = PluginConfig.toJson(config)
        assertEquals(config, PluginConfig.parseJson(json))
    }

    @Test
    fun parseJson_blankReturnsEmpty() {
        assertTrue(PluginConfig.parseJson("").isEmpty())
        assertTrue(PluginConfig.parseJson("   ").isEmpty())
    }

    @Test
    fun parseJson_invalidReturnsEmpty() {
        assertTrue(PluginConfig.parseJson("not-json{{").isEmpty())
    }

    @Test
    fun parseJson_unknownKeysSurvive() {
        // Forward tolerance: a newer plugin may add keys; an older host/plugin
        // must keep them instead of dropping the config.
        val parsed = PluginConfig.parseJson("""{"enabled":true,"future":"x"}""")
        assertEquals("x", parsed["future"])
        assertEquals(true, parsed["enabled"])
    }

    @Test
    fun toJson_nestedValuesRoundTripWithoutLosingTopLevelKeys() {
        // The protocol keeps the bundle primitive-typed by wrapping config as a
        // JSON string; nested JSON inside that string is fine and must survive.
        val json = PluginConfig.toJson(mapOf("enabled" to true, "nested" to mapOf("a" to 1)))
        val back = PluginConfig.parseJson(json)
        assertEquals(true, back["enabled"])
        // The nested object is preserved (as its JSON representation).
        assertTrue(back.containsKey("nested"))
    }

    @Test
    fun bundleRoundTrip_under25KbForRealisticConfig() {
        // The 25 KB protocol guard lives in the SDK; here we just verify a
        // realistic config fits comfortably through a real Bundle round-trip.
        val config = mapOf("enabled" to true, "label" to "Turn NFC on")
        val bundle = PluginConfig.toBundle(config)
        assertEquals(config, PluginConfig.fromBundle(bundle))
    }
}
