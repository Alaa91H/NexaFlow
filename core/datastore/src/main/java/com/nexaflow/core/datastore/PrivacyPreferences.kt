package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(
    name = "nexaflow_privacy",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Privacy-related opt-ins. Crash reporting (Sentry) and any future telemetry
 * are OFF by default (privacy-first); the user enables them explicitly in
 * Settings > Privacy, and the data never leaves the device otherwise.
 */
data class PrivacySettings(
    val crashReportingEnabled: Boolean = false
)

class PrivacyPreferences(private val context: Context) {

    private val dataStore = context.privacyDataStore

    val settings: Flow<PrivacySettings> = dataStore.data.map { preferences ->
        PrivacySettings(
            crashReportingEnabled = preferences[KEY_CRASH_REPORTING] ?: false
        )
    }

    suspend fun isCrashReportingEnabled(): Boolean =
        dataStore.data.first()[KEY_CRASH_REPORTING] ?: false

    suspend fun setCrashReportingEnabled(value: Boolean) {
        dataStore.edit { it[KEY_CRASH_REPORTING] = value }
    }

    private companion object {
        val KEY_CRASH_REPORTING = booleanPreferencesKey("crash_reporting_enabled")
    }
}
