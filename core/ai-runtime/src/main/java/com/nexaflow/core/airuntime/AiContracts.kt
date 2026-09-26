package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AiModelProvider {
    val descriptor: StateFlow<AiProviderDescriptor>

    fun stream(request: AiProviderRequest): Flow<AiProviderEvent>
}

interface AiToolExecutor {
    val tools: StateFlow<List<AiToolDefinition>>

    suspend fun execute(call: AiToolCall): AiToolResult
}

object EmptyAiToolExecutor : AiToolExecutor {
    private val empty = kotlinx.coroutines.flow.MutableStateFlow(emptyList<AiToolDefinition>())
    override val tools: StateFlow<List<AiToolDefinition>> = empty

    override suspend fun execute(call: AiToolCall): AiToolResult =
        AiToolResult(
            callId = call.id,
            toolName = call.name,
            output = kotlinx.serialization.json.buildJsonObject {
                put("error", "tool_unavailable")
            },
            isError = true
        )
}
