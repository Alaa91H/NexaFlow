package com.nexaflow.feature.themes

import com.nexaflow.core.datastore.GlassEffectsMode
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.datastore.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun storedModesRoundTripAndUnknownValuesFallBackToSystem() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStored(mode.storedValue))
        }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("future-mode"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
    }

    @Test
    fun defaultThemeIsInkWithDynamicColourOptOut() {
        assertEquals(
            ThemeSettings(),
            ThemeSettings(ThemeMode.SYSTEM, "ink", false, GlassEffectsMode.AUTO)
        )
        assertEquals(ThemeMode.SYSTEM, ThemeSettings().mode)
        assertEquals("ink", ThemeSettings().accent)
        assertEquals(false, ThemeSettings().dynamicColor)
        assertEquals(GlassEffectsMode.AUTO, ThemeSettings().glassEffects)
    }

    @Test
    fun glassModesRoundTripAndUnknownValuesFallBackToAuto() {
        GlassEffectsMode.entries.forEach { mode ->
            assertEquals(mode, GlassEffectsMode.fromStored(mode.storedValue))
        }
        assertEquals(GlassEffectsMode.AUTO, GlassEffectsMode.fromStored(null))
        assertEquals(GlassEffectsMode.AUTO, GlassEffectsMode.fromStored("frosted"))
    }
}
