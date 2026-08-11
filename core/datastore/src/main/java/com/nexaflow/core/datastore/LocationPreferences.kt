package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.locationDataStore by preferencesDataStore(
    name = "nexaflow_location",
    // A crash mid-write must never block startup reads — reset to defaults.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Global location behaviour. [checkIntervalMinutes] is how often the app
 * re-enables location in the background to verify location-triggered tasks
 * when the user has location switched OFF (0 = manual only).
 */
data class LocationSettings(
    val checkIntervalMinutes: Int = 0
)

class LocationPreferences(private val context: Context) {

    private val dataStore = context.locationDataStore

    val settings: Flow<LocationSettings> = dataStore.data.map { preferences ->
        LocationSettings(
            checkIntervalMinutes = preferences[KEY_CHECK_INTERVAL_MINUTES] ?: 0
        )
    }

    val checkIntervalMinutes: Flow<Int> = settings.map { it.checkIntervalMinutes }

    suspend fun setCheckIntervalMinutes(value: Int) {
        dataStore.edit { it[KEY_CHECK_INTERVAL_MINUTES] = value }
    }

    companion object {
        /** 0 = manual only; otherwise the periodic re-check interval in minutes. */
        const val MANUAL = 0
        val PRESETS = listOf(0, 15, 30, 60, 180, 360)
        private val KEY_CHECK_INTERVAL_MINUTES = intPreferencesKey("check_interval_minutes")
    }
}
