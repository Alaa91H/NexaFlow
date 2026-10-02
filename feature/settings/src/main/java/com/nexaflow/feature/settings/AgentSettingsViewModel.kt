package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentapi.AgentApiRuntime
import com.nexaflow.core.agentapi.AgentApiServer
import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentGrantRecord
import com.nexaflow.core.agentsecurity.AgentIdentityRequest
import com.nexaflow.core.agentsecurity.AgentPairingStartResult
import com.nexaflow.core.airuntime.AiAuthScheme
import com.nexaflow.core.airuntime.AiConnectionTestResult
import com.nexaflow.core.airuntime.AiCredentialReferences
import com.nexaflow.core.airuntime.AiCredentialStore
import com.nexaflow.core.airuntime.AiProviderDefinitionRegistry
import com.nexaflow.core.airuntime.AiProviderDescriptor
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiProviderProtocol
import com.nexaflow.core.airuntime.AiReasoningLevel
import com.nexaflow.core.airuntime.AiRoutingMode
import com.nexaflow.core.airuntime.AiRoutingPolicy
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiEndpointPolicy
import com.nexaflow.core.airuntime.OpenAiModelInfo
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.AnthropicMessagesProvider
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.datastore.AgentNetworkPreferences
import com.nexaflow.core.datastore.AiProviderPreferences
import com.nexaflow.core.datastore.AiProviderProfileSettings
import com.nexaflow.core.datastore.AiProviderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AgentPairingUi(
    val displayName: String,
    val payload: String,
    val expiresAt: Long
)

enum class AiProviderProbeState {
    IDLE,
    TESTING,
    SUCCESS,
    FAILED
}

enum class AiModelDiscoveryState { IDLE, LOADING, SUCCESS, UNSUPPORTED, FAILED }

data class AgentSettingsUiState(
    val loading: Boolean = true,
    val accessEnabled: Boolean = false,
    val serverPort: Int = 0,
    val lanAccessEnabled: Boolean = false,
    val activeSessionCount: Int = 0,
    val pendingPairingCount: Int = 0,
    val agents: List<AgentGrantRecord> = emptyList(),
    val activity: List<AgentApiAuditEventV1> = emptyList(),
    val pairing: AgentPairingUi? = null,
    val providerSettings: AiProviderSettings = AiProviderSettings(),
    val providerApiKeyConfigured: Boolean = false,
    val providerProbeState: AiProviderProbeState = AiProviderProbeState.IDLE,
    val providerProbeStatusCode: Int? = null,
    val providerLastVerifiedAtMillis: Long? = null,
    val modelDiscoveryState: AiModelDiscoveryState = AiModelDiscoveryState.IDLE,
    val profileModelChoices: List<String> = emptyList(),
    val discoveredModels: List<OpenAiModelInfo> = emptyList(),
    val providerDescriptors: List<AiProviderDescriptor> = emptyList(),
    val providerProfiles: List<AiProviderProfileSettings> = emptyList(),
    val operationFailed: Boolean = false
)

