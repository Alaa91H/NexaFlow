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

private val Context.updateDataStore by preferencesDataStore(
    name = "nexaflow_updates",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** User-selectable, approximate cadence for automatic update checks. */
enum class UpdateCheckFrequency(val storageValue: String, val repeatDays: Long) {
    DAILY("daily", 1),
    WEEKLY("weekly", 7),
    MONTHLY("monthly", 30);

    companion object {
        fun fromStorage(value: String?): UpdateCheckFrequency =
            entries.firstOrNull { it.storageValue == value } ?: MONTHLY
    }
}

/**
 * Persistent update-check configuration. Automatic checks intentionally start
 * disabled: no background network work and no notification occur until the
 * user explicitly enables this setting.
 */
data class UpdateSettings(
    val automaticChecksEnabled: Boolean = false,
    val frequency: UpdateCheckFrequency = UpdateCheckFrequency.MONTHLY
)

class UpdatePreferences(private val context: Context) {

    private val dataStore = context.updateDataStore

    val settings: Flow<UpdateSettings> = dataStore.data.map { preferences ->
        UpdateSettings(
            automaticChecksEnabled = preferences[KEY_AUTOMATIC_CHECKS_ENABLED] ?: false,
            frequency = UpdateCheckFrequency.fromStorage(preferences[KEY_FREQUENCY])
        )
    }

    suspend fun setAutomaticChecksEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTOMATIC_CHECKS_ENABLED] = enabled
        }
    }

    suspend fun setFrequency(frequency: UpdateCheckFrequency) {
        dataStore.edit { preferences ->
            preferences[KEY_FREQUENCY] = frequency.storageValue
        }
    }

    /**
     * Atomically reserves a notification for [canonicalVersion]. Repeated work
     * runs, process restarts, and scheduler replacement cannot notify twice for
     * the same release. Disabled settings never reserve or notify.
     */
    suspend fun claimNotification(canonicalVersion: String): Boolean {
        if (canonicalVersion.isBlank()) return false
        var claimed = false
        dataStore.edit { preferences ->
            val enabled = preferences[KEY_AUTOMATIC_CHECKS_ENABLED] ?: false
            if (enabled && preferences[KEY_LAST_NOTIFIED_VERSION] != canonicalVersion) {
                preferences[KEY_LAST_NOTIFIED_VERSION] = canonicalVersion
                claimed = true
            }
        }
        return claimed
    }

    companion object {
        private val KEY_AUTOMATIC_CHECKS_ENABLED = booleanPreferencesKey("automatic_update_checks_enabled")
        private val KEY_FREQUENCY = stringPreferencesKey("automatic_update_check_frequency")
        private val KEY_LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_update_version")
    }
}
