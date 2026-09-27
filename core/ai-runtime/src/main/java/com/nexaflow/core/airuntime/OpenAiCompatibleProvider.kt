package com.nexaflow.core.airuntime

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

data class OpenAiProviderProbeResult(
    val success: Boolean,
    val statusCode: Int? = null,
    val capabilities: AiProviderCapabilities = AiProviderCapabilities()
)

data class OpenAiModelInfo(
    val id: String,
    val ownedBy: String? = null,
    val contextTokens: Int? = null
)

data class OpenAiModelDiscoveryResult(
    val success: Boolean,
    val models: List<OpenAiModelInfo> = emptyList(),
    val statusCode: Int? = null
)

interface OpenAiCompatibleTransport {
    suspend fun postChatCompletions(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse

    suspend fun getModels(
        config: OpenAiCompatibleProviderConfig,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse =
        OpenAiCompatibleTransportResponse(
            statusCode = 501,
            body = """{"error":"models_not_supported"}"""
        )

    /**
     * Emits raw JSON completion chunks. Implementations should stream when
     * possible; the default preserves compatibility with buffered transports.
     */
    fun streamChatCompletions(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): Flow<String> = flow {
        val response = postChatCompletions(config, body, apiKey)
        if (response.statusCode !in 200..299) {
            error("Provider returned HTTP ${response.statusCode}")
        }
        emit(response.body)
    }
}

class OpenAiCompatibleProvider(
    private val transport: OpenAiCompatibleTransport,
    private val apiKeyProvider: suspend () -> String? = { null },
    private val fallbackToolCallIdGenerator: () -> String = {
        "fallback-" + UUID.randomUUID()
    },
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

    suspend fun discoverModels(): OpenAiModelDiscoveryResult {
        val snapshot = config
        if (!isConfigured(snapshot)) {
            return OpenAiModelDiscoveryResult(success = false)
        }
        val response = runCatching {
            transport.getModels(
                config = snapshot,
                apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
            )
        }.getOrElse {
            return OpenAiModelDiscoveryResult(success = false)
        }
        if (response.statusCode !in 200..299) {
            return OpenAiModelDiscoveryResult(
                success = false,
                statusCode = response.statusCode
            )
        }

        val models = runCatching {
            json.parseToJsonElement(response.body)
                .jsonObject["data"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull(::parseModelInfo)
                .distinctBy(OpenAiModelInfo::id)
                .sortedBy { it.id.lowercase() }
                .take(MAX_DISCOVERED_MODELS)
        }.getOrElse {
            return OpenAiModelDiscoveryResult(
                success = false,
                statusCode = response.statusCode
            )
        }
        return OpenAiModelDiscoveryResult(
            success = true,
            models = models,
            statusCode = response.statusCode
        )
    }

    private fun parseModelInfo(element: kotlinx.serialization.json.JsonElement): OpenAiModelInfo? {
        val value = element as? JsonObject ?: return null
        val id = value["id"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.length in 1..MAX_MODEL_ID_LENGTH }
            ?: return null
        val ownedBy = value["owned_by"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.length in 1..MAX_MODEL_OWNER_LENGTH }
        val context = listOfNotNull(
            value["max_context_length"]?.jsonPrimitive?.intOrNull,
            value["context_length"]?.jsonPrimitive?.intOrNull,
            (value["meta"] as? JsonObject)
                ?.get("n_ctx_train")
                ?.jsonPrimitive
                ?.intOrNull,
            (value["meta"] as? JsonObject)
                ?.get("context_length")
                ?.jsonPrimitive
                ?.intOrNull
        ).firstOrNull { it > 0 }
        return OpenAiModelInfo(
            id = id,
            ownedBy = ownedBy,
            contextTokens = context
        )
    }

    suspend fun probe(): OpenAiProviderProbeResult {
        val snapshot = config
        if (!isConfigured(snapshot)) {
            return OpenAiProviderProbeResult(success = false)
        }

        val apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
        val basic = runCatching {
            transport.postChatCompletions(
                config = snapshot,
                body = basicProbeBody(snapshot.modelId),
                apiKey = apiKey
            )
        }.getOrElse {
            return OpenAiProviderProbeResult(success = false)
        }
        val validBody = basic.statusCode in 200..299 &&
            runCatching {
                json.parseToJsonElement(basic.body)
                    .jsonObject["choices"]
                    ?.jsonArray
                    ?.isNotEmpty() == true
            }.getOrDefault(false)
        if (!validBody) {
            return OpenAiProviderProbeResult(
                success = false,
                statusCode = basic.statusCode
            )
        }

        val capabilities = AiProviderCapabilities(
            toolCalling = probeToolCalling(snapshot, apiKey),
            structuredOutput = probeStructuredOutput(snapshot, apiKey),
            streaming = probeStreaming(snapshot, apiKey),
            local = snapshot.local
        )
        _descriptor.value = descriptorFor(snapshot, capabilities)
        return OpenAiProviderProbeResult(
            success = true,
            statusCode = basic.statusCode,
            capabilities = capabilities
        )
    }

    private fun basicProbeBody(modelId: String) = buildJsonObject {
        put("model", modelId)
        put("stream", false)
        put("max_tokens", 8)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", "Reply with OK.")
                }
            )
        }
    }

