package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class OpenAiCompatibleProviderConfig(
    val enabled: Boolean = false,
    val displayName: String = "OpenAI-compatible",
    val baseUrl: String = "",
    val modelId: String = "",
    val local: Boolean = true
)

data class OpenAiCompatibleTransportResponse(
    val statusCode: Int,
    val body: String
)

fun interface OpenAiCompatibleTransport {
    suspend fun postChatCompletions(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse
}

class OpenAiCompatibleProvider(
    private val transport: OpenAiCompatibleTransport,
    private val apiKeyProvider: suspend () -> String? = { null },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : AiModelProvider {

    private val _descriptor =
        MutableStateFlow(descriptorFor(OpenAiCompatibleProviderConfig()))
    override val descriptor: StateFlow<AiProviderDescriptor> = _descriptor

    @Volatile
    private var config = OpenAiCompatibleProviderConfig()

    fun configure(value: OpenAiCompatibleProviderConfig) {
        config = value.normalized()
        _descriptor.value = descriptorFor(config)
    }

    override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> = flow {
        val snapshot = config
        check(isConfigured(snapshot)) {
            "OpenAI-compatible provider is not configured"
        }

        val response = transport.postChatCompletions(
            config = snapshot,
            body = request.toChatCompletionBody(snapshot.modelId),
            apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
        )
        if (response.statusCode !in 200..299) {
            error("Provider returned HTTP ${response.statusCode}")
        }

        val root = json.parseToJsonElement(response.body).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalArgumentException("Provider response has no choices")
        val message = choice["message"]?.jsonObject
            ?: throw IllegalArgumentException("Provider response has no message")

        message["content"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf(String::isNotEmpty)
            ?.let { emit(AiProviderEvent.TextDelta(it)) }

        message["tool_calls"]
            ?.takeUnless { it is JsonNull }
            ?.jsonArray
            ?.forEach { item ->
                emit(AiProviderEvent.ToolCall(item.jsonObject.toToolCall()))
            }

        emit(
            AiProviderEvent.Finished(
                choice["finish_reason"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonPrimitive
                    ?.contentOrNull
            )
        )
    }

    private fun AiProviderRequest.toChatCompletionBody(modelId: String): JsonObject =
        buildJsonObject {
            put("model", modelId)
            put("stream", false)
            put("messages", buildJsonArray {
                messages.forEach { add(it.toOpenAiMessage()) }
            })
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.inputSchema)
                                }
                            }
                        )
                    }
                }
                put("tool_choice", "auto")
            }
        }

    private fun AiConversationMessage.toOpenAiMessage(): JsonObject =
        buildJsonObject {
            put("role", role.toOpenAiRole())
            when {
                role == AiRole.TOOL -> {
                    put("content", text)
                    toolCallId?.let { put("tool_call_id", it) }
                }
                toolCalls.isNotEmpty() -> {
                    put(
                        "content",
                        text.takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: JsonNull
                    )
                    putJsonArray("tool_calls") {
                        toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", call.name)
                                        put("arguments", call.arguments.toString())
                                    }
                                }
                            )
                        }
                    }
                }
                else -> put("content", text)
            }
        }

    private fun JsonObject.toToolCall(): AiToolCall {
        val id = this["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.length in 1..MAX_TOOL_CALL_ID_LENGTH }
            ?: throw IllegalArgumentException("Tool call id is invalid")
        val function = this["function"]?.jsonObject
            ?: throw IllegalArgumentException("Tool call function is missing")
        val name = function["name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.length in 1..AiConversationEngine.MAX_TOOL_NAME_LENGTH }
            ?: throw IllegalArgumentException("Tool call name is invalid")
        val rawArguments = function["arguments"]?.jsonPrimitive?.contentOrNull
            ?: "{}"
        if (rawArguments.length > AiConversationEngine.MAX_TOOL_ARGUMENT_CHARACTERS) {
            throw IllegalArgumentException("Tool arguments exceed limit")
        }
        val arguments = json.parseToJsonElement(rawArguments) as? JsonObject
            ?: throw IllegalArgumentException("Tool arguments must be an object")
        return AiToolCall(id = id, name = name, arguments = arguments)
    }

    private fun AiRole.toOpenAiRole(): String = when (this) {
        AiRole.SYSTEM -> "system"
        AiRole.USER -> "user"
        AiRole.ASSISTANT -> "assistant"
        AiRole.TOOL -> "tool"
    }

    private fun OpenAiCompatibleProviderConfig.normalized() = copy(
        displayName = displayName.trim().ifBlank { "OpenAI-compatible" },
        baseUrl = baseUrl.trim().trimEnd('/'),
        modelId = modelId.trim()
    )

    private fun descriptorFor(value: OpenAiCompatibleProviderConfig) =
        AiProviderDescriptor(
            id = PROVIDER_ID,
            displayName = value.displayName.ifBlank { "OpenAI-compatible" },
            modelId = value.modelId.takeIf(String::isNotBlank),
            capabilities = AiProviderCapabilities(
                toolCalling = true,
                structuredOutput = true,
                streaming = false,
                local = value.local
            ),
            available = isConfigured(value),
            detail = value.baseUrl.takeIf(String::isNotBlank)
        )

    private fun isConfigured(value: OpenAiCompatibleProviderConfig): Boolean =
        value.enabled &&
            value.modelId.isNotBlank() &&
            value.baseUrl.length in 8..MAX_BASE_URL_LENGTH &&
            runCatching {
                val uri = java.net.URI(value.baseUrl)
                uri.scheme in setOf("http", "https") &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.fragment == null
            }.getOrDefault(false)

    companion object {
        const val PROVIDER_ID = "openai_compatible"
        const val MAX_BASE_URL_LENGTH = 2048
        const val MAX_TOOL_CALL_ID_LENGTH = 256
        const val API_KEY_STORAGE_KEY = "ai.provider.openai_compatible.api_key"
    }
}