@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    private val accessManager: AgentAccessManager,
    private val runtime: AgentApiRuntime,
    private val agentNetworkPreferences: AgentNetworkPreferences,
    private val providerPreferences: AiProviderPreferences,
    private val provider: OpenAiCompatibleProvider,
    private val providerRegistry: AiProviderRegistry,
    private val credentialStore: AiCredentialStore,
    private val compatibleTransport: OpenAiCompatibleTransport,
    private val anthropicTransport: AnthropicMessagesTransport
) : ViewModel() {

    private val _state = MutableStateFlow(AgentSettingsUiState())
    val state: StateFlow<AgentSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun setAccessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val success = runCatching {
                accessManager.setAccessEnabled(enabled)
                if (!enabled) {
                    agentNetworkPreferences.setLanAccessEnabled(false)
                }
            }.isSuccess
            reload(operationFailed = !success)
        }
    }

    fun setLanAccessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val success = runCatching {
                agentNetworkPreferences.setLanAccessEnabled(enabled)
            }.isSuccess
            reload(operationFailed = !success)
        }
    }

    fun createPairing(displayName: String) {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank() || normalizedName.length > MAX_AGENT_NAME_LENGTH) {
            _state.value = _state.value.copy(operationFailed = true)
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                accessManager.beginPairing(
                    AgentIdentityRequest(
                        agentId = "agent.ui." + UUID.randomUUID(),
                        displayName = normalizedName
                    )
                )
            }.getOrNull()

            if (result !is AgentPairingStartResult.Started) {
                reload(operationFailed = true)
                return@launch
            }

            val port = AgentApiServer.currentPort
                .takeIf { it > 0 }
                ?: AgentApiServer.DEFAULT_PORT
            val payload = buildJsonObject {
                put("schemaVersion", 1)
                put("transport", "NEXAFLOW_LOCAL")
                put("host", "127.0.0.1")
                put("port", port)
                put("pairingPath", "/api/v1/auth/pair/complete")
                put("sessionPath", "/api/v1/auth/session")
                put("mcpPath", AgentApiServer.MCP_PATH)
                put("challengeId", result.offer.challengeId)
                put("challengeSecret", result.offer.challengeSecret)
                put("expiresAt", result.offer.expiresAt)
            }.toString()

            reload(
                pairing = AgentPairingUi(
                    displayName = normalizedName,
                    payload = payload,
                    expiresAt = result.offer.expiresAt
                )
            )
        }
    }

    fun clearPairing() {
        _state.value = _state.value.copy(pairing = null)
    }

    fun revokeAgent(agentId: String) {
        viewModelScope.launch {
            val success = runCatching {
                accessManager.revokeAgent(agentId)
            }.getOrDefault(false)
            reload(operationFailed = !success)
        }
    }

    fun revokeAllAgents() {
        viewModelScope.launch {
            val success = runCatching {
                accessManager.revokeAllAgents()
            }.isSuccess
            reload(pairing = null, operationFailed = !success)
        }
    }

    fun saveProvider(
        enabled: Boolean,
        displayName: String,
        baseUrl: String,
        modelId: String,
        local: Boolean,
        apiKey: String,
        routingMode: AiRoutingMode,
        selectedProviderId: String?,
        allowCloudFallback: Boolean
    ) {
        viewModelScope.launch {
            val candidate = AiProviderSettings(
                enabled = enabled,
                displayName = displayName.trim(),
                baseUrl = baseUrl.trim().trimEnd('/'),
                modelId = modelId.trim(),
                local = local,
                routingMode = routingMode.name,
                selectedProviderId = selectedProviderId,
                allowCloudFallback = allowCloudFallback
            )
            val success = runCatching {
                val existingKey = credentialStore.resolve(AiCredentialReferences.legacySingleProvider)
                val hasApiKey = apiKey.isNotBlank() || !existingKey.isNullOrBlank()
                if (candidate.enabled) {
                    OpenAiEndpointPolicy.chatCompletionsUri(
                        candidate.toProviderConfig(),
                        hasApiKey = hasApiKey
                    )
                }
                providerPreferences.update(candidate)
                if (apiKey.isNotBlank()) {
                    credentialStore.store(AiCredentialReferences.legacySingleProvider, apiKey)
                    providerPreferences.currentProfiles()
                        .firstOrNull {
                            it.presetId == AiProviderPreferences.LEGACY_PROFILE_PRESET_ID
                        }
                        ?.let { legacyProfile ->
                            credentialStore.store(AiCredentialReferences.forProfile(legacyProfile.id), apiKey)
                        }
                }
                provider.configure(candidate.toProviderConfig())
                providerRegistry.updateRoutingPolicy(candidate.toRoutingPolicy())
                providerRegistry.refreshDescriptors()
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                providerProbeStatusCode = null,
                discoveredModels = emptyList(),
                operationFailed = !success
            )
        }
    }

    fun clearProviderApiKey() {
        viewModelScope.launch {
            val success = runCatching {
                credentialStore.delete(AiCredentialReferences.legacySingleProvider)
                providerPreferences.currentProfiles()
                    .firstOrNull {
                        it.presetId == AiProviderPreferences.LEGACY_PROFILE_PRESET_ID
                    }
                    ?.let { credentialStore.delete(AiCredentialReferences.forProfile(it.id)) }
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                providerProbeStatusCode = null,
                providerLastVerifiedAtMillis = null,
                operationFailed = !success
            )
        }
    }

    fun saveProviderProfile(
        profileId: String?,
        presetId: String?,
        protocol: AiProviderProtocol,
        displayName: String,
        baseUrl: String,
        modelId: String,
        local: Boolean,
        apiKey: String,
        reasoningEffort: String = "medium",
        enableRequested: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val normalizedName = displayName.trim()
            val normalizedUrl = baseUrl.trim().trimEnd('/')
            val normalizedModel = modelId.trim()
            val id = profileId ?: presetId ?: "custom-${UUID.randomUUID()}"
            val preset = AiProviderDefinitionRegistry.preset(presetId)
            val definition = preset?.let {
                AiProviderDefinitionRegistry.definition(it.definitionId)
            }
            val canonicalDialect = if (presetId == "opencode_zen") {
                null
            } else {
                preset?.dialect ?: protocol.toDialect()
            }
            val canonicalAuth = preset?.authScheme ?: when {
                protocol == AiProviderProtocol.ANTHROPIC_MESSAGES -> AiAuthScheme.X_API_KEY
                local -> AiAuthScheme.NONE
                else -> AiAuthScheme.BEARER_TOKEN
            }
            val credentialReference = AiCredentialReferences.forProfile(id)
            val enableProfile = enableRequested &&
                AiProviderSetupPolicy.enableOnSave(_state.value.providerProbeState)
            val profile = AiProviderProfileSettings(
                id = id,
                presetId = presetId,
                displayName = normalizedName,
                protocol = protocol.name,
                baseUrl = normalizedUrl,
                modelId = normalizedModel,
                local = local,
                enabled = enableProfile,
                reasoningEffort = reasoningEffort,
                providerDefinitionId = preset?.definitionId ?: "custom",
                providerKind = definition?.kind?.name,
                dialect = canonicalDialect?.name,
                authScheme = canonicalAuth.name,
                credentialRef = if (canonicalAuth == AiAuthScheme.NONE) {
                    null
                } else {
                    credentialReference.value
                }
            )
            val saved = runCatching {
                val existingKey = credentialStore.resolve(AiCredentialReferences.forProfile(id))
                val key = apiKey.takeIf(String::isNotBlank) ?: existingKey
                val keyRequired = presetId != null || !local ||
                    protocol == AiProviderProtocol.ANTHROPIC_MESSAGES
                require(!key.isNullOrBlank() || !keyRequired)
                require(normalizedName.isNotBlank() && normalizedModel.isNotBlank())
                require(!enableRequested || enableProfile)
                when (protocol) {
                    AiProviderProtocol.OPENAI_CHAT_COMPLETIONS ->
                        OpenAiEndpointPolicy.chatCompletionsUri(
                            OpenAiCompatibleProviderConfig(
                                enabled = true, providerId = id, displayName = normalizedName,
                                baseUrl = normalizedUrl, modelId = normalizedModel, local = local
                            ),
                            hasApiKey = !key.isNullOrBlank()
                        )
                    AiProviderProtocol.ANTHROPIC_MESSAGES ->
                        com.nexaflow.core.airuntime.AnthropicEndpointPolicy.messagesUri(
                            AnthropicMessagesProviderConfig(
                                id, true, normalizedName, normalizedUrl, normalizedModel, local
                            ),
                            hasApiKey = true
                        )
                }
                if (!apiKey.isBlank()) {
                    credentialStore.store(AiCredentialReferences.forProfile(id), apiKey)
                }
                providerPreferences.upsertProfile(profile)
                if (!enableProfile && providerPreferences.current().selectedProviderId == id) {
                    val settings = providerPreferences.current()
                    providerPreferences.update(
                        settings.copy(
                            routingMode = AiRoutingMode.AUTOMATIC.name,
                            selectedProviderId = null,
                            allowCloudFallback = false
                        )
                    )
                }
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                providerProbeStatusCode = null,
                providerLastVerifiedAtMillis = null,
                operationFailed = !saved
            )
            onComplete(saved)
        }
    }

    fun verifyProviderProfile(id: String) {
        if (_state.value.providerProbeState == AiProviderProbeState.TESTING) return
        _state.value = _state.value.copy(
            providerProbeState = AiProviderProbeState.TESTING,
            providerProbeStatusCode = null,
            providerLastVerifiedAtMillis = null,
        )
        viewModelScope.launch {
            val verification: AiConnectionTestResult? = runCatching {
                val profile = providerPreferences.currentProfiles().first { it.id == id }
                val key = credentialStore.resolve(AiCredentialReferences.forProfile(id))
                    ?.takeIf(String::isNotBlank)
                if (!profile.local && key == null) return@runCatching null
                when (profile.protocol) {
                    AiProviderProtocol.OPENAI_CHAT_COMPLETIONS.name -> {
                        val adapter = OpenAiCompatibleProvider(
                            transport = compatibleTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            OpenAiCompatibleProviderConfig(
                                enabled = true, providerId = profile.id,
                                displayName = profile.displayName, baseUrl = profile.baseUrl,
                                modelId = profile.modelId, local = profile.local,
                                reasoningEffort = profile.reasoningEffort.takeIf {
                                    profile.presetId == "openai"
                                }
                            )
                        )
                        adapter.verifyConnection()
                    }
                    AiProviderProtocol.ANTHROPIC_MESSAGES.name -> {
                        val adapter = AnthropicMessagesProvider(
                            transport = anthropicTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            AnthropicMessagesProviderConfig(
                                id = profile.id, enabled = true,
                                displayName = profile.displayName, baseUrl = profile.baseUrl,
                                modelId = profile.modelId, local = profile.local,
                                reasoningEffort = profile.reasoningEffort.takeIf {
                                    profile.presetId == "claude"
                                }
                            )
                        )
                        adapter.verifyConnection()
                    }
                    else -> null
                }
            }.getOrNull()
            verification?.let(providerRegistry::recordConnectionTest)
            providerRegistry.refreshDescriptors()
            reload(
                providerProbeState = if (verification?.success == true) {
                    AiProviderProbeState.SUCCESS
                } else {
                    AiProviderProbeState.FAILED
                },
                providerProbeStatusCode = verification?.httpStatus,
                providerLastVerifiedAtMillis = if (verification?.success == true) {
                    System.currentTimeMillis()
                } else {
                    null
                },
                operationFailed = false
            )
        }
    }

    fun selectProviderProfile(id: String) {
        viewModelScope.launch {
            val success = runCatching {
                require(providerPreferences.currentProfiles().any { it.id == id && it.enabled })
                providerPreferences.update(
                    providerPreferences.current().copy(
                        routingMode = AiRoutingMode.SELECTED_PROVIDER.name,
                        selectedProviderId = id,
                        allowCloudFallback = false
                    )
                )
            }.isSuccess
            reload(operationFailed = !success)
        }
    }

    fun removeProviderProfile(id: String) {
        viewModelScope.launch {
            val success = runCatching {
                providerPreferences.removeProfile(id)
                credentialStore.delete(AiCredentialReferences.forProfile(id))
                if (providerPreferences.current().selectedProviderId == null) {
                    val settings = providerPreferences.current()
                    providerPreferences.update(
                        settings.copy(routingMode = AiRoutingMode.AUTOMATIC.name)
                    )
                }
            }.isSuccess
            reload(operationFailed = !success)
        }
    }

    fun clearProviderProfileApiKey(id: String) {
        viewModelScope.launch {
            val success = runCatching {
                credentialStore.delete(AiCredentialReferences.forProfile(id))
                providerPreferences.currentProfiles()
                    .firstOrNull { it.id == id }
                    ?.let { profile ->
                        providerPreferences.upsertProfile(
                            profile.copy(enabled = false, credentialRef = null)
                        )
                    }
                val settings = providerPreferences.current()
                if (settings.selectedProviderId == id) {
                    providerPreferences.update(
                        settings.copy(
                            routingMode = AiRoutingMode.AUTOMATIC.name,
                            selectedProviderId = null,
                            allowCloudFallback = false
                        )
                    )
                }
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                providerProbeStatusCode = null,
                providerLastVerifiedAtMillis = null,
                operationFailed = !success
            )
        }
    }

    fun discoverModels() {
        viewModelScope.launch {
            val result = provider.discoverModels()
            reload(
                discoveredModels = result.models,
                operationFailed = !result.success
            )
        }
    }

    fun discoverProfileModels(
        profileId: String?,
        presetId: String?,
        protocol: AiProviderProtocol,
        displayName: String,
        baseUrl: String,
        modelId: String,
        local: Boolean,
        apiKey: String
    ) {
        if (!AiProviderSetupPolicy.canDiscoverModels(_state.value.providerProbeState)) return
        _state.value = _state.value.copy(
            modelDiscoveryState = AiModelDiscoveryState.LOADING,
            profileModelChoices = emptyList()
        )
        viewModelScope.launch {
            val discovered = runCatching {
                val key = apiKey.trim().takeIf(String::isNotEmpty)
                    ?: profileId?.let { credentialStore.resolve(AiCredentialReferences.forProfile(it)) }
                        ?.takeIf(String::isNotBlank)
                when (protocol) {
                    AiProviderProtocol.OPENAI_CHAT_COMPLETIONS -> {
                        val adapter = OpenAiCompatibleProvider(
                            transport = compatibleTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            OpenAiCompatibleProviderConfig(
                                enabled = true,
                                providerId = presetId ?: profileId ?: "custom",
                                displayName = displayName,
                                baseUrl = baseUrl,
                                modelId = modelId.ifBlank { "model-discovery" },
                                local = local
                            )
                        )
                        adapter.discoverModels().let { result ->
                            when {
                                result.success && result.models.isNotEmpty() ->
                                    AiModelDiscoveryState.SUCCESS to result.models.map { it.id }
                                result.statusCode == 501 || (result.success && result.models.isEmpty()) ->
                                    AiModelDiscoveryState.UNSUPPORTED to emptyList()
                                else -> AiModelDiscoveryState.FAILED to emptyList()
                            }
                        }
                    }
                    AiProviderProtocol.ANTHROPIC_MESSAGES -> {
                        val adapter = AnthropicMessagesProvider(
                            transport = anthropicTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            AnthropicMessagesProviderConfig(
                                id = presetId ?: profileId ?: "claude",
                                enabled = true,
                                displayName = displayName,
                                baseUrl = baseUrl,
                                modelId = modelId.ifBlank { "model-discovery" },
                                local = local
                            )
                        )
                        adapter.discoverModels().let { result ->
                            when {
                                result.success && result.models.isNotEmpty() ->
                                    AiModelDiscoveryState.SUCCESS to result.models
                                result.statusCode == 404 || result.statusCode == 501 ||
                                    (result.success && result.models.isEmpty()) ->
                                    AiModelDiscoveryState.UNSUPPORTED to emptyList()
                                else -> AiModelDiscoveryState.FAILED to emptyList()
                            }
                        }
                    }
                }
            }.getOrElse { AiModelDiscoveryState.FAILED to emptyList() }
            _state.value = _state.value.copy(
                modelDiscoveryState = discovered.first,
                profileModelChoices = discovered.second
            )
        }
    }

    fun verifyProviderDraft(
        profileId: String?,
        presetId: String?,
        protocol: AiProviderProtocol,
        displayName: String,
        baseUrl: String,
        modelId: String,
        local: Boolean,
        apiKey: String
    ) {
        if (_state.value.providerProbeState == AiProviderProbeState.TESTING) return
        _state.value = _state.value.copy(
            providerProbeState = AiProviderProbeState.TESTING,
            providerProbeStatusCode = null,
        )
        viewModelScope.launch {
            val verification = runCatching {
                val key = apiKey.trim().takeIf(String::isNotEmpty)
                    ?: profileId?.let { credentialStore.resolve(AiCredentialReferences.forProfile(it)) }
                        ?.takeIf(String::isNotBlank)
                when (protocol) {
                    AiProviderProtocol.OPENAI_CHAT_COMPLETIONS -> {
                        val adapter = OpenAiCompatibleProvider(
                            transport = compatibleTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            OpenAiCompatibleProviderConfig(
                                enabled = true,
                                providerId = presetId ?: profileId ?: "custom",
                                displayName = displayName,
                                baseUrl = baseUrl,
                                modelId = modelId,
                                local = local,
                                reasoningEffort = AiReasoningLevel.BALANCED.apiValue.takeIf {
                                    presetId == "openai"
                                }
                            )
                        )
                        adapter.verifyConnection().let { it.success to it.httpStatus }
                    }
                    AiProviderProtocol.ANTHROPIC_MESSAGES -> {
                        val adapter = AnthropicMessagesProvider(
                            transport = anthropicTransport,
                            apiKeyProvider = { key }
                        )
                        adapter.configure(
                            AnthropicMessagesProviderConfig(
                                id = presetId ?: profileId ?: "claude",
                                enabled = true,
                                displayName = displayName,
                                baseUrl = baseUrl,
                                modelId = modelId,
                                local = local,
                                reasoningEffort = AiReasoningLevel.BALANCED.apiValue.takeIf {
                                    presetId == "claude"
                                }
                            )
                        )
                        adapter.verifyConnection().let { it.success to it.httpStatus }
                    }
                }
            }.getOrDefault(false to null)
            _state.value = _state.value.copy(
                providerProbeState = if (verification.first) AiProviderProbeState.SUCCESS
                    else AiProviderProbeState.FAILED,
                providerProbeStatusCode = verification.second,
                providerLastVerifiedAtMillis = if (verification.first) {
                    System.currentTimeMillis()
                } else {
                    null
                },
            )
        }
    }

    fun clearProfileModelChoices() {
        _state.value = _state.value.copy(
            modelDiscoveryState = AiModelDiscoveryState.IDLE,
            profileModelChoices = emptyList()
        )
    }

    fun invalidateProviderDraft() {
        _state.value = _state.value.copy(
            providerProbeState = AiProviderProbeState.IDLE,
            providerProbeStatusCode = null,
            providerLastVerifiedAtMillis = null,
            modelDiscoveryState = AiModelDiscoveryState.IDLE,
            profileModelChoices = emptyList()
        )
    }

    fun testProvider() {
        if (_state.value.providerProbeState == AiProviderProbeState.TESTING) return
        _state.value = _state.value.copy(
            providerProbeState = AiProviderProbeState.TESTING,
            providerProbeStatusCode = null,
        )
        viewModelScope.launch {
            val result = provider.verifyConnection()
            providerRegistry.recordConnectionTest(result)
            providerRegistry.refreshDescriptors()
            reload(
                providerProbeState = if (result.success) {
                    AiProviderProbeState.SUCCESS
                } else {
                    AiProviderProbeState.FAILED
                },
                providerProbeStatusCode = result.httpStatus,
            )
        }
    }

    fun clearOperationError() {
        _state.value = _state.value.copy(operationFailed = false)
    }

    fun clearActivity() {
        viewModelScope.launch {
            runCatching { runtime.clearAudit() }
            reload()
        }
    }

    private suspend fun reload(
        pairing: AgentPairingUi? = _state.value.pairing,
        providerProbeState: AiProviderProbeState = _state.value.providerProbeState,
        providerProbeStatusCode: Int? = _state.value.providerProbeStatusCode,
        providerLastVerifiedAtMillis: Long? = _state.value.providerLastVerifiedAtMillis,
        discoveredModels: List<OpenAiModelInfo> = _state.value.discoveredModels,
        operationFailed: Boolean = false
    ) {
        val security = runCatching { accessManager.status() }.getOrNull()
        val agents = runCatching {
            accessManager.listActiveGrants()
                .sortedByDescending { it.lastUsedAt ?: it.createdAt }
        }.getOrElse { emptyList() }
        val activity = runCatching {
            runtime.latestAudit(MAX_ACTIVITY_ROWS)
        }.getOrElse { emptyList() }
        val networkSettings = runCatching {
            agentNetworkPreferences.current()
        }.getOrDefault(com.nexaflow.core.datastore.AgentNetworkSettings())
        val providerSettings = runCatching {
            providerPreferences.current()
        }.getOrDefault(AiProviderSettings())
        val providerProfiles = runCatching {
            providerPreferences.currentProfiles()
        }.getOrDefault(emptyList())
        val providerApiKeyConfigured = runCatching {
            !credentialStore.resolve(AiCredentialReferences.legacySingleProvider).isNullOrBlank()
        }.getOrDefault(false)

        _state.value = AgentSettingsUiState(
            loading = false,
            accessEnabled = security?.accessEnabled ?: false,
            serverPort = AgentApiServer.currentPort,
            lanAccessEnabled = networkSettings.lanAccessEnabled,
            activeSessionCount = security?.activeSessionCount ?: 0,
            pendingPairingCount = security?.pendingPairingCount ?: 0,
            agents = agents,
            activity = activity,
            pairing = pairing,
            providerSettings = providerSettings,
            providerApiKeyConfigured = providerApiKeyConfigured,
            providerProbeState = providerProbeState,
            providerProbeStatusCode = providerProbeStatusCode,
            providerLastVerifiedAtMillis = providerLastVerifiedAtMillis,
            modelDiscoveryState = _state.value.modelDiscoveryState,
            profileModelChoices = _state.value.profileModelChoices,
            discoveredModels = discoveredModels,
            providerDescriptors = providerRegistry.state.value.providers,
            providerProfiles = providerProfiles,
            operationFailed = operationFailed || security == null
        )
    }

    private fun AiProviderSettings.toRoutingPolicy() =
        AiRoutingPolicy(
            mode = runCatching {
                AiRoutingMode.valueOf(routingMode)
            }.getOrDefault(AiRoutingMode.AUTOMATIC),
            selectedProviderId = selectedProviderId,
            allowCloudFallback = allowCloudFallback
        )

    private fun AiProviderSettings.toProviderConfig() =
        OpenAiCompatibleProviderConfig(
            enabled = enabled,
            displayName = displayName,
            baseUrl = baseUrl,
            modelId = modelId,
            local = local
        )

    private companion object {
        const val MAX_AGENT_NAME_LENGTH = 128
        const val MAX_ACTIVITY_ROWS = 30
    }
}