    private suspend fun probeToolCalling(
        snapshot: OpenAiCompatibleProviderConfig,
        apiKey: String?
    ): Boolean = runCatching {
        val response = transport.postChatCompletions(
            config = snapshot,
            body = buildJsonObject {
                put("model", snapshot.modelId)
                put("stream", false)
                put("max_tokens", 16)
                putJsonArray("messages") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "Call the supplied probe function.")
                        }
                    )
                }
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", PROBE_TOOL_NAME)
                                put("description", "Capability probe")
                                putJsonObject("parameters") {
                                    put("type", "object")
                                    putJsonObject("properties") {}
                                    put("additionalProperties", false)
                                }
                            }
                        }
                    )
                }
                putJsonObject("tool_choice") {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", PROBE_TOOL_NAME)
                    }
                }
            },
            apiKey = apiKey
        )
        if (response.statusCode !in 200..299) return@runCatching false
        val message = json.parseToJsonElement(response.body)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            as? JsonObject
            ?: return@runCatching false
        message["tool_calls"]
            ?.takeUnless { it is JsonNull }
            ?.jsonArray
            ?.any { item ->
                item.jsonObject["function"]
                    ?.jsonObject
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.contentOrNull == PROBE_TOOL_NAME
            } == true
    }.getOrDefault(false)

    private suspend fun probeStructuredOutput(
        snapshot: OpenAiCompatibleProviderConfig,
        apiKey: String?
    ): Boolean = runCatching {
        val response = transport.postChatCompletions(
            config = snapshot,
            body = buildJsonObject {
                put("model", snapshot.modelId)
                put("stream", false)
                put("max_tokens", 16)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
                putJsonArray("messages") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "Return a JSON object with ok=true.")
                        }
                    )
                }
            },
            apiKey = apiKey
        )
        if (response.statusCode !in 200..299) return@runCatching false
        val content = json.parseToJsonElement(response.body)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return@runCatching false
        json.parseToJsonElement(content) is JsonObject
    }.getOrDefault(false)

    private suspend fun probeStreaming(
        snapshot: OpenAiCompatibleProviderConfig,
        apiKey: String?
    ): Boolean = runCatching {
        var sawDelta = false
        transport.streamChatCompletions(
            config = snapshot,
            body = buildJsonObject {
                put("model", snapshot.modelId)
                put("stream", true)
                put("max_tokens", 8)
                putJsonArray("messages") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "Reply with OK.")
                        }
                    )
                }
            },
            apiKey = apiKey
        ).collect { raw ->
            if (raw.isBlank() || raw.trim() == "[DONE]") return@collect
            val choice = json.parseToJsonElement(raw)
                .jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
            if (choice?.get("delta") is JsonObject) {
                sawDelta = true
            }
        }
        sawDelta
    }.getOrDefault(false)

    override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> = flow {
        val snapshot = config
        check(isConfigured(snapshot)) {
            "OpenAI-compatible provider is not configured"
        }

        val capabilities = _descriptor.value.capabilities
        if (
            request.tools.isNotEmpty() &&
            !capabilities.toolCalling &&
            capabilities.structuredOutput
        ) {
            emitStructuredToolFallback(
                request = request,
                snapshot = snapshot,
                apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
            )
            return@flow
        }

        val effectiveRequest = if (
            request.tools.isNotEmpty() && !capabilities.toolCalling
        ) {
            request.copy(tools = emptyList())
        } else {
            request
        }

        val toolCalls = linkedMapOf<Int, ToolCallAccumulator>()
        var finishReason: String? = null
        transport.streamChatCompletions(
            config = snapshot,
            body = effectiveRequest.toChatCompletionBody(
                snapshot.modelId,
                streaming = true
            ),
            apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
        ).collect { raw ->
            if (raw.isBlank() || raw.trim() == "[DONE]") {
                return@collect
            }

            val root = json.parseToJsonElement(raw).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@collect
            finishReason = choice["finish_reason"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?: finishReason

            val message = choice["message"] as? JsonObject
            if (message != null) {
                emitMessage(message)
                message["tool_calls"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonArray
                    ?.forEachIndexed { index, item ->
                        toolCalls[index] = completeAccumulator(item.jsonObject)
                    }
                return@collect
            }

            val delta = choice["delta"] as? JsonObject ?: return@collect
            delta["content"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let { emit(AiProviderEvent.TextDelta(it)) }

            delta["tool_calls"]
                ?.takeUnless { it is JsonNull }
                ?.jsonArray
                ?.forEachIndexed { fallbackIndex, item ->
                    val part = item.jsonObject
                    val index = part["index"]?.jsonPrimitive?.intOrNull
                        ?: fallbackIndex
                    require(index in 0 until MAX_STREAM_TOOL_CALLS) {
                        "Tool call index exceeds limit"
                    }
                    toolCalls.getOrPut(index, ::ToolCallAccumulator)
                        .append(part)
                }
        }

        toolCalls.toSortedMap().values.forEach { accumulator ->
            emit(AiProviderEvent.ToolCall(accumulator.toToolCall()))
        }
        emit(AiProviderEvent.Finished(finishReason))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AiProviderEvent>
        .emitStructuredToolFallback(
            request: AiProviderRequest,
            snapshot: OpenAiCompatibleProviderConfig,
            apiKey: String?
        ) {
            val response = transport.postChatCompletions(
                config = snapshot,
                body = request.toStructuredFallbackBody(snapshot.modelId),
                apiKey = apiKey
            )
            require(response.statusCode in 200..299) {
                "Provider returned HTTP ${response.statusCode}"
            }
            val message = json.parseToJsonElement(response.body)
                .jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?: throw IllegalArgumentException("Provider response has no message")
            val content = message["content"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?: throw IllegalArgumentException("Structured response is empty")
            val envelope = json.parseToJsonElement(content) as? JsonObject
                ?: throw IllegalArgumentException("Structured response must be an object")

            val toolName = envelope["tool"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
            if (!toolName.isNullOrBlank()) {
                val definition = request.tools.firstOrNull { it.name == toolName }
                    ?: throw IllegalArgumentException("Structured response selected an unknown tool")
                val arguments = envelope["arguments"] as? JsonObject
                    ?: throw IllegalArgumentException("Structured tool arguments must be an object")
                require(
                    arguments.toString().length <=
                        AiConversationEngine.MAX_TOOL_ARGUMENT_CHARACTERS
                ) {
                    "Tool arguments exceed limit"
                }
                val callId = fallbackToolCallIdGenerator()
                    .take(MAX_TOOL_CALL_ID_LENGTH)
                    .takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException(
                        "Structured fallback tool-call id is invalid"
                    )
                emit(
                    AiProviderEvent.ToolCall(
                        AiToolCall(
                            id = callId,
                            name = definition.name,
                            arguments = arguments
                        )
                    )
                )
                emit(AiProviderEvent.Finished("structured_tool_call"))
                return
            }

            val text = envelope["message"]
                ?.takeUnless { it is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException(
                    "Structured response must contain tool or message"
                )
            emit(AiProviderEvent.TextDelta(text))
            emit(AiProviderEvent.Finished("structured_message"))
        }

    private fun AiProviderRequest.toStructuredFallbackBody(
        modelId: String
    ): JsonObject {
        val instruction = structuredFallbackInstruction()
        require(instruction.length <= MAX_FALLBACK_INSTRUCTION_CHARACTERS) {
            "Structured fallback tool catalog exceeds limit"
        }
        return buildJsonObject {
            put("model", modelId)
            put("stream", false)
            putJsonObject("response_format") {
                put("type", "json_object")
            }
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", instruction)
                    }
                )
                messages.forEach { add(it.toOpenAiMessage()) }
            }
        }
    }

    private fun AiProviderRequest.structuredFallbackInstruction(): String {
        val catalog = buildJsonArray {
            tools.forEach { tool ->
                add(
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.inputSchema)
                    }
                )
            }
        }
        return buildString {
            append(
                "Return exactly one JSON object. To call a tool use " +
                    "{\"tool\":\"tool.name\",\"arguments\":{...}}. "
            )
            append(
                "To answer without a tool use {\"message\":\"text\"}. " +
                    "Never invent a tool name. Available tools: "
            )
            append(catalog)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AiProviderEvent>.emitMessage(
        message: JsonObject
    ) {
        message["content"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf(String::isNotEmpty)
            ?.let { emit(AiProviderEvent.TextDelta(it)) }
    }

    private fun AiProviderRequest.toChatCompletionBody(
        modelId: String,
        streaming: Boolean
    ): JsonObject = buildJsonObject {
        put("model", modelId)
        put("stream", streaming)
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

    private fun descriptorFor(
        value: OpenAiCompatibleProviderConfig,
        capabilities: AiProviderCapabilities = AiProviderCapabilities(
            toolCalling = true,
            structuredOutput = true,
            streaming = true,
            local = value.local
        )
    ) = AiProviderDescriptor(
            id = PROVIDER_ID,
            displayName = value.displayName.ifBlank { "OpenAI-compatible" },
            modelId = value.modelId.takeIf(String::isNotBlank),
            capabilities = capabilities,
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

    private fun completeAccumulator(value: JsonObject): ToolCallAccumulator =
        ToolCallAccumulator().apply { append(value) }

    private inner class ToolCallAccumulator {
        private var id = ""
        private var name = ""
        private val arguments = StringBuilder()

        fun append(part: JsonObject) {
            part["id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let { fragment ->
                    if (id.isEmpty()) id = fragment else if (id != fragment) id += fragment
                }
            val function = part["function"] as? JsonObject ?: return
            function["name"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let { fragment ->
                    if (name.isEmpty()) name = fragment else if (name != fragment) name += fragment
                }
            function["arguments"]?.jsonPrimitive?.contentOrNull?.let { fragment ->
                require(
                    arguments.length + fragment.length <=
                        AiConversationEngine.MAX_TOOL_ARGUMENT_CHARACTERS
                ) {
                    "Tool arguments exceed limit"
                }
                arguments.append(fragment)
            }
        }

        fun toToolCall(): AiToolCall {
            require(id.length in 1..MAX_TOOL_CALL_ID_LENGTH) {
                "Tool call id is invalid"
            }
            require(name.length in 1..AiConversationEngine.MAX_TOOL_NAME_LENGTH) {
                "Tool call name is invalid"
            }
            val rawArguments = arguments.toString().ifBlank { "{}" }
            val parsed = json.parseToJsonElement(rawArguments) as? JsonObject
                ?: throw IllegalArgumentException("Tool arguments must be an object")
            return AiToolCall(id = id, name = name, arguments = parsed)
        }

    }

    companion object {
        const val PROVIDER_ID = "openai_compatible"
        const val MAX_BASE_URL_LENGTH = 2048
        const val MAX_TOOL_CALL_ID_LENGTH = 256
        const val API_KEY_STORAGE_KEY = "ai.provider.openai_compatible.api_key"
        private const val MAX_STREAM_TOOL_CALLS = 16
        private const val MAX_DISCOVERED_MODELS = 256
        private const val MAX_MODEL_ID_LENGTH = 256
        private const val MAX_MODEL_OWNER_LENGTH = 256
        private const val MAX_FALLBACK_INSTRUCTION_CHARACTERS = 65_536
        private const val PROBE_TOOL_NAME = "nexaflow_capability_probe"
    }
}
