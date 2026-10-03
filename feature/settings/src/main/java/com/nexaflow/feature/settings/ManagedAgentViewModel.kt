package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.agentruntime.AgentBudget
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentMemoryEntry
import com.nexaflow.core.agentruntime.AgentPolicy
import com.nexaflow.core.airuntime.AiToolDefinition
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.datastore.AiProviderPreferences
import com.nexaflow.core.datastore.AiProviderProfileSettings
import com.nexaflow.data.agents.AgentDefinitionRepository
import com.nexaflow.data.agents.AgentDefinitionWriteResult
import com.nexaflow.data.agents.AgentMemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ManagedAgentDraft(
    val id: String? = null,
    val expectedRevision: Long? = null,
    val name: String = "",
    val description: String = "",
    val systemInstructions: String = "",
    val providerProfileId: String? = null,
    val modelId: String = "",
    val allowedToolNames: Set<String> = emptySet(),
    val approvalRequiredToolNames: Set<String> = emptySet(),
    val approvalOptionalToolNames: Set<String> = emptySet(),
    val allowCloudData: Boolean = false,
    val enabled: Boolean = false,
    val maxTurns: String = "8",
    val maxToolCalls: String = "32",
    val maxDurationSeconds: String = "60",
    val maxOutputCharacters: String = "65536",
    val maxCostMicros: String = ""
)

data class ManagedAgentUiState(
    val definitions: List<AgentDefinition> = emptyList(),
    val tools: List<AiToolDefinition> = emptyList(),
    val providerProfiles: List<AiProviderProfileSettings> = emptyList(),
    val draft: ManagedAgentDraft? = null,
    val operationInProgress: Boolean = false,
    val errorCode: String? = null
)

data class ManagedAgentMemoryUiState(
    val agentId: String? = null,
    val entries: List<AgentMemoryEntry> = emptyList(),
    val loading: Boolean = false,
    val errorCode: String? = null,
    val editing: AgentMemoryEntry? = null,
    val editMode: Boolean = false
)

