package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.activeExecutionDataStore by preferencesDataStore(
    name = "nexaflow_active_executions",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Durable lifecycle ledger for automations whose main actions actually passed
 * the constraint gate and started. It is intentionally separate from
 * [ActiveTriggerStore]: a trigger can be active while a task is blocked by a
 * constraint, and that must never arm an end behavior.
 */
class ActiveExecutionStore(private val context: Context) {

    private val dataStore = context.activeExecutionDataStore

    /** Records that [automationId] entered the executable task lifecycle. */
    suspend fun markStarted(automationId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_EXECUTIONS] =
                (preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()) + automationId
        }
    }

    /**
     * Atomically removes and returns whether an active lifecycle was present.
     * End behavior is one-shot, so consuming the marker prevents duplicate exit
     * actions if two monitor callbacks arrive around the same state transition.
     */
    suspend fun consumeStarted(automationId: String): Boolean {
        var wasStarted = false
        dataStore.edit { preferences ->
            val active = preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()
            wasStarted = automationId in active
            preferences[KEY_ACTIVE_EXECUTIONS] = active - automationId
        }
        return wasStarted
    }

    /** Removes a lifecycle marker when a task is deleted or deliberately reset. */
    suspend fun clear(automationId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_EXECUTIONS] =
                (preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()) - automationId
        }
    }

    internal suspend fun activeIdsForTest(): Set<String> =
        dataStore.data.first()[KEY_ACTIVE_EXECUTIONS].orEmpty()

    private companion object {
        val KEY_ACTIVE_EXECUTIONS = stringSetPreferencesKey("active_executions")
    }
}
