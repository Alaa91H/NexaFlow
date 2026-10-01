package com.nexaflow.core.airuntime

import com.nexaflow.core.common.EndpointSecurityPolicy

import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AnthropicMessagesProviderConfig(
    val id: String = "claude",
    val enabled: Boolean = false,
    val displayName: String = "Claude",
    val baseUrl: String = "",
    val modelId: String = "",
    val local: Boolean = false,
    val reasoningEffort: String? = null
)

data class AnthropicMessagesTransportResponse(val statusCode: Int, val body: String)

interface AnthropicMessagesTransport {
    suspend fun postMessages(
        config: AnthropicMessagesProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): AnthropicMessagesTransportResponse

    suspend fun getModels(
        config: AnthropicMessagesProviderConfig,
        apiKey: String?
    ): AnthropicMessagesTransportResponse
}

object AnthropicEndpointPolicy {
    fun requireAddresses(addresses: List<java.net.InetAddress>) =
        EndpointSecurityPolicy.requireAddressScope(addresses, local = false)

    fun messagesUri(config: AnthropicMessagesProviderConfig, hasApiKey: Boolean): URI =
        endpoint(config, hasApiKey, "/messages")

    fun modelsUri(config: AnthropicMessagesProviderConfig, hasApiKey: Boolean): URI {
        val base = endpoint(config, hasApiKey, "/models")
        return URI(base.scheme, null, base.host, base.port, base.path, "limit=1000", null)
    }

    private fun endpoint(
        config: AnthropicMessagesProviderConfig,
        hasApiKey: Boolean,
        suffix: String
    ): URI {
        require(config.baseUrl.length in 8..OpenAiCompatibleProvider.MAX_BASE_URL_LENGTH)
        val base = EndpointSecurityPolicy.validateBaseUri(
            raw = config.baseUrl.trim().trimEnd('/'),
            allowHttp = false,
            local = false,
            hasCredential = hasApiKey,
        )
        require(!config.local) { "Anthropic native API profiles are remote-only" }
        return URI(base.scheme, null, base.host, base.port,
            base.path.trimEnd('/') + suffix, null, null)
    }
}

