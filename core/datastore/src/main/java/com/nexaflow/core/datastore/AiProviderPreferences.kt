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

enum class AiStoredDialect {
    OPENAI_CHAT_COMPLETIONS,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT
}

enum class AiStoredAuthScheme {
    BEARER_TOKEN,
    X_API_KEY,
    GOOGLE_API_KEY,
    CUSTOM_HEADER,
    NONE
}

data class AiConnectionSettings(
    val id: String,
    val presetId: String?,
    val providerDefinitionId: String,
    val displayName: String,
    val dialect: AiStoredDialect?,
    val baseUrl: String,
    val authScheme: AiStoredAuthScheme,
    val credentialRef: String?,
    val local: Boolean,
    val enabled: Boolean
)

data class AiModelSelectionSettings(
    val connectionId: String,
    val modelId: String,
    val reasoningEffort: String = "medium"
)

data class AiConnectionsState(
    val version: Int = PROFILE_SCHEMA_VERSION,
    val connections: List<AiConnectionSettings> = emptyList(),
    val models: List<AiModelSelectionSettings> = emptyList()
) {
    companion object {
        const val PROFILE_SCHEMA_VERSION = 2
    }
}

/**
 * Compatibility projection used while settings/runtime callers migrate to
 * [AiConnectionsState]. Secrets remain references only.
 */
data class AiProviderProfileSettings(
    val id: String,
    val presetId: String?,
    val displayName: String,
    val protocol: String,
    val baseUrl: String,
    val modelId: String,
    val local: Boolean = false,
    val enabled: Boolean = true,
    val reasoningEffort: String = "medium",
    val providerDefinitionId: String? = null,
    val dialect: String? = null,
    val authScheme: String? = null,
    val credentialRef: String? = null
)

