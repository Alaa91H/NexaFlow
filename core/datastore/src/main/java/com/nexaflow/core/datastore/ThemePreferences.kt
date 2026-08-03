package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "nexaflow_theme")

data class ThemeSettings(
    val darkMode: Boolean = false,
    val accent: String = "blue"
)

class ThemePreferences(private val context: Context) {

    private val dataStore = context.themeDataStore

    val theme: Flow<ThemeSettings> = dataStore.data.map { preferences ->
        ThemeSettings(
            darkMode = preferences[KEY_DARK_MODE] ?: false,
            accent = preferences[KEY_ACCENT] ?: "blue"
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun setAccent(accent: String) {
        dataStore.edit { it[KEY_ACCENT] = accent }
    }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_ACCENT = stringPreferencesKey("accent")
    }
}
