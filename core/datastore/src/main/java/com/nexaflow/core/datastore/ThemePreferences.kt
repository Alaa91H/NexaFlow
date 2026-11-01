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
 * Dock glass preference. `AUTO` blurs on API 31+ unless Battery Saver is on;
 * `ON`/`OFF` are explicit overrides the user can pick in Themes.
 */
enum class GlassEffectsMode(val storedValue: String) {
    AUTO("auto"),
    ON("on"),
    OFF("off");

    companion object {
        fun fromStored(value: String?): GlassEffectsMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String = "ink",
    /**
     * Android 12+ Material You colors from the wallpaper. Off by default:
     * Ink is the product's design language, and Material You is opt-in so the
     * reference palette is what every new install sees (§14.1).
     */
    val dynamicColor: Boolean = false,
    /** Blur preference for the Dock; Auto follows API level + Battery Saver. */
    val glassEffects: GlassEffectsMode = GlassEffectsMode.AUTO
)

class ThemePreferences(private val context: Context) {

    private val dataStore = context.themeDataStore

    val theme: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            mode = ThemeMode.fromStored(preferences[KEY_THEME_MODE]),
            accent = preferences[KEY_ACCENT] ?: "ink",
            dynamicColor = preferences[KEY_DYNAMIC_COLOR] ?: false,
            glassEffects = GlassEffectsMode.fromStored(preferences[KEY_GLASS_EFFECTS])
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

    suspend fun setGlassEffects(mode: GlassEffectsMode) {
        dataStore.edit { it[KEY_GLASS_EFFECTS] = mode.storedValue }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_GLASS_EFFECTS = stringPreferencesKey("glass_effects")
    }
}
