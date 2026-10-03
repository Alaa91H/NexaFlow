package com.nexaflow.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentRun
import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.agentruntime.ManagedAgentRunUseCase
import com.nexaflow.data.agents.AgentDefinitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManagedAgentRunLocalState(
    val prompt: String = "",
    val output: String = "",
    val active: Boolean = false,
    val currentRunId: String? = null,
    val pendingApproval: ManagedAgentRunEvent.ApprovalRequired? = null,
    val errorCode: String? = null
)

data class ManagedAgentRunUiState(
    val agent: AgentDefinition? = null,
    val runs: List<AgentRun> = emptyList(),
    val local: ManagedAgentRunLocalState = ManagedAgentRunLocalState()
)

@HiltViewModel
class ManagedAgentRunViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    definitions: AgentDefinitionRepository,
    private val runUseCase: ManagedAgentRunUseCase
) : ViewModel() {
    private val agentId: String = checkNotNull(savedStateHandle["agentId"])
    private val local = MutableStateFlow(ManagedAgentRunLocalState())
    private var runJob: Job? = null

    val state: StateFlow<ManagedAgentRunUiState> = combine(
        definitions.observeAll(),
        runUseCase.observeRuns(agentId),
        local
    ) { allDefinitions, runs, localState ->
        ManagedAgentRunUiState(
            agent = allDefinitions.firstOrNull { it.id == agentId },
            runs = runs,
            local = localState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagedAgentRunUiState())

    fun editPrompt(value: String) {
        local.value = local.value.copy(prompt = value.take(MAX_PROMPT_CHARACTERS), errorCode = null)
    }

    fun run() {
        val definition = state.value.agent ?: return
        val prompt = local.value.prompt.trim()
        if (!definition.enabled || prompt.isBlank() || local.value.active) return
        runJob?.cancel()
        local.value = local.value.copy(
            output = "",
            active = true,
            currentRunId = null,
            pendingApproval = null,
            errorCode = null
        )
        runJob = viewModelScope.launch {
            try {
                runUseCase.runAgent(definition.id, prompt, UUID.randomUUID().toString()).collect { event ->
                    when (event) {
                        is ManagedAgentRunEvent.Queued -> local.value = local.value.copy(currentRunId = event.runId)
                        is ManagedAgentRunEvent.Existing -> local.value = local.value.copy(
                            active = false, currentRunId = event.runId, errorCode = "existing_run"
                        )
                        is ManagedAgentRunEvent.AssistantDelta -> {
                            val max = definition.budget.maxOutputCharacters
                            local.value = local.value.copy(output = (local.value.output + event.text).take(max))
                        }
                        is ManagedAgentRunEvent.ApprovalRequired -> local.value = local.value.copy(
                            pendingApproval = event
                        )
                        is ManagedAgentRunEvent.Completed -> local.value = local.value.copy(
                            active = false, currentRunId = event.runId, pendingApproval = null
                        )
                        is ManagedAgentRunEvent.Failed -> local.value = local.value.copy(
                            active = false,
                            currentRunId = event.runId ?: local.value.currentRunId,
                            pendingApproval = null,
                            errorCode = event.safeCode
                        )
                        is ManagedAgentRunEvent.Rejected -> local.value = local.value.copy(
                            active = false, errorCode = event.safeCode
                        )
                    }
                }
            } finally {
                local.value = local.value.copy(active = false, pendingApproval = null)
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        local.value = local.value.copy(active = false, pendingApproval = null)
    }

    fun resolveApproval(approved: Boolean) {
        val approval = local.value.pendingApproval ?: return
        viewModelScope.launch {
            val resolved = runUseCase.resolveApproval(approval.approvalId, approved)
            local.value = local.value.copy(
                pendingApproval = null,
                errorCode = if (resolved) null else "approval_invalidated"
            )
        }
    }

    override fun onCleared() {
        runJob?.cancel()
    }

    private companion object {
        const val MAX_PROMPT_CHARACTERS = 32_768
    }
}
