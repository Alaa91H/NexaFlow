package com.nexaflow.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiConversationEvent
import com.nexaflow.core.airuntime.AiConversationMessage
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiRole
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiChatUiState(
    val messages: List<AiConversationMessage> = emptyList(),
    val assistantDraft: String = "",
    val providerName: String? = null,
    val running: Boolean = false,
    val activeToolName: String? = null,
    val errorCode: String? = null
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val engine: AiConversationEngine,
    private val providerRegistry: AiProviderRegistry
) : ViewModel() {
    private val conversationId = "chat." + UUID.randomUUID()
    private val transcript = mutableListOf<AiConversationMessage>()
    private var responseJob: Job? = null

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            providerRegistry.state.collect { registry ->
                _state.value = _state.value.copy(
                    providerName = registry.selectedProvider?.displayName
                )
            }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (
            message.isBlank() ||
            message.length > AiConversationEngine.MAX_MESSAGE_CHARACTERS ||
            _state.value.running
        ) {
            return
        }

        transcript += AiConversationMessage(AiRole.USER, message)
        publish(
            running = true,
            assistantDraft = "",
            activeToolName = null,
            errorCode = null
        )

        responseJob = viewModelScope.launch {
            engine.stream(conversationId, transcript.toList()).collect { event ->
                when (event) {
                    is AiConversationEvent.AssistantDelta -> {
                        _state.value = _state.value.copy(
                            assistantDraft = _state.value.assistantDraft + event.text
                        )
                    }
                    is AiConversationEvent.ToolStarted -> {
                        _state.value = _state.value.copy(
                            activeToolName = event.call.name
                        )
                    }
                    is AiConversationEvent.ToolFinished -> {
                        _state.value = _state.value.copy(activeToolName = null)
                    }
                    is AiConversationEvent.Completed -> {
                        transcript.clear()
                        transcript += event.messages
                        publish(
                            running = false,
                            assistantDraft = "",
                            activeToolName = null,
                            errorCode = null
                        )
                    }
                    is AiConversationEvent.Unavailable -> {
                        publish(
                            running = false,
                            activeToolName = null,
                            errorCode = event.reason
                        )
                    }
                    is AiConversationEvent.Failed -> {
                        publish(
                            running = false,
                            activeToolName = null,
                            errorCode = event.code
                        )
                    }
                }
            }
        }
    }

    fun cancel() {
        responseJob?.cancel()
        responseJob = null
        val partial = _state.value.assistantDraft
        if (partial.isNotBlank()) {
            transcript += AiConversationMessage(AiRole.ASSISTANT, partial)
        }
        publish(
            running = false,
            assistantDraft = "",
            activeToolName = null,
            errorCode = null
        )
    }

    fun clearConversation() {
        responseJob?.cancel()
        responseJob = null
        transcript.clear()
        _state.value = AiChatUiState(
            providerName = providerRegistry.state.value.selectedProvider?.displayName
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(errorCode = null)
    }

    private fun publish(
        running: Boolean = _state.value.running,
        assistantDraft: String = _state.value.assistantDraft,
        activeToolName: String? = _state.value.activeToolName,
        errorCode: String? = _state.value.errorCode
    ) {
        _state.value = _state.value.copy(
            messages = transcript.filter {
                it.role in setOf(AiRole.USER, AiRole.ASSISTANT) &&
                    it.text.isNotBlank()
            },
            running = running,
            assistantDraft = assistantDraft,
            activeToolName = activeToolName,
            errorCode = errorCode
        )
    }
}
