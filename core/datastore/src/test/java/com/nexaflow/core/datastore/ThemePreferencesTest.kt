package com.nexaflow.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The appearance settings: Ink is the default scheme, Material You is opt-in
 * and the Dock's glass mode starts on Auto.
 *
 * A wrong default here changes how every install looks, and a stored value that
 * no longer parses must fall back instead of throwing, so both the defaults and
 * every round trip are pinned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemePreferencesTest {

    private lateinit var preferences: ThemePreferences

    @Before
    fun setUp() {
        preferences = ThemePreferences(freshContext())
        // The DataStore delegate is a single store per file for the whole
        // process, so every test starts from the shipped defaults instead of
        // inheriting the previous test's writes.
        runBlocking {
            preferences.setThemeMode(ThemeMode.SYSTEM)
            preferences.setAccent("ink")
            preferences.setDynamicColor(false)
            preferences.setGlassEffects(GlassEffectsMode.AUTO)
        }
    }

    /**
     * The DataStore delegate caches one store per application context, so tests
     * that share the process context would also share their stored values. Each
     * test gets its own context whose files directory is unique.
     */
    private fun freshContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val dir = kotlin.io.path.createTempDirectory("theme-preferences").toFile()
        return object : android.content.ContextWrapper(base) {
            override fun getFilesDir(): java.io.File = dir

            override fun getApplicationContext(): Context = this
        }
    }

    @Test
    fun defaultsAreTheInkSchemeWithoutDynamicColourOrForcedGlass() = runBlocking {
        val settings = preferences.theme.first()

        assertEquals(ThemeMode.SYSTEM, settings.mode)
        assertEquals("ink", settings.accent)
        assertFalse(settings.dynamicColor)
        assertEquals(GlassEffectsMode.AUTO, settings.glassEffects)
    }

    @Test
    fun everySettingRoundTrips() = runBlocking {
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setAccent("violet")
        preferences.setDynamicColor(true)
        preferences.setGlassEffects(GlassEffectsMode.OFF)

        val settings = preferences.theme.first()

        assertEquals(ThemeMode.DARK, settings.mode)
        assertEquals("violet", settings.accent)
        assertTrue(settings.dynamicColor)
        assertEquals(GlassEffectsMode.OFF, settings.glassEffects)
    }

    @Test
    fun glassModeCanBeForcedOnAndBackToAuto() = runBlocking {
        preferences.setGlassEffects(GlassEffectsMode.ON)
        assertEquals(GlassEffectsMode.ON, preferences.theme.first().glassEffects)

        preferences.setGlassEffects(GlassEffectsMode.AUTO)
        assertEquals(GlassEffectsMode.AUTO, preferences.theme.first().glassEffects)
    }

    @Test
    fun lightModeIsStoredAsItsOwnValue() = runBlocking {
        preferences.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, preferences.theme.first().mode)
    }

    @Test
    fun unknownStoredValuesFallBackInsteadOfThrowing() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("amoled"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("dark"))
        assertEquals("dark", ThemeMode.DARK.storedValue)

        assertEquals(GlassEffectsMode.AUTO, GlassEffectsMode.fromStored(null))
        assertEquals(GlassEffectsMode.AUTO, GlassEffectsMode.fromStored("sometimes"))
        assertEquals(GlassEffectsMode.OFF, GlassEffectsMode.fromStored("off"))
        assertEquals("on", GlassEffectsMode.ON.storedValue)
    }
}