class AiProviderPreferences internal constructor(
    private val dataStore:
        androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) {
    constructor(context: Context) : this(context.aiProviderDataStore)

    val settings: Flow<AiProviderSettings> = dataStore.data.map(::decode)
    val connectionsState: Flow<AiConnectionsState> =
        dataStore.data.map(::decodeConnectionsState)
    val profiles: Flow<List<AiProviderProfileSettings>> =
        connectionsState.map(::profilesFromState)

    suspend fun current(): AiProviderSettings = decode(dataStore.data.first())
    suspend fun currentConnectionsState(): AiConnectionsState =
        decodeConnectionsState(dataStore.data.first())
    suspend fun currentProfiles(): List<AiProviderProfileSettings> =
        profilesFromState(currentConnectionsState())

    suspend fun upsertProfile(value: AiProviderProfileSettings) {
        validateProfile(value)
        dataStore.edit { preferences ->
            val profiles = profilesFromState(decodeConnectionsState(preferences))
            require(
                value.id in profiles.map(AiProviderProfileSettings::id) ||
                    profiles.size < MAX_PROFILES
            ) { "Too many AI provider profiles" }
            preferences[KEY_PROFILES] = encodeState(
                stateFromProfiles(profiles.filterNot { it.id == value.id } + value)
            )
        }
    }

    suspend fun removeProfile(id: String) {
        dataStore.edit { preferences ->
            val profiles = profilesFromState(decodeConnectionsState(preferences))
                .filterNot { it.id == id }
            preferences[KEY_PROFILES] = encodeState(stateFromProfiles(profiles))
            if (preferences[KEY_SELECTED_PROVIDER_ID] == id) {
                preferences.remove(KEY_SELECTED_PROVIDER_ID)
            }
        }
    }

    /**
     * Rewrites any valid v1/unversioned profile payload into the typed v2
     * connection/model envelope. Invalid entries are dropped individually.
     */
    suspend fun migrateConnectionsStateIfNeeded(): AiConnectionsState {
        var migrated = AiConnectionsState()
        dataStore.edit { preferences ->
            migrated = decodeConnectionsState(preferences)
            val raw = preferences[KEY_PROFILES].orEmpty()
            if (raw.isNotBlank() && !isVersion2(raw)) {
                preferences[KEY_PROFILES] = encodeState(migrated)
            }
        }
        return migrated
    }

    /** Migrates the previous single provider once, preserving its legacy ID/key mapping. */
    suspend fun migrateLegacyProfileIfNeeded(): AiProviderProfileSettings? {
        var migrated: AiProviderProfileSettings? = null
        dataStore.edit { preferences ->
            if (preferences[KEY_LEGACY_MIGRATION_COMPLETE] == true) return@edit
            val existingProfiles = profilesFromState(decodeConnectionsState(preferences))
            if (existingProfiles.isNotEmpty()) {
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
                enabled = legacy.enabled,
                providerDefinitionId = "openai_compatible",
                dialect = AiStoredDialect.OPENAI_CHAT_COMPLETIONS.name,
                authScheme = if (legacy.local) {
                    AiStoredAuthScheme.NONE.name
                } else {
                    AiStoredAuthScheme.BEARER_TOKEN.name
                },
                credentialRef = defaultCredentialRef(legacy.selectedProviderId ?: LEGACY_PROFILE_ID)
            )
            if (runCatching { validateProfile(candidate) }.isFailure) {
                preferences[KEY_LEGACY_MIGRATION_COMPLETE] = true
                return@edit
            }
            preferences[KEY_PROFILES] = encodeState(stateFromProfiles(listOf(candidate)))
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

    private fun decodeConnectionsState(
        preferences: androidx.datastore.preferences.core.Preferences
    ): AiConnectionsState {
        val raw = preferences[KEY_PROFILES].orEmpty()
        if (raw.isBlank()) return AiConnectionsState()
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            ?: return AiConnectionsState()
        return when (root) {
            is JsonArray -> stateFromProfiles(decodeLegacyProfiles(root))
            is JsonObject -> when (
                root["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ) {
                PROFILE_SCHEMA_VERSION -> decodeVersion2(root)
                1 -> stateFromProfiles(
                    decodeLegacyProfiles(root["profiles"] as? JsonArray ?: JsonArray(emptyList()))
                )
                else -> AiConnectionsState()
            }
            else -> AiConnectionsState()
        }
    }

    private fun decodeVersion2(root: JsonObject): AiConnectionsState {
        val connections = (root["connections"] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                runCatching { decodeConnection(element.jsonObject) }.getOrNull()
            }
            .distinctBy(AiConnectionSettings::id)
            .take(MAX_PROFILES)
        val connectionIds = connections.map(AiConnectionSettings::id).toSet()
        val models = (root["models"] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                runCatching { decodeModel(element.jsonObject) }.getOrNull()
            }
            .filter { it.connectionId in connectionIds }
            .distinctBy(AiModelSelectionSettings::connectionId)
            .take(MAX_PROFILES)
        return AiConnectionsState(
            connections = connections,
            models = models
        )
    }

    private fun decodeConnection(value: JsonObject): AiConnectionSettings {
        fun required(key: String): String =
            value[key]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: error("Missing connection field: $key")

        val connection = AiConnectionSettings(
            id = required("id"),
            presetId = value["presetId"]?.jsonPrimitive?.contentOrNull,
            providerDefinitionId = required("providerDefinitionId"),
            displayName = required("displayName"),
            dialect = value["dialect"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(AiStoredDialect::valueOf),
            baseUrl = required("baseUrl"),
            authScheme = AiStoredAuthScheme.valueOf(required("authScheme")),
            credentialRef = value["credentialRef"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank),
            local = value["local"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: false,
            enabled = value["enabled"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: true
        )
        validateConnection(connection)
        return connection
    }

    private fun decodeModel(value: JsonObject): AiModelSelectionSettings {
        fun required(key: String): String =
            value[key]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: error("Missing model field: $key")

        val model = AiModelSelectionSettings(
            connectionId = required("connectionId"),
            modelId = required("modelId"),
            reasoningEffort = value["reasoningEffort"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank) ?: "medium"
        )
        validateModel(model)
        return model
    }

    private fun decodeLegacyProfiles(values: JsonArray): List<AiProviderProfileSettings> =
        values.mapNotNull { element ->
            runCatching { decodeLegacyProfile(element.jsonObject) }.getOrNull()
        }
            .distinctBy(AiProviderProfileSettings::id)
            .take(MAX_PROFILES)

    private fun decodeLegacyProfile(value: JsonObject): AiProviderProfileSettings {
        fun required(key: String): String =
            value[key]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: error("Missing legacy profile field: $key")

        val profile = AiProviderProfileSettings(
            id = required("id"),
            presetId = value["presetId"]?.jsonPrimitive?.contentOrNull,
            displayName = required("displayName"),
            protocol = required("protocol"),
            baseUrl = required("baseUrl"),
            modelId = value["modelId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            local = value["local"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: false,
            enabled = value["enabled"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: true,
            reasoningEffort = value["reasoningEffort"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank) ?: "medium"
        )
        validateProfile(profile)
        return profile
    }

    private fun profilesFromState(state: AiConnectionsState): List<AiProviderProfileSettings> {
        val models = state.models.associateBy(AiModelSelectionSettings::connectionId)
        return state.connections.map { connection ->
            val model = models[connection.id]
            AiProviderProfileSettings(
                id = connection.id,
                presetId = connection.presetId,
                displayName = connection.displayName,
                protocol = legacyProtocolFor(connection.dialect),
                baseUrl = connection.baseUrl,
                modelId = model?.modelId.orEmpty(),
                local = connection.local,
                enabled = connection.enabled,
                reasoningEffort = model?.reasoningEffort ?: "medium",
                providerDefinitionId = connection.providerDefinitionId,
                dialect = connection.dialect?.name,
                authScheme = connection.authScheme.name,
                credentialRef = connection.credentialRef
            )
        }
    }

    private fun stateFromProfiles(
        profiles: List<AiProviderProfileSettings>
    ): AiConnectionsState {
        val normalized = profiles
            .mapNotNull { profile ->
                runCatching {
                    validateProfile(profile)
                    val dialect = storedDialect(profile)
                    val authScheme = storedAuthScheme(profile)
                    val connection = AiConnectionSettings(
                        id = profile.id,
                        presetId = profile.presetId,
                        providerDefinitionId = profile.providerDefinitionId
                            ?.takeIf(String::isNotBlank)
                            ?: definitionIdForPreset(profile.presetId),
                        displayName = profile.displayName,
                        dialect = dialect,
                        baseUrl = profile.baseUrl,
                        authScheme = authScheme,
                        credentialRef = profile.credentialRef
                            ?.takeIf(String::isNotBlank)
                            ?: if (authScheme == AiStoredAuthScheme.NONE) {
                                null
                            } else {
                                defaultCredentialRef(profile.id)
                            },
                        local = profile.local,
                        enabled = profile.enabled
                    )
                    validateConnection(connection)
                    connection to profile.modelId
                        .takeIf(String::isNotBlank)
                        ?.let {
                            AiModelSelectionSettings(
                                connectionId = profile.id,
                                modelId = it,
                                reasoningEffort = profile.reasoningEffort
                            ).also(::validateModel)
                        }
                }.getOrNull()
            }
            .take(MAX_PROFILES)

        return AiConnectionsState(
            connections = normalized.map(Pair<AiConnectionSettings, AiModelSelectionSettings?>::first),
            models = normalized.mapNotNull(Pair<AiConnectionSettings, AiModelSelectionSettings?>::second)
        )
    }

    private fun encodeState(state: AiConnectionsState): String =
        buildJsonObject {
            put("version", PROFILE_SCHEMA_VERSION)
            put("connections", buildJsonArray {
                state.connections.take(MAX_PROFILES).forEach { value ->
                    add(buildJsonObject {
                        put("id", value.id)
                        if (value.presetId == null) put("presetId", JsonNull)
                        else put("presetId", value.presetId)
                        put("providerDefinitionId", value.providerDefinitionId)
                        put("displayName", value.displayName)
                        if (value.dialect == null) put("dialect", JsonNull)
                        else put("dialect", value.dialect.name)
                        put("baseUrl", value.baseUrl)
                        put("authScheme", value.authScheme.name)
                        if (value.credentialRef == null) put("credentialRef", JsonNull)
                        else put("credentialRef", value.credentialRef)
                        put("local", value.local)
                        put("enabled", value.enabled)
                    })
                }
            })
            put("models", buildJsonArray {
                state.models.take(MAX_PROFILES).forEach { value ->
                    add(buildJsonObject {
                        put("connectionId", value.connectionId)
                        put("modelId", value.modelId)
                        put("reasoningEffort", value.reasoningEffort)
                    })
                }
            })
        }.toString()

    private fun isVersion2(raw: String): Boolean =
        runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            root["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ==
                PROFILE_SCHEMA_VERSION
        }.getOrDefault(false)

    private fun storedDialect(profile: AiProviderProfileSettings): AiStoredDialect? {
        profile.dialect?.takeIf(String::isNotBlank)?.let { value ->
            runCatching { return AiStoredDialect.valueOf(value) }
        }
        return when (profile.presetId) {
            "openai" -> AiStoredDialect.OPENAI_RESPONSES
            "gemini" -> AiStoredDialect.GEMINI_GENERATE_CONTENT
            "claude" -> AiStoredDialect.ANTHROPIC_MESSAGES
            "opencode_zen" -> null
            else -> when (profile.protocol) {
                "ANTHROPIC_MESSAGES" -> AiStoredDialect.ANTHROPIC_MESSAGES
                else -> AiStoredDialect.OPENAI_CHAT_COMPLETIONS
            }
        }
    }

    private fun storedAuthScheme(profile: AiProviderProfileSettings): AiStoredAuthScheme {
        profile.authScheme?.takeIf(String::isNotBlank)?.let { value ->
            runCatching { return AiStoredAuthScheme.valueOf(value) }
        }
        return when {
            profile.presetId == "claude" -> AiStoredAuthScheme.X_API_KEY
            profile.presetId == "gemini" -> AiStoredAuthScheme.GOOGLE_API_KEY
            profile.local && profile.presetId == null -> AiStoredAuthScheme.NONE
            else -> AiStoredAuthScheme.BEARER_TOKEN
        }
    }

    private fun legacyProtocolFor(dialect: AiStoredDialect?): String =
        when (dialect) {
            AiStoredDialect.ANTHROPIC_MESSAGES -> "ANTHROPIC_MESSAGES"
            AiStoredDialect.OPENAI_CHAT_COMPLETIONS,
            AiStoredDialect.OPENAI_RESPONSES,
            AiStoredDialect.GEMINI_GENERATE_CONTENT,
            null -> "OPENAI_CHAT_COMPLETIONS"
        }

    private fun definitionIdForPreset(presetId: String?): String =
        when (presetId) {
            "openai" -> "openai"
            "claude" -> "anthropic"
            "gemini" -> "google"
            "opencode_zen" -> "opencode"
            LEGACY_PROFILE_PRESET_ID -> "openai_compatible"
            null -> "custom"
            else -> presetId
        }

    private fun validateConnection(value: AiConnectionSettings) {
        require(value.id.length in 1..MAX_PROVIDER_ID_LENGTH)
        require(value.presetId.orEmpty().length <= MAX_PROVIDER_ID_LENGTH)
        require(value.providerDefinitionId.length in 1..MAX_PROVIDER_ID_LENGTH)
        require(value.displayName.isNotBlank() && value.displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(value.baseUrl.length in 8..MAX_BASE_URL_LENGTH)
        value.credentialRef?.let {
            require(it.length <= MAX_CREDENTIAL_REFERENCE_LENGTH)
            require(it.matches(CREDENTIAL_REFERENCE_PATTERN))
        }
    }

    private fun validateModel(value: AiModelSelectionSettings) {
        require(value.connectionId.length in 1..MAX_PROVIDER_ID_LENGTH)
        require(value.modelId.length in 1..MAX_MODEL_ID_LENGTH)
        require(value.reasoningEffort.length in 1..MAX_REASONING_EFFORT_LENGTH)
    }

    private fun validateProfile(value: AiProviderProfileSettings) {
        require(value.id.length in 1..MAX_PROVIDER_ID_LENGTH)
        require(value.presetId.orEmpty().length <= MAX_PROVIDER_ID_LENGTH)
        require(value.displayName.isNotBlank() && value.displayName.length <= MAX_DISPLAY_NAME_LENGTH)
        require(value.protocol.length in 1..MAX_ROUTING_MODE_LENGTH)
        require(value.baseUrl.length in 8..MAX_BASE_URL_LENGTH)
        require(value.modelId.length <= MAX_MODEL_ID_LENGTH)
        require(value.providerDefinitionId.orEmpty().length <= MAX_PROVIDER_ID_LENGTH)
        require(value.dialect.orEmpty().length <= MAX_ROUTING_MODE_LENGTH)
        require(value.authScheme.orEmpty().length <= MAX_ROUTING_MODE_LENGTH)
        value.credentialRef?.let {
            require(it.length <= MAX_CREDENTIAL_REFERENCE_LENGTH)
            require(it.matches(CREDENTIAL_REFERENCE_PATTERN))
        }
    }

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 128
        const val MAX_BASE_URL_LENGTH = 2048
        const val MAX_MODEL_ID_LENGTH = 256
        const val MAX_ROUTING_MODE_LENGTH = 64
        const val MAX_PROVIDER_ID_LENGTH = 128
        const val MAX_PROFILES = 16
        const val PROFILE_SCHEMA_VERSION = AiConnectionsState.PROFILE_SCHEMA_VERSION
        const val LEGACY_PROFILE_ID = "openai_compatible"
        const val LEGACY_PROFILE_PRESET_ID = "legacy_migration"

        private const val MAX_REASONING_EFFORT_LENGTH = 32
        private const val MAX_CREDENTIAL_REFERENCE_LENGTH = 256
        private val CREDENTIAL_REFERENCE_PATTERN =
            Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}")

        private fun defaultCredentialRef(profileId: String): String =
            "ai.provider.profile.$profileId.api_key"

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
