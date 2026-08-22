package com.nexaflow.feature.builder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
