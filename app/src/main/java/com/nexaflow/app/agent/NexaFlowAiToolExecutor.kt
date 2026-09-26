package com.nexaflow.app.agent

import com.nexaflow.core.agentapi.AgentHttpRequest
import com.nexaflow.core.agentapi.AgentMcpRestToolExecutor
import com.nexaflow.core.agentapi.AgentMcpToolRegistry
import com.nexaflow.core.agentapi.AgentTrustedPrincipal
import com.nexaflow.core.airuntime.AiToolCall
import com.nexaflow.core.airuntime.AiToolDefinition
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.AiToolResult
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class NexaFlowAiToolExecutor(
    controller: com.nexaflow.core.agentapi.AgentApiController,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }
) : AiToolExecutor {
    private val executor = AgentMcpRestToolExecutor(
        restController = controller,
        trustedPrincipal = AgentTrustedPrincipal(
            agentId = IN_APP_AGENT_ID,
            transport = IN_APP_TRANSPORT
        )
    )

    private val definitions = MutableStateFlow(
        AgentMcpToolRegistry.tools.map { tool ->
            AiToolDefinition(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }
    )

    override val tools: StateFlow<List<AiToolDefinition>> = definitions

    override suspend fun execute(call: AiToolCall): AiToolResult {
        val source = AgentHttpRequest(
            method = "POST",
            target = "/internal/ai-tool",
            headers = mapOf("host" to "127.0.0.1"),
            body = ByteArray(0)
        )
        val response = executor.execute(
            toolName = call.name,
            arguments = call.arguments,
            sourceRequest = source
        )
        val payload = runCatching {
            json.parseToJsonElement(
                response.body.toString(StandardCharsets.UTF_8)
            )
        }.getOrElse {
            buildJsonObject {
                put("error", "invalid_tool_response")
            }
        }
        return AiToolResult(
            callId = call.id,
            toolName = call.name,
            output = payload,
            isError = response.status !in 200..299
        )
    }

    private companion object {
        const val IN_APP_AGENT_ID = "nexaflow.in_app_ai"
        const val IN_APP_TRANSPORT = "IN_APP_AI"
    }
}