class AnthropicMessagesProvider(
    private val transport: AnthropicMessagesTransport,
    private val apiKeyProvider: suspend () -> String?,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiModelProvider {
    private val _descriptor = MutableStateFlow(descriptorFor(AnthropicMessagesProviderConfig()))
    override val descriptor: StateFlow<AiProviderDescriptor> = _descriptor.asStateFlow()

    @Volatile
    private var config = AnthropicMessagesProviderConfig()

    fun configure(value: AnthropicMessagesProviderConfig) {
        val normalized = value.copy(
            id = value.id.trim(),
            displayName = value.displayName.trim(),
            baseUrl = value.baseUrl.trim().trimEnd('/'),
            modelId = value.modelId.trim()
        )
        config = normalized
        _descriptor.value = descriptorFor(normalized)
    }

    suspend fun verify(): Boolean {
        val snapshot = config
        val result = discoverModels()
        val valid = result.success && result.models.any { it == snapshot.modelId }
        _descriptor.value = descriptorFor(snapshot, available = valid)
        return valid
    }

    data class ModelDiscoveryResult(
        val success: Boolean,
        val models: List<String> = emptyList(),
        val statusCode: Int? = null
    )

    suspend fun discoverModels(): ModelDiscoveryResult {
        val snapshot = config
        if (!isConfigured(snapshot)) return ModelDiscoveryResult(success = false)
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: return ModelDiscoveryResult(success = false)
        val response = runCatching { transport.getModels(snapshot, key) }.getOrNull()
            ?: return ModelDiscoveryResult(success = false)
        if (response.statusCode !in 200..299) {
            return ModelDiscoveryResult(success = false, statusCode = response.statusCode)
        }
        val models = runCatching {
            json.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim() }
                ?.filter(String::isNotEmpty)
                ?.distinct()
                ?.take(MAX_MODELS)
                .orEmpty()
        }.getOrElse {
            return ModelDiscoveryResult(success = false, statusCode = response.statusCode)
        }
        return ModelDiscoveryResult(true, models, response.statusCode)
    }

    override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> = flow {
        val snapshot = config
        require(isConfigured(snapshot)) { "Provider is not configured" }
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("API key is missing")
        val response = transport.postMessages(snapshot, request.toBody(snapshot), key)
        require(response.statusCode in 200..299) { "Provider returned HTTP ${response.statusCode}" }
        val root = json.parseToJsonElement(response.body).jsonObject
        root["content"]?.jsonArray.orEmpty().forEach { block ->
            val value = block.jsonObject
            when (value["type"]?.jsonPrimitive?.content) {
                "text" -> value["text"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotEmpty)?.let { emit(AiProviderEvent.TextDelta(it)) }
                "tool_use" -> {
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val id = value["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val arguments = value["input"] as? JsonObject ?: JsonObject(emptyMap())
                    require(name.length in 1..MAX_TOOL_NAME_LENGTH && id.length in 1..MAX_TOOL_ID_LENGTH)
                    emit(AiProviderEvent.ToolCall(AiToolCall(id, name, arguments)))
                }
            }
        }
        emit(AiProviderEvent.Finished(
            root["stop_reason"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        ))
    }

    private fun AiProviderRequest.toBody(snapshot: AnthropicMessagesProviderConfig) =
        buildJsonObject {
            put("model", snapshot.modelId)
            put("max_tokens", (maxOutputCharacters / CHARS_PER_TOKEN).coerceIn(1, MAX_OUTPUT_TOKENS))
            snapshot.reasoningEffort?.let { effort ->
                put("output_config", buildJsonObject { put("effort", effort) })
            }
            val system = messages.filter { it.role == AiRole.SYSTEM }
                .joinToString("\n") { it.text }
            if (system.isNotBlank()) put("system", system.take(MAX_SYSTEM_CHARACTERS))
            put("messages", buildJsonArray {
                messages.filter { it.role != AiRole.SYSTEM }.forEach { message ->
                    when (message.role) {
                        AiRole.USER -> add(buildJsonObject {
                            put("role", "user")
                            put("content", message.text.take(MAX_MESSAGE_CHARACTERS))
                        })
                        AiRole.ASSISTANT -> add(buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray {
                                if (message.text.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", message.text.take(MAX_MESSAGE_CHARACTERS))
                                    })
                                }
                                message.toolCalls.take(MAX_TOOLS).forEach { call ->
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", call.id.take(MAX_TOOL_ID_LENGTH))
                                        put("name", call.name.take(MAX_TOOL_NAME_LENGTH))
                                        put("input", call.arguments)
                                    })
                                }
                            })
                        })
                        AiRole.TOOL -> add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", message.toolCallId.orEmpty())
                                    put("content", message.text.take(MAX_MESSAGE_CHARACTERS))
                                    put("is_error", message.isError)
                                })
                            })
                        })
                        AiRole.SYSTEM -> Unit
                    }
                }
            })
            if (tools.isNotEmpty()) put("tools", buildJsonArray {
                tools.take(MAX_TOOLS).forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name.take(MAX_TOOL_NAME_LENGTH))
                        put("description", tool.description.take(MAX_DESCRIPTION_CHARACTERS))
                        put("input_schema", tool.inputSchema)
                    })
                }
            })
        }

    private fun isConfigured(value: AnthropicMessagesProviderConfig): Boolean =
        value.enabled && value.id.length in 1..MAX_ID_LENGTH && value.modelId.isNotBlank() &&
            runCatching { AnthropicEndpointPolicy.messagesUri(value, true) }.isSuccess

    private fun descriptorFor(
        value: AnthropicMessagesProviderConfig,
        available: Boolean = isConfigured(value)
    ) = AiProviderDescriptor(
        id = value.id,
        displayName = value.displayName.ifBlank { "Claude" },
        modelId = value.modelId.takeIf(String::isNotBlank),
        capabilities = AiProviderCapabilities(
            toolCalling = true,
            structuredOutput = false,
            streaming = false,
            local = false
        ),
        available = available,
        detail = value.baseUrl.takeIf(String::isNotBlank)
    )

    private companion object {
        const val MAX_ID_LENGTH = 128
        const val MAX_MODELS = 1000
        const val MAX_TOOLS = 64
        const val MAX_TOOL_NAME_LENGTH = 128
        const val MAX_TOOL_ID_LENGTH = 256
        const val MAX_DESCRIPTION_CHARACTERS = 4096
        const val MAX_SYSTEM_CHARACTERS = 65_536
        const val MAX_MESSAGE_CHARACTERS = 65_536
        const val MAX_OUTPUT_TOKENS = 32_768
        const val CHARS_PER_TOKEN = 4
    }
}
