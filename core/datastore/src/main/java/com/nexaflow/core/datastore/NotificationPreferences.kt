package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "nexaflow_notifications")

/**
 * Per-category control over which notifications NexaFlow is allowed to show.
 * The master [NotificationSettings.enabled] flag turns every category off.
 */
data class NotificationSettings(
    val enabled: Boolean = true,
    val executionEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val monitoringEnabled: Boolean = true
)

class NotificationPreferences(private val context: Context) {

    private val dataStore = context.notificationDataStore

    val settings: Flow<NotificationSettings> = dataStore.data.map { preferences ->
        NotificationSettings(
            enabled = preferences[KEY_ENABLED] ?: true,
            executionEnabled = preferences[KEY_EXECUTION] ?: true,
            remindersEnabled = preferences[KEY_REMINDERS] ?: true,
            monitoringEnabled = preferences[KEY_MONITORING] ?: true
        )
    }

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setExecutionEnabled(value: Boolean) {
        dataStore.edit { it[KEY_EXECUTION] = value }
    }

    suspend fun setRemindersEnabled(value: Boolean) {
        dataStore.edit { it[KEY_REMINDERS] = value }
    }

    suspend fun setMonitoringEnabled(value: Boolean) {
        dataStore.edit { it[KEY_MONITORING] = value }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_EXECUTION = booleanPreferencesKey("execution_notifications")
        val KEY_REMINDERS = booleanPreferencesKey("reminder_notifications")
        val KEY_MONITORING = booleanPreferencesKey("monitoring_notifications")
    }
}
