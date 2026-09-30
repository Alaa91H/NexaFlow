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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val Context.aiProviderDataStore by preferencesDataStore(
    name = "nexaflow_ai_provider",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class AiProviderSettings(
    val enabled: Boolean = false,
    val displayName: String = "Local model",
    val baseUrl: String = "",
    val modelId: String = "",
    val local: Boolean = true,
    val routingMode: String = "AUTOMATIC",
    val selectedProviderId: String? = null,
    val allowCloudFallback: Boolean = false
)

/** Provider metadata only. API keys belong in SecureStorage, never DataStore. */
data class AiProviderProfileSettings(
    val id: String,
    val presetId: String?,
    val displayName: String,
    val protocol: String,
    val baseUrl: String,
    val modelId: String,
    val local: Boolean = false,
    val enabled: Boolean = true
)

class AiProviderPreferences internal constructor(
    private val dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) {
    constructor(context: Context) : this(context.aiProviderDataStore)

    val settings: Flow<AiProviderSettings> = dataStore.data.map(::decode)
    val profiles: Flow<List<AiProviderProfileSettings>> = dataStore.data.map(::decodeProfiles)

    suspend fun current(): AiProviderSettings = decode(dataStore.data.first())
    suspend fun currentProfiles(): List<AiProviderProfileSettings> =
        decodeProfiles(dataStore.data.first())

    suspend fun upsertProfile(value: AiProviderProfileSettings) {
        validateProfile(value)
        dataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences)
            require(value.id in profiles.map(AiProviderProfileSettings::id) ||
                profiles.size < MAX_PROFILES) { "Too many AI provider profiles" }
            preferences[KEY_PROFILES] = encodeProfiles(
                profiles.filterNot { it.id == value.id } + value
            )
        }
    }

    suspend fun removeProfile(id: String) {
        dataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences).filterNot { it.id == id }
            preferences[KEY_PROFILES] = encodeProfiles(profiles)
            if (preferences[KEY_SELECTED_PROVIDER_ID] == id) {
                preferences.remove(KEY_SELECTED_PROVIDER_ID)
            }
        }
    }

    /** Migrates the previous single provider once, preserving its legacy ID/key mapping. */
    suspend fun migrateLegacyProfileIfNeeded(): AiProviderProfileSettings? {
        var migrated: AiProviderProfileSettings? = null
        dataStore.edit { preferences ->
            if (preferences[KEY_LEGACY_MIGRATION_COMPLETE] == true) return@edit
            if (decodeProfiles(preferences).isNotEmpty()) {
                preferences[KEY_LEGACY_MIGRATION_COMPLETE] = true
                return@edit
            }
            val legacy = decode(preferences)
            if (legacy.baseUrl.isBlank() && legacy.modelId.isBlank() && !legacy.enabled) {
                preferences[KEY_LEGACY_MIGRATION_COMPLETE] = true
                return@edit
            }
            val candidate = AiProviderProfileSettings(
                id = legacy.selectedProviderId ?: LEGACY_PROFILE_ID,
                presetId = LEGACY_PROFILE_PRESET_ID,
                displayName = legacy.displayName,
                protocol = "OPENAI_CHAT_COMPLETIONS",
                baseUrl = legacy.baseUrl,
                modelId = legacy.modelId,
                local = legacy.local,
                enabled = legacy.enabled
            )
            if (runCatching { validateProfile(candidate) }.isFailure) {
                preferences[KEY_LEGACY_MIGRATION_COMPLETE] = true
                return@edit
            }
            preferences[KEY_PROFILES] = encodeProfiles(listOf(candidate))
            preferences[KEY_LEGACY_MIGRATION_COMPLETE] = true
            migrated = candidate
        }
        return migrated
    }
    suspend fun update(value: AiProviderSettings) {
        require(value.displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(value.baseUrl.length <= MAX_BASE_URL_LENGTH)
        require(value.modelId.length <= MAX_MODEL_ID_LENGTH)
        require(value.routingMode.length <= MAX_ROUTING_MODE_LENGTH)
        require(value.selectedProviderId.orEmpty().length <= MAX_PROVIDER_ID_LENGTH)
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = value.enabled
            preferences[KEY_DISPLAY_NAME] = value.displayName.trim()
            preferences[KEY_BASE_URL] = value.baseUrl.trim()
            preferences[KEY_MODEL_ID] = value.modelId.trim()
            preferences[KEY_LOCAL] = value.local
            preferences[KEY_ROUTING_MODE] = value.routingMode
            value.selectedProviderId?.takeIf(String::isNotBlank)?.let {
                preferences[KEY_SELECTED_PROVIDER_ID] = it
            } ?: preferences.remove(KEY_SELECTED_PROVIDER_ID)
            preferences[KEY_ALLOW_CLOUD_FALLBACK] = value.allowCloudFallback
        }
    }

    private fun decode(
        preferences: androidx.datastore.preferences.core.Preferences
    ) = AiProviderSettings(
        enabled = preferences[KEY_ENABLED] ?: false,
        displayName = preferences[KEY_DISPLAY_NAME] ?: "Local model",
        baseUrl = preferences[KEY_BASE_URL].orEmpty(),
        modelId = preferences[KEY_MODEL_ID].orEmpty(),
        local = preferences[KEY_LOCAL] ?: true,
        routingMode = preferences[KEY_ROUTING_MODE] ?: "AUTOMATIC",
        selectedProviderId = preferences[KEY_SELECTED_PROVIDER_ID],
        allowCloudFallback = preferences[KEY_ALLOW_CLOUD_FALLBACK] ?: false
    )

    private fun decodeProfiles(
        preferences: androidx.datastore.preferences.core.Preferences
    ): List<AiProviderProfileSettings> = runCatching {
        val root = Json.parseToJsonElement(preferences[KEY_PROFILES].orEmpty())
        val values = when (root) {
            is JsonArray -> root // Accept early unversioned profile data.
            is JsonObject -> {
                require(root["version"]?.jsonPrimitive?.content == PROFILE_SCHEMA_VERSION.toString())
                root["profiles"]?.jsonArray ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }
        values
            .mapNotNull(::decodeProfile)
            .distinctBy(AiProviderProfileSettings::id)
            .take(MAX_PROFILES)
    }.getOrDefault(emptyList())

    private fun decodeProfile(element: kotlinx.serialization.json.JsonElement): AiProviderProfileSettings? {
        val value = element as? JsonObject ?: return null
        fun string(key: String) = value[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = string("id")
        val displayName = string("displayName")
        val protocol = string("protocol")
        val baseUrl = string("baseUrl")
        if (id.isBlank() || displayName.isBlank() || protocol.isBlank() || baseUrl.isBlank()) {
            return null
        }
        return AiProviderProfileSettings(
            id = id,
            presetId = value["presetId"]?.jsonPrimitive?.contentOrNull,
            displayName = displayName,
            protocol = protocol,
            baseUrl = baseUrl,
            modelId = string("modelId"),
            local = value["local"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            enabled = value["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        ).takeIf { runCatching { validateProfile(it) }.isSuccess }
    }

    private fun encodeProfiles(values: List<AiProviderProfileSettings>): String =
        buildJsonObject {
            put("version", PROFILE_SCHEMA_VERSION)
            put("profiles", buildJsonArray {
            values.take(MAX_PROFILES).forEach { value ->
                add(buildJsonObject {
                    put("id", value.id)
                    if (value.presetId == null) put("presetId", JsonNull)
                    else put("presetId", value.presetId)
                    put("displayName", value.displayName)
                    put("protocol", value.protocol)
                    put("baseUrl", value.baseUrl)
                    put("modelId", value.modelId)
                    put("local", value.local)
                    put("enabled", value.enabled)
                })
            }
            })
        }.toString()

    private fun validateProfile(value: AiProviderProfileSettings) {
        require(value.id.length in 1..MAX_PROVIDER_ID_LENGTH)
        require(value.presetId.orEmpty().length <= MAX_PROVIDER_ID_LENGTH)
        require(value.displayName.isNotBlank() && value.displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(value.protocol.length in 1..MAX_ROUTING_MODE_LENGTH)
        require(value.baseUrl.length in 8..MAX_BASE_URL_LENGTH)
        require(value.modelId.length <= MAX_MODEL_ID_LENGTH)
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 128
        const val MAX_BASE_URL_LENGTH = 2048
        const val MAX_MODEL_ID_LENGTH = 256
        const val MAX_ROUTING_MODE_LENGTH = 64
        const val MAX_PROVIDER_ID_LENGTH = 128
        const val MAX_PROFILES = 16
        const val PROFILE_SCHEMA_VERSION = 1
        const val LEGACY_PROFILE_ID = "openai_compatible"
        const val LEGACY_PROFILE_PRESET_ID = "legacy_migration"

        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL_ID = stringPreferencesKey("model_id")
        private val KEY_LOCAL = booleanPreferencesKey("local")
        private val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val KEY_SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
        private val KEY_ALLOW_CLOUD_FALLBACK =
            booleanPreferencesKey("allow_cloud_fallback")
        private val KEY_PROFILES = stringPreferencesKey("profiles_json")
        private val KEY_LEGACY_MIGRATION_COMPLETE =
            booleanPreferencesKey("legacy_profile_migration_complete")
    }
}
