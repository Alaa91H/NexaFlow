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

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String = "blue",
    /**
     * Android 12+ Material You colors from the wallpaper. True by default —
     * the same experience as every Google app; the user can disable it in
     * Settings to use the accent seed schemes instead.
     */
    val dynamicColor: Boolean = true
)

class ThemePreferences(private val context: Context) {

    private val dataStore = context.themeDataStore

    val theme: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            mode = ThemeMode.fromStored(preferences[KEY_THEME_MODE]),
            accent = preferences[KEY_ACCENT] ?: "blue",
            dynamicColor = preferences[KEY_DYNAMIC_COLOR] ?: true
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

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
