package com.nexaflow.core.airuntime

import kotlinx.serialization.json.JsonObject

enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class AiConversationMessage(
    val role: AiRole,
    val text: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val isError: Boolean = false
)

data class AiToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

data class AiToolResult(
    val callId: String,
    val toolName: String,
    val output: JsonObject,
    val isError: Boolean = false
)

data class AiProviderCapabilities(
    val toolCalling: Boolean = false,
    val structuredOutput: Boolean = false,
    val streaming: Boolean = false,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val contextTokens: Int? = null,
    val local: Boolean = false
)

data class AiProviderDescriptor(
    val id: String,
    val displayName: String,
    val modelId: String? = null,
    val capabilities: AiProviderCapabilities = AiProviderCapabilities(),
    val available: Boolean = false,
    val detail: String? = null
)

data class AiProviderRequest(
    val conversationId: String,
    val messages: List<AiConversationMessage>,
    val tools: List<AiToolDefinition>,
    val maxOutputCharacters: Int
)

sealed interface AiProviderEvent {
    data class TextDelta(val text: String) : AiProviderEvent
    data class ToolCall(val call: AiToolCall) : AiProviderEvent
    data class Finished(val reason: String? = null) : AiProviderEvent
}

sealed interface AiConversationEvent {
    data class AssistantDelta(val text: String) : AiConversationEvent
    data class ToolStarted(val call: AiToolCall) : AiConversationEvent
    data class ToolFinished(val result: AiToolResult) : AiConversationEvent
    data class Completed(
        val messages: List<AiConversationMessage>
    ) : AiConversationEvent
    data class Unavailable(val reason: String) : AiConversationEvent
    data class Failed(val code: String) : AiConversationEvent
}
