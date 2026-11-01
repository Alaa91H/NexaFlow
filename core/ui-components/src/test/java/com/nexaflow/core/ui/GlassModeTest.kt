package com.nexaflow.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Dock material must never depend on anything but these three inputs, so
 * the fallback is provable without a device (§10).
 */
class GlassModeTest {

    @Test
    fun autoBlursOnApi31AndAbove() {
        assertEquals(GlassMode.Blur, resolveGlassMode(31, batterySaver = false, GlassPreference.AUTO))
        assertEquals(GlassMode.Blur, resolveGlassMode(37, batterySaver = false, GlassPreference.AUTO))
    }

    @Test
    fun autoIsSolidBelow31() {
        assertEquals(GlassMode.Solid, resolveGlassMode(26, false, GlassPreference.AUTO))
        assertEquals(GlassMode.Solid, resolveGlassMode(30, false, GlassPreference.AUTO))
    }

    @Test
    fun batterySaverForcesSolidEvenOnNewApis() {
        assertEquals(GlassMode.Solid, resolveGlassMode(37, batterySaver = true, GlassPreference.AUTO))
    }

    @Test
    fun explicitOffIsAlwaysSolid() {
        assertEquals(GlassMode.Solid, resolveGlassMode(37, false, GlassPreference.OFF))
    }

    @Test
    fun explicitOnStillFallsBackBelow31() {
        assertEquals(GlassMode.Solid, resolveGlassMode(29, false, GlassPreference.ON))
        assertEquals(GlassMode.Blur, resolveGlassMode(31, batterySaver = true, GlassPreference.ON))
    }
}
