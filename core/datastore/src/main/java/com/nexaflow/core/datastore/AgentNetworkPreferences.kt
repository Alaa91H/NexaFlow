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

private val Context.agentNetworkDataStore by preferencesDataStore(
    name = "nexaflow_agent_network",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class AgentNetworkSettings(
    val lanAccessEnabled: Boolean = false
)

class AgentNetworkPreferences internal constructor(
    private val dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) {
    constructor(context: Context) : this(context.agentNetworkDataStore)

    val settings: Flow<AgentNetworkSettings> = dataStore.data.map(::decode)

    suspend fun current(): AgentNetworkSettings = decode(dataStore.data.first())

    suspend fun setLanAccessEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_LAN_ACCESS_ENABLED] = enabled
        }
    }

    private fun decode(
        preferences: androidx.datastore.preferences.core.Preferences
    ) = AgentNetworkSettings(
        lanAccessEnabled = preferences[KEY_LAN_ACCESS_ENABLED] ?: false
    )

    private companion object {
        val KEY_LAN_ACCESS_ENABLED = booleanPreferencesKey("lan_access_enabled")
    }
}
