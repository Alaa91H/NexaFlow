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

data class AgentSettingsUiState(
    val loading: Boolean = true,
    val accessEnabled: Boolean = false,
    val serverPort: Int = 0,
    val activeSessionCount: Int = 0,
    val pendingPairingCount: Int = 0,
    val agents: List<AgentGrantRecord> = emptyList(),
    val activity: List<AgentApiAuditEventV1> = emptyList(),
    val pairing: AgentPairingUi? = null,
    val operationFailed: Boolean = false
)

@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    private val accessManager: AgentAccessManager,
    private val runtime: AgentApiRuntime
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

    fun clearOperationError() {
        _state.value = _state.value.copy(operationFailed = false)
    }

    private suspend fun reload(
        pairing: AgentPairingUi? = _state.value.pairing,
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

        _state.value = AgentSettingsUiState(
            loading = false,
            accessEnabled = security?.accessEnabled ?: false,
            serverPort = AgentApiServer.currentPort,
            activeSessionCount = security?.activeSessionCount ?: 0,
            pendingPairingCount = security?.pendingPairingCount ?: 0,
            agents = agents,
            activity = activity,
            pairing = pairing,
            operationFailed = operationFailed || security == null
        )
    }

    private companion object {
        const val MAX_AGENT_NAME_LENGTH = 128
        const val MAX_ACTIVITY_ROWS = 30
    }
}
