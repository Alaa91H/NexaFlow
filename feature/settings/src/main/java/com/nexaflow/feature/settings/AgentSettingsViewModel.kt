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
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiEndpointPolicy
import com.nexaflow.core.datastore.AiProviderPreferences
import com.nexaflow.core.datastore.AiProviderSettings
import com.nexaflow.core.security.SecureStorage
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

data class AgentSettingsUiState(
    val loading: Boolean = true,
    val accessEnabled: Boolean = false,
    val serverPort: Int = 0,
    val activeSessionCount: Int = 0,
    val pendingPairingCount: Int = 0,
    val agents: List<AgentGrantRecord> = emptyList(),
    val activity: List<AgentApiAuditEventV1> = emptyList(),
    val pairing: AgentPairingUi? = null,
    val providerSettings: AiProviderSettings = AiProviderSettings(),
    val providerApiKeyConfigured: Boolean = false,
    val providerProbeState: AiProviderProbeState = AiProviderProbeState.IDLE,
    val operationFailed: Boolean = false
)

@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    private val accessManager: AgentAccessManager,
    private val runtime: AgentApiRuntime,
    private val providerPreferences: AiProviderPreferences,
    private val provider: OpenAiCompatibleProvider,
    private val providerRegistry: AiProviderRegistry,
    private val secureStorage: SecureStorage
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
        apiKey: String
    ) {
        viewModelScope.launch {
            val candidate = AiProviderSettings(
                enabled = enabled,
                displayName = displayName.trim(),
                baseUrl = baseUrl.trim().trimEnd('/'),
                modelId = modelId.trim(),
                local = local
            )
            val success = runCatching {
                val existingKey = secureStorage.get(
                    OpenAiCompatibleProvider.API_KEY_STORAGE_KEY
                )
                val hasApiKey = apiKey.isNotBlank() || !existingKey.isNullOrBlank()
                if (candidate.enabled) {
                    OpenAiEndpointPolicy.chatCompletionsUri(
                        candidate.toProviderConfig(),
                        hasApiKey = hasApiKey
                    )
                }
                providerPreferences.update(candidate)
                if (apiKey.isNotBlank()) {
                    secureStorage.put(
                        OpenAiCompatibleProvider.API_KEY_STORAGE_KEY,
                        apiKey
                    )
                }
                provider.configure(candidate.toProviderConfig())
                providerRegistry.refreshDescriptors()
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                operationFailed = !success
            )
        }
    }

    fun clearProviderApiKey() {
        viewModelScope.launch {
            val success = runCatching {
                secureStorage.remove(OpenAiCompatibleProvider.API_KEY_STORAGE_KEY)
            }.isSuccess
            reload(
                providerProbeState = AiProviderProbeState.IDLE,
                operationFailed = !success
            )
        }
    }

    fun testProvider() {
        if (_state.value.providerProbeState == AiProviderProbeState.TESTING) return
        _state.value = _state.value.copy(
            providerProbeState = AiProviderProbeState.TESTING
        )
        viewModelScope.launch {
            val result = provider.probe()
            providerRegistry.refreshDescriptors()
            reload(
                providerProbeState = if (result.success) {
                    AiProviderProbeState.SUCCESS
                } else {
                    AiProviderProbeState.FAILED
                }
            )
        }
    }

    fun clearOperationError() {
        _state.value = _state.value.copy(operationFailed = false)
    }

    private suspend fun reload(
        pairing: AgentPairingUi? = _state.value.pairing,
        providerProbeState: AiProviderProbeState = _state.value.providerProbeState,
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
        val providerSettings = runCatching {
            providerPreferences.current()
        }.getOrDefault(AiProviderSettings())
        val providerApiKeyConfigured = runCatching {
            !secureStorage.get(OpenAiCompatibleProvider.API_KEY_STORAGE_KEY).isNullOrBlank()
        }.getOrDefault(false)

        _state.value = AgentSettingsUiState(
            loading = false,
            accessEnabled = security?.accessEnabled ?: false,
            serverPort = AgentApiServer.currentPort,
            activeSessionCount = security?.activeSessionCount ?: 0,
            pendingPairingCount = security?.pendingPairingCount ?: 0,
            agents = agents,
            activity = activity,
            pairing = pairing,
            providerSettings = providerSettings,
            providerApiKeyConfigured = providerApiKeyConfigured,
            providerProbeState = providerProbeState,
            operationFailed = operationFailed || security == null
        )
    }

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
