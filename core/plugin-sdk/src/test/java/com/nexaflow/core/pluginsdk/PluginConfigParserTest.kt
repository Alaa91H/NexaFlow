package com.nexaflow.core.pluginsdk

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginConfigParserTest {

    @Test
    fun `round trip preserves primitive types`() {
        val config = mapOf(
            "enabled" to true,
            "level" to 7,
            "label" to "alarm",
            "ratio" to 0.5,
            "tags" to listOf("a", "b")
        )
        val bundle = PluginConfigParser.toBundle(config)
        val back = PluginConfigParser.fromBundle(bundle)
        assertEquals(config, back)
    }

    @Test
    fun `bundle carries protocol keys`() {
        val bundle = PluginConfigParser.toBundle(mapOf("k" to "v"))
        assertTrue(bundle.containsKey(PluginConfigParser.KEY_CONFIG))
        assertEquals(PluginConfigParser.SDK_VERSION, bundle.getInt(PluginConfigParser.KEY_SDK_VERSION))
    }

    @Test
    fun `missing or invalid bundle yields empty config`() {
        assertEquals(emptyMap<String, Any?>(), PluginConfigParser.fromBundle(null))
        assertEquals(emptyMap<String, Any?>(), PluginConfigParser.fromBundle(Bundle()))
        assertEquals(emptyMap<String, Any?>(), PluginConfigParser.parseJson("not json"))
        assertEquals(emptyMap<String, Any?>(), PluginConfigParser.parseJson(""))
    }

    @Test
    fun `size guard rejects oversized bundles`() {
        val huge = mapOf("payload" to "x".repeat(30_000))
        assertThrows(PluginBundleTooLargeException::class.java) {
            PluginConfigParser.toBundle(huge)
        }
    }

    @Test
    fun `flattenBundle extracts primitive extras`() {
        val bundle = Bundle().apply {
            putString("number", "123")
            putInt("level", 5)
            putBoolean("on", true)
        }
        val flat = PluginConfigParser.flattenBundle(bundle)
        assertEquals("123", flat["number"])
        assertEquals(5, flat["level"])
        assertEquals(true, flat["on"])
    }
}
