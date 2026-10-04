package com.nexaflow.feature.themes

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
    fun defaultThemeUsesSystemModeBlueAccentAndDynamicColors() {
        assertEquals(ThemeSettings(), ThemeSettings(ThemeMode.SYSTEM, "blue", true))
        assertEquals(ThemeMode.SYSTEM, ThemeSettings().mode)
        assertEquals("blue", ThemeSettings().accent)
        assertEquals(true, ThemeSettings().dynamicColor)
    }
}
