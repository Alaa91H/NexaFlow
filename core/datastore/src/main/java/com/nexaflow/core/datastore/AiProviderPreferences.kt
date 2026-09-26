package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiProviderDataStore by preferencesDataStore(
    name = "nexaflow_ai_provider",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class AiProviderSettings(
    val enabled: Boolean = false,
    val displayName: String = "Local model",
    val baseUrl: String = "",
    val modelId: String = "",
    val local: Boolean = true
)

class AiProviderPreferences internal constructor(
    private val dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) {
    constructor(context: Context) : this(context.aiProviderDataStore)

    val settings: Flow<AiProviderSettings> = dataStore.data.map(::decode)

    suspend fun current(): AiProviderSettings = decode(dataStore.data.first())

    suspend fun update(value: AiProviderSettings) {
        require(value.displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(value.baseUrl.length <= MAX_BASE_URL_LENGTH)
        require(value.modelId.length <= MAX_MODEL_ID_LENGTH)
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = value.enabled
            preferences[KEY_DISPLAY_NAME] = value.displayName.trim()
            preferences[KEY_BASE_URL] = value.baseUrl.trim()
            preferences[KEY_MODEL_ID] = value.modelId.trim()
            preferences[KEY_LOCAL] = value.local
        }
    }

    private fun decode(
        preferences: androidx.datastore.preferences.core.Preferences
    ) = AiProviderSettings(
        enabled = preferences[KEY_ENABLED] ?: false,
        displayName = preferences[KEY_DISPLAY_NAME] ?: "Local model",
        baseUrl = preferences[KEY_BASE_URL].orEmpty(),
        modelId = preferences[KEY_MODEL_ID].orEmpty(),
        local = preferences[KEY_LOCAL] ?: true
    )

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 128
        const val MAX_BASE_URL_LENGTH = 2048
        const val MAX_MODEL_ID_LENGTH = 256

        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL_ID = stringPreferencesKey("model_id")
        private val KEY_LOCAL = booleanPreferencesKey("local")
    }
}
