package com.nexaflow.feature.builder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapPickerFallbackTest {

    @Test
    fun blankKeyUsesAlternativeMapImmediately() {
        assertTrue(shouldUseOpenStreetMap(apiKey = "", googleMapFailed = false))
    }

    @Test
    fun configuredKeyUsesGoogleMapUntilARealLoadFailureOccurs() {
        assertFalse(shouldUseOpenStreetMap(apiKey = "configured-key", googleMapFailed = false))
    }

    @Test
    fun googleMapLoadFailureUsesAlternativeMapEvenWithConfiguredKey() {
        assertTrue(shouldUseOpenStreetMap(apiKey = "configured-key", googleMapFailed = true))
    }

    @Test
    fun tileProviderFailureDoesNotRepresentRendererFailure() {
        assertFalse(isAlternativeMapRendererFailure("tile_network_unavailable"))
        assertTrue(isAlternativeMapRendererFailure("leaflet_unavailable"))
        assertTrue(isAlternativeMapRendererFailure(null))
    }

    @Test
    fun alternativeMapUsesBundledLeafletInsteadOfRuntimeCdn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = context.readAsset("map_picker.html").decodeToString()
        val stylesheet = context.readAsset("leaflet/leaflet.css").decodeToString()
        val license = context.readAsset("leaflet/LICENSE").decodeToString()

        assertTrue(html.contains("leaflet/leaflet.css"))
        assertTrue(html.contains("leaflet/leaflet.js"))
        assertTrue(html.contains("onMapReady"))
        assertTrue(html.contains("tile_network_unavailable"))
        assertFalse(html.contains("cdn.jsdelivr.net"))
        assertFalse(html.contains("unpkg.com"))
        assertTrue(stylesheet.contains("images/marker-icon.png"))
        assertTrue(license.contains("BSD 2-Clause License"))

        listOf(
            "leaflet/leaflet.js",
            "leaflet/images/marker-icon.png",
            "leaflet/images/marker-icon-2x.png",
            "leaflet/images/marker-shadow.png",
            "leaflet/images/layers.png",
            "leaflet/images/layers-2x.png"
        ).forEach { path ->
            assertTrue("Expected non-empty asset: $path", context.readAsset(path).isNotEmpty())
        }
    }

    private fun Context.readAsset(path: String): ByteArray =
        assets.open(path).use { it.readBytes() }
}
