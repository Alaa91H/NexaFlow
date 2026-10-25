package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(
    name = "nexaflow_theme",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode {
            return entries.firstOrNull { it.storedValue == value } ?: SYSTEM
        }
    }
}

/**
 * The stored appearance.
 *
 * The first three fields are the original appearance model — a mode, an
 * accent and the Material You switch. The rest are the NexaFlow One traits:
 * they compose with the first three rather than replacing them, so a
 * monochrome screen still has a mode and a monochrome screen in the light
 * theme still has an accent.
 *
 * The accent default moved from the old Google Blue seed to `indigo` with
 * the new brand. An install that already chose an accent keeps it; the
 * stored value is read back, not reset, so upgrading never moves the
 * ground under a user who picked a color.
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String = "indigo",
    /**
     * Android 12+ Material You colors from the wallpaper. Off by default —
     * a wallpaper-derived palette replaces the app's own identity, which is
     * the opposite of what the design system is for. The user can still
     * turn it on in Appearance.
     */
    val dynamicColor: Boolean = false,
    /** Flatten every accent into the neutral palette. */
    val monochrome: Boolean = false,
    /** Let items keep their own colors instead of taking the theme accent. */
    val colorAccents: Boolean = true,
    /** Draw the hairline edge around cards and panels. */
    val outlines: Boolean = true,
    /** Let the floating bars show the content scrolling under them. */
    val blur: Boolean = true,
    /** Draw the soft shapes behind the content. */
    val ambientBackground: Boolean = true,
    /** `comfortable` or `compact`. */
    val density: String = "comfortable",
    /** Which ambient field to draw: `ambient`, `ember`, `quiet` or `none`. */
    val background: String = "ambient",
    /** Paint the dark canvas in pure black for OLED panels. */
    val pureBlack: Boolean = false
)

class ThemePreferences(private val context: Context) {

    private val dataStore = context.themeDataStore

    val theme: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            mode = ThemeMode.fromStored(preferences[KEY_THEME_MODE]),
            accent = preferences[KEY_ACCENT] ?: "indigo",
            dynamicColor = preferences[KEY_DYNAMIC_COLOR] ?: false,
            monochrome = preferences[KEY_MONOCHROME] ?: false,
            colorAccents = preferences[KEY_COLOR_ACCENTS] ?: true,
            outlines = preferences[KEY_OUTLINES] ?: true,
            blur = preferences[KEY_BLUR] ?: true,
            ambientBackground = preferences[KEY_AMBIENT] ?: true,
            density = preferences[KEY_DENSITY] ?: "comfortable",
            background = preferences[KEY_BACKGROUND] ?: "ambient",
            pureBlack = preferences[KEY_PURE_BLACK] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storedValue }
    }

    suspend fun setAccent(accent: String) {
        dataStore.edit { it[KEY_ACCENT] = accent }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setMonochrome(enabled: Boolean) {
        dataStore.edit { it[KEY_MONOCHROME] = enabled }
    }

    suspend fun setColorAccents(enabled: Boolean) {
        dataStore.edit { it[KEY_COLOR_ACCENTS] = enabled }
    }

    suspend fun setOutlines(enabled: Boolean) {
        dataStore.edit { it[KEY_OUTLINES] = enabled }
    }

    suspend fun setBlur(enabled: Boolean) {
        dataStore.edit { it[KEY_BLUR] = enabled }
    }

    suspend fun setAmbientBackground(enabled: Boolean) {
        dataStore.edit { it[KEY_AMBIENT] = enabled }
    }

    suspend fun setDensity(density: String) {
        dataStore.edit { it[KEY_DENSITY] = density }
    }

    suspend fun setBackground(background: String) {
        dataStore.edit { it[KEY_BACKGROUND] = background }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[KEY_PURE_BLACK] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_MONOCHROME = booleanPreferencesKey("monochrome")
        val KEY_COLOR_ACCENTS = booleanPreferencesKey("color_accents")
        val KEY_OUTLINES = booleanPreferencesKey("outlines")
        val KEY_BLUR = booleanPreferencesKey("blur")
        val KEY_AMBIENT = booleanPreferencesKey("ambient_background")
        val KEY_DENSITY = stringPreferencesKey("density")
        val KEY_BACKGROUND = stringPreferencesKey("background")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
    }
}