@HiltViewModel
class ManagedAgentViewModel @Inject constructor(
    private val repository: AgentDefinitionRepository,
    private val memoryRepository: AgentMemoryRepository,
    private val toolExecutor: AiToolExecutor,
    providerPreferences: AiProviderPreferences
) : ViewModel() {
    private val editing = MutableStateFlow<ManagedAgentDraft?>(null)
    private val operation = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val memoryState = MutableStateFlow(ManagedAgentMemoryUiState())
    val memory: StateFlow<ManagedAgentMemoryUiState> = memoryState.asStateFlow()

    val state: StateFlow<ManagedAgentUiState> = combine(
        repository.observeAll(),
        toolExecutor.tools,
        providerPreferences.profiles,
        editing,
        operation,
        error
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ManagedAgentUiState(
            definitions = values[0] as List<AgentDefinition>,
            tools = values[1] as List<AiToolDefinition>,
            providerProfiles = values[2] as List<AiProviderProfileSettings>,
            draft = values[3] as ManagedAgentDraft?,
            operationInProgress = values[4] as Boolean,
            errorCode = values[5] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagedAgentUiState())

    fun createNew() {
        error.value = null
        editing.value = ManagedAgentDraft()
    }

    fun edit(definition: AgentDefinition) {
        error.value = null
        editing.value = definition.toDraft()
    }

    fun cancelEdit() {
        editing.value = null
        error.value = null
    }

    fun updateDraft(transform: (ManagedAgentDraft) -> ManagedAgentDraft) {
        editing.value = editing.value?.let(transform)
        error.value = null
    }

    fun save() {
        val draft = editing.value ?: return
        if (operation.value) return
        val newName = draft.name.trim()
        if (newName.isBlank()) {
            error.value = "invalid_name"
            return
        }
        val parsed = runCatching {
            val maxCost = draft.maxCostMicros.trim().takeIf(String::isNotEmpty)?.toLong()
            val budget = AgentBudget(
                maxTurns = draft.maxTurns.toInt(),
                maxToolCalls = draft.maxToolCalls.toInt(),
                maxDurationMillis = draft.maxDurationSeconds.toLong() * MILLIS_PER_SECOND,
                maxOutputCharacters = draft.maxOutputCharacters.toInt(),
                maxCostMicros = maxCost
            )
            val policy = AgentPolicy(
                allowedToolNames = draft.allowedToolNames,
                approvalRequiredToolNames = draft.approvalRequiredToolNames,
                approvalOptionalToolNames = draft.approvalOptionalToolNames,
                allowCloudData = draft.allowCloudData
            )
            val now = System.currentTimeMillis()
            val revision = draft.expectedRevision?.plus(1L) ?: INITIAL_REVISION
            AgentDefinition(
                id = draft.id ?: NEW_AGENT_PREFIX + UUID.randomUUID().toString(),
                name = newName,
                description = draft.description.trim(),
                systemInstructions = draft.systemInstructions.trim(),
                providerProfileId = draft.providerProfileId,
                modelId = draft.modelId.trim().takeIf(String::isNotEmpty),
                policy = policy,
                budget = budget,
                enabled = draft.enabled,
                revision = revision,
                createdAtMillis = state.value.definitions
                    .firstOrNull { it.id == draft.id }?.createdAtMillis ?: now,
                updatedAtMillis = maxOf(now, state.value.definitions
                    .firstOrNull { it.id == draft.id }?.createdAtMillis ?: now)
            )
        }.getOrElse {
            error.value = "invalid_budget_or_policy"
            return
        }
        operation.value = true
        viewModelScope.launch {
            val result = if (draft.expectedRevision == null) {
                repository.create(parsed)
            } else {
                repository.update(parsed, draft.expectedRevision)
            }
            when (result) {
                is AgentDefinitionWriteResult.Saved -> {
                    editing.value = null
                    error.value = null
                }
                AgentDefinitionWriteResult.Conflict -> error.value = "revision_conflict"
                AgentDefinitionWriteResult.InvalidRevision -> error.value = "invalid_revision"
            }
            operation.value = false
        }
    }

    fun delete(definition: AgentDefinition) {
        if (operation.value) return
        operation.value = true
        viewModelScope.launch {
            val deleted = repository.delete(definition.id, definition.revision)
            val memoryCleared = if (deleted) runCatching { memoryRepository.clear(definition.id) }.isSuccess else true
            error.value = when {
                !deleted -> "revision_conflict"
                !memoryCleared -> "memory_clear_failed"
                else -> null
            }
            operation.value = false
        }
    }

    fun openMemory(agentId: String) {
        if (memoryState.value.loading) return
        memoryState.value = ManagedAgentMemoryUiState(agentId = agentId, loading = true)
        viewModelScope.launch {
            runCatching { memoryRepository.observeSnapshot(agentId) }
                .onSuccess { entries ->
                    memoryState.value = ManagedAgentMemoryUiState(agentId = agentId, entries = entries)
                }
                .onFailure {
                    memoryState.value = ManagedAgentMemoryUiState(agentId = agentId, errorCode = "storage_error")
                }
        }
    }

    fun editMemory(entry: AgentMemoryEntry? = null) {
        val agentId = memoryState.value.agentId ?: return
        memoryState.value = memoryState.value.copy(editing = entry, editMode = true, errorCode = null)
    }

    fun cancelMemoryEdit() {
        memoryState.value = memoryState.value.copy(editing = null, editMode = false, errorCode = null)
    }

    fun saveMemory(title: String, content: String, expiryDays: String, existing: AgentMemoryEntry?) {
        val agentId = memoryState.value.agentId ?: return
        val now = System.currentTimeMillis()
        val days = expiryDays.trim().ifEmpty { "0" }.toIntOrNull()
        if (title.isBlank() || content.isBlank() || days == null || days !in 0..365) {
            memoryState.value = memoryState.value.copy(errorCode = "invalid_memory")
            return
        }
        val entry = runCatching {
            AgentMemoryEntry(
                id = existing?.id ?: "memory-${UUID.randomUUID()}",
                agentId = agentId,
                title = title.trim().take(AgentMemoryEntry.MAX_TITLE_CHARACTERS),
                content = content.trim().take(AgentMemoryEntry.MAX_CONTENT_CHARACTERS),
                createdAtMillis = existing?.createdAtMillis ?: now,
                expiresAtMillis = if (days == 0) null else now + days * MILLIS_PER_DAY
            )
        }.getOrElse {
            memoryState.value = memoryState.value.copy(errorCode = "invalid_memory")
            return
        }
        memoryState.value = memoryState.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            runCatching { memoryRepository.save(entry); memoryRepository.observeSnapshot(agentId) }
                .onSuccess { entries ->
                    memoryState.value = ManagedAgentMemoryUiState(agentId = agentId, entries = entries)
                }
                .onFailure {
                    memoryState.value = memoryState.value.copy(loading = false, errorCode = "save_failed")
                }
        }
    }

    fun deleteMemory(entry: AgentMemoryEntry) {
        val agentId = memoryState.value.agentId ?: return
        memoryState.value = memoryState.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            runCatching { memoryRepository.delete(agentId, entry.id); memoryRepository.observeSnapshot(agentId) }
                .onSuccess { entries ->
                    memoryState.value = ManagedAgentMemoryUiState(agentId = agentId, entries = entries)
                }
                .onFailure {
                    memoryState.value = memoryState.value.copy(loading = false, errorCode = "save_failed")
                }
        }
    }

    fun exportMemory(agentId: String, onReady: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(memoryRepository.exportSnapshot(agentId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onReady(null)
            }
        }
    }

    fun closeMemory() { memoryState.value = ManagedAgentMemoryUiState() }

    private fun AgentDefinition.toDraft() = ManagedAgentDraft(
        id = id,
        expectedRevision = revision,
        name = name,
        description = description,
        systemInstructions = systemInstructions,
        providerProfileId = providerProfileId,
        modelId = modelId.orEmpty(),
        allowedToolNames = policy.allowedToolNames,
        approvalRequiredToolNames = policy.approvalRequiredToolNames,
        approvalOptionalToolNames = policy.approvalOptionalToolNames,
        allowCloudData = policy.allowCloudData,
        enabled = enabled,
        maxTurns = budget.maxTurns.toString(),
        maxToolCalls = budget.maxToolCalls.toString(),
        maxDurationSeconds = (budget.maxDurationMillis / MILLIS_PER_SECOND).toString(),
        maxOutputCharacters = budget.maxOutputCharacters.toString(),
        maxCostMicros = budget.maxCostMicros?.toString().orEmpty()
    )

    private companion object {
        const val INITIAL_REVISION = 1L
        const val NEW_AGENT_PREFIX = "agent-"
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
