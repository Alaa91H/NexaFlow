package com.nexaflow.core.airuntime

import com.nexaflow.core.common.EndpointSecurityPolicy
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

data class GeminiNativeProviderConfig(
    val id: String = "gemini",
    val enabled: Boolean = false,
    val displayName: String = "Gemini",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    val modelId: String = "",
    val gatewaySession: Boolean = false
)

data class GeminiNativeTransportResponse(
    val statusCode: Int,
    val body: String,
    val retryAfterMs: Long? = null
)

interface GeminiNativeTransport {
    suspend fun generateContent(
        config: GeminiNativeProviderConfig,
        body: JsonObject,
        apiKey: String
    ): GeminiNativeTransportResponse

    suspend fun generateContentWithHeaders(
        config: GeminiNativeProviderConfig,
        body: JsonObject,
        apiKey: String,
        headers: Map<String, String>
    ): GeminiNativeTransportResponse =
        generateContent(config, body, apiKey)

    suspend fun getModels(
        config: GeminiNativeProviderConfig,
        apiKey: String
    ): GeminiNativeTransportResponse

    /**
     * One page of the model catalog. The default keeps existing transports
     * source-compatible: it serves the single legacy page and terminates
     * pagination when a page token is requested.
     */
    suspend fun getModelsPage(
        config: GeminiNativeProviderConfig,
        apiKey: String,
        pageToken: String?,
        pageSize: Int
    ): GeminiNativeTransportResponse = if (pageToken == null) {
        getModels(config, apiKey)
    } else {
        GeminiNativeTransportResponse(200, """{"models":[]}""")
    }
}

object GeminiNativeEndpointPolicy {
    private val modelIdPattern = Regex("[A-Za-z0-9._:-]{1,256}")

    fun generateContentUri(config: GeminiNativeProviderConfig): URI {
        require(config.modelId.matches(modelIdPattern)) { "Gemini model id is invalid" }
        return endpoint(config, "/models/" + config.modelId + ":generateContent")
    }

    fun modelsUri(config: GeminiNativeProviderConfig): URI =
        endpoint(config, "/models")

    fun modelsPageUri(
        config: GeminiNativeProviderConfig,
        pageToken: String?,
        pageSize: Int
    ): URI {
        val base = modelsUri(config)
        val query = buildString {
            append("pageSize=").append(pageSize.coerceIn(1, MAX_PAGE_SIZE))
            pageToken?.takeIf(String::isNotBlank)?.let { token ->
                append("&pageToken=").append(java.net.URLEncoder.encode(token, "UTF-8"))
            }
        }
        return URI(base.scheme, null, base.host, base.port, base.path, query, null)
    }

    const val MAX_PAGE_SIZE = 1000

    fun requireAddresses(addresses: List<java.net.InetAddress>) =
        EndpointSecurityPolicy.requireAddressScope(addresses, local = false)

    private fun endpoint(config: GeminiNativeProviderConfig, suffix: String): URI {
        val base = EndpointSecurityPolicy.validateBaseUri(
            raw = config.baseUrl.trim().trimEnd('/'),
            allowHttp = false,
            local = false,
            hasCredential = true
        )
        return URI(
            base.scheme,
            null,
            base.host,
            base.port,
            base.path.orEmpty().trimEnd('/') + suffix,
            null,
            null
        )
    }
}

class GeminiNativeProvider(
    private val transport: GeminiNativeTransport,
    private val apiKeyProvider: suspend () -> String?,
    private val callIdGenerator: () -> String = { "gemini-" + UUID.randomUUID() },
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProviderAdapter {

    private val _descriptor = MutableStateFlow(descriptorFor(GeminiNativeProviderConfig()))
    override val descriptor: StateFlow<AiProviderDescriptor> = _descriptor.asStateFlow()

    @Volatile
    private var config = GeminiNativeProviderConfig()

    fun configure(value: GeminiNativeProviderConfig) {
        config = value.copy(
            id = value.id.trim().ifBlank { "gemini" },
            displayName = value.displayName.trim().ifBlank { "Gemini" },
            baseUrl = value.baseUrl.trim().trimEnd('/'),
            modelId = value.modelId.trim()
        )
        _descriptor.value = descriptorFor(config)
    }

    override suspend fun verifyConnection(): AiConnectionTestResult {
        val snapshot = config
        val startedAt = System.nanoTime()
        if (!isConfigured(snapshot)) {
            return connectionFailure(snapshot, startedAt, AiConnectionFailure.UNKNOWN)
        }
        val discovery = listModels()
        val modelFound = discovery.success && discovery.models.any { it.id == snapshot.modelId }
        if (!discovery.success || !modelFound) {
            val reason = if (!discovery.success) {
                discovery.failure
            } else {
                AiConnectionFailure.MODEL_NOT_FOUND
            }
            return AiConnectionTestResult(
                success = false,
                providerId = snapshot.id,
                dialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
                httpStatus = discovery.httpStatus,
                failure = reason,
                latencyMs = elapsedMillis(startedAt)
            )
        }

        val apiKey = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: return connectionFailure(snapshot, startedAt, AiConnectionFailure.AUTHENTICATION)
        val generation = try {
            transport.generateContent(snapshot, verificationRequest(), apiKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return connectionFailure(
                snapshot,
                startedAt,
                AiProviderFailureClassifier.fromThrowable(failure)
            )
        }
        val reason = if (generation.statusCode in 200..299) {
            null
        } else {
            AiProviderHttpFailure.classify(generation.statusCode, generation.body)
        }
        return AiConnectionTestResult(
            success = reason == null,
            providerId = snapshot.id,
            dialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            httpStatus = generation.statusCode,
            failure = reason,
            latencyMs = elapsedMillis(startedAt)
        )
    }

    private fun verificationRequest() = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") { add(buildJsonObject { put("text", "Reply with OK") }) }
            })
        }
        putJsonObject("generationConfig") { put("maxOutputTokens", 1) }
    }

    override suspend fun listModels(): AiModelDiscoveryResult {
        val snapshot = config
        if (!isConfigured(snapshot)) {
            return AiModelDiscoveryResult(false, failure = AiConnectionFailure.UNKNOWN)
        }
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: return AiModelDiscoveryResult(false, failure = AiConnectionFailure.AUTHENTICATION)
        val discovered = LinkedHashSet<String>()
        var pageToken: String? = null
        var pages = 0
        while (pages < MAX_MODEL_PAGES && discovered.size < MAX_MODELS) {
            pages += 1
            val response = try {
                transport.getModelsPage(snapshot, key, pageToken, PAGE_SIZE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return AiModelDiscoveryResult(
                    false,
                    models = discovered.map(::AiDiscoveredModel),
                    failure = AiProviderFailureClassifier.fromThrowable(failure)
                )
            }
            if (response.statusCode !in 200..299) {
                return AiModelDiscoveryResult(
                    false,
                    models = discovered.map(::AiDiscoveredModel),
                    httpStatus = response.statusCode,
                    failure = AiProviderHttpFailure.classify(
                        response.statusCode,
                        response.body
                    )
                )
            }
            val page = runCatching {
                json.parseToJsonElement(response.body).jsonObject
            }.getOrElse {
                return AiModelDiscoveryResult(
                    false,
                    models = discovered.map(::AiDiscoveredModel),
                    httpStatus = response.statusCode,
                    failure = AiConnectionFailure.INVALID_RESPONSE
                )
            }
            page["models"]?.jsonArray.orEmpty().forEach { item ->
                val id = runCatching {
                    item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                id?.removePrefix("models/")?.trim()?.takeIf(String::isNotEmpty)?.let {
                    discovered += it
                }
            }
            pageToken = page["nextPageToken"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
            if (pageToken == null) break
        }
        return AiModelDiscoveryResult(
            true,
            discovered.take(MAX_MODELS).map(::AiDiscoveredModel),
            httpStatus = 200
        )
    }

    override suspend fun discoverCapabilities(model: AiModelDescriptorV2): AiCapabilityResult {
        val verification = verifyConnection()
        return AiCapabilityResult(
            success = verification.success,
            modelId = model.id,
            capabilities = if (verification.success) capabilities() else AiProviderCapabilities(),
            failure = verification.failure
        )
    }

    override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> = flow {
        val snapshot = config
        require(isConfigured(snapshot)) { "Provider is not configured" }
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("API key is missing")
        val headers = if (snapshot.gatewaySession) {
            AiGatewaySessionPolicy.requestHeaders(request.conversationId)
        } else {
            emptyMap()
        }
        val response = transport.generateContentWithHeaders(
            snapshot,
            request.toBody(),
            key,
            headers
        )
        if (response.statusCode !in 200..299) {
            throw AiProviderHttpFailure.of(
                response.statusCode,
                response.body,
                response.retryAfterMs
            )
        }
        val root = json.parseToJsonElement(response.body).jsonObject
        val blockReason = root["promptFeedback"]?.jsonObject
            ?.get("blockReason")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
        if (blockReason != null) {
            throw blockedResponse("prompt_blocked_" + blockReason.lowercase(), blockReason)
        }
        val candidates = root["candidates"]?.jsonArray.orEmpty()
        if (candidates.isEmpty()) {
            val feedback = root["promptFeedback"]?.jsonObject
                ?.get("blockReasonMessage")?.jsonPrimitive?.contentOrNull
            throw blockedResponse("empty_candidates", feedback)
        }
        val candidate = candidates.firstOrNull()?.jsonObject
        val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
        val responseParts = candidate?.get("content")?.jsonObject?.get("parts")
        var producedText = false
        var producedCalls = false
        responseParts?.jsonArray.orEmpty().forEach { partElement ->
            val part = partElement.jsonObject
            part["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let {
                    producedText = true
                    emit(AiProviderEvent.TextDelta(it))
                }
            part["functionCall"]?.jsonObject?.let { call ->
                val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val args = call["args"] as? JsonObject ?: JsonObject(emptyMap())
                if (name.isNotBlank()) {
                    producedCalls = true
                    emit(
                        AiProviderEvent.ToolCall(
                            AiToolCall(callIdGenerator(), name, args)
                        )
                    )
                }
            }
        }
        if (!producedText && !producedCalls && finishReason in BLOCKING_FINISH_REASONS) {
            throw blockedResponse(
                "finish_" + finishReason.orEmpty().lowercase(),
                finishReason
            )
        }
        if (responseParts is kotlinx.serialization.json.JsonArray) {
            emit(
                AiProviderEvent.WireContext(
                    buildJsonObject { put(GEMINI_CONTENT_PARTS_CONTEXT, responseParts) }
                )
            )
        }
        emit(AiProviderEvent.Finished(finishReason))
    }

    private fun blockedResponse(code: String, detail: String?) = AiProviderRequestException(
        failure = AiConnectionFailure.INVALID_RESPONSE,
        httpStatus = 200,
        providerCode = AiProviderHttpFailure.sanitizeCode(code),
        providerMessage = AiProviderHttpFailure.sanitizeMessage(
            "Gemini returned no usable content" +
                (detail?.let { ": $it" } ?: "")
        )
    )

    private fun AiProviderRequest.toBody() = buildJsonObject {
        val systemText = messages
            .filter { it.role == AiRole.SYSTEM }
            .joinToString("\n") { it.text }
        if (systemText.isNotBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemText) })
                }
            }
        }
        putJsonArray("contents") {
            val conversational = messages.filter { it.role != AiRole.SYSTEM }
            var index = 0
            while (index < conversational.size) {
                val message = conversational[index]
                if (message.role == AiRole.TOOL) {
                    // Gemini requires one function-response turn per function-call turn:
                    // every parallel call must be answered by the same `contents` entry.
                    val parts = buildJsonArray {
                        while (index < conversational.size &&
                            conversational[index].role == AiRole.TOOL
                        ) {
                            val result = conversational[index]
                            add(
                                buildJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", result.toolName.orEmpty())
                                        put(
                                            "response",
                                            buildJsonObject { put("result", result.text) }
                                        )
                                    }
                                }
                            )
                            index += 1
                        }
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", parts)
                    })
                    continue
                }
                add(
                    buildJsonObject {
                        put("role", if (message.role == AiRole.ASSISTANT) "model" else "user")
                        putJsonArray("parts") {
                            val previousParts = if (message.role == AiRole.ASSISTANT) {
                                message.providerContext[GEMINI_CONTENT_PARTS_CONTEXT]
                                    as? kotlinx.serialization.json.JsonArray
                            } else {
                                null
                            }
                            if (previousParts != null) {
                                previousParts.forEach { add(it) }
                            } else {
                                if (message.text.isNotBlank()) {
                                    add(buildJsonObject { put("text", message.text) })
                                }
                                message.toolCalls.forEach { call ->
                                    add(
                                        buildJsonObject {
                                            putJsonObject("functionCall") {
                                                put("name", call.name)
                                                put("args", call.arguments)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                index += 1
            }
        }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { tool ->
                                add(
                                    buildJsonObject {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        // Gemini's OpenAPI `parameters` accepts only a narrow
                                        // subset and rejects several valid NexaFlow JSON Schema
                                        // keywords. Use the JSON Schema-specific field so the
                                        // complete agent tool catalog can be sent with chat.
                                        put(
                                            "parametersJsonSchema",
                                            GeminiFunctionSchema.normalize(tool.inputSchema)
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
            }
            putJsonObject("toolConfig") {
                putJsonObject("functionCallingConfig") { put("mode", "AUTO") }
            }
        }
    }

    private fun isConfigured(value: GeminiNativeProviderConfig): Boolean =
        value.enabled &&
            value.modelId.isNotBlank() &&
            runCatching { GeminiNativeEndpointPolicy.generateContentUri(value) }.isSuccess

    private fun descriptorFor(value: GeminiNativeProviderConfig) =
        AiProviderDescriptor(
            id = value.id,
            displayName = value.displayName,
            modelId = value.modelId.takeIf(String::isNotBlank),
            capabilities = capabilities(),
            available = isConfigured(value),
            detail = value.baseUrl.takeIf(String::isNotBlank)
        )

    private fun capabilities() = AiProviderCapabilities(
        toolCalling = true,
        structuredOutput = false,
        streaming = false,
        reasoning = false,
        local = false
    )

    private fun connectionFailure(
        snapshot: GeminiNativeProviderConfig,
        startedAt: Long,
        reason: AiConnectionFailure
    ) = AiConnectionTestResult(
        success = false,
        providerId = snapshot.id,
        dialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
        failure = reason,
        latencyMs = elapsedMillis(startedAt)
    )

    private companion object {
        const val MAX_MODELS = 512
        const val MAX_MODEL_PAGES = 10
        const val PAGE_SIZE = GeminiNativeEndpointPolicy.MAX_PAGE_SIZE
        val BLOCKING_FINISH_REASONS = setOf(
            "SAFETY",
            "RECITATION",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "SPII",
            "IMAGE_SAFETY"
        )
    }
}

private const val GEMINI_CONTENT_PARTS_CONTEXT = "gemini.content.parts"

/** Converts NexaFlow's Draft 2020-12 schemas to Gemini's supported function schema subset. */
internal object GeminiFunctionSchema {
    private val droppedKeywords = setOf(
        "${'$'}schema", "${'$'}id", "${'$'}anchor", "${'$'}comment", "${'$'}defs", "definitions",
        "title", "default", "examples", "deprecated", "readOnly", "writeOnly", "propertyNames"
    )

    fun normalize(schema: JsonObject): JsonObject {
        val definitions = (schema["${'$'}defs"] as? JsonObject) ?: JsonObject(emptyMap())
        return expand(schema, definitions, linkedSetOf()) as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun expand(
        element: JsonElement,
        definitions: JsonObject,
        resolving: MutableSet<String>
    ): JsonElement = when (element) {
        is JsonObject -> {
            val reference = element["${'$'}ref"]?.jsonPrimitive?.contentOrNull
            if (reference != null && reference.startsWith("#/${'$'}defs/")) {
                val name = reference.removePrefix("#/${'$'}defs/")
                if (name !in resolving) {
                    definitions[name]?.let {
                        resolving += name
                        val resolved = expand(it, definitions, resolving)
                        resolving -= name
                        return resolved
                    }
                }
            }
            val result = linkedMapOf<String, JsonElement>()
            element.forEach { (key, value) ->
                if (key !in droppedKeywords && key != "${'$'}ref") {
                    when (key) {
                        "const" -> result["enum"] = JsonArray(listOf(expand(value, definitions, resolving)))
                        "type" -> normalizeType(value)?.let { result["type"] = it }
                        "oneOf", "anyOf", "allOf" -> result[key] = expand(value, definitions, resolving)
                        "additionalProperties" -> if (value is JsonPrimitive && value.isString.not()) {
                            result[key] = value
                        }
                        else -> result[key] = expand(value, definitions, resolving)
                    }
                }
            }
            if (element["type"] is JsonArray &&
                element["type"]!!.jsonArray.any { it.jsonPrimitive.contentOrNull == "null" }
            ) {
                result["nullable"] = JsonPrimitive(true)
            }
            JsonObject(result)
        }
        is JsonArray -> JsonArray(element.map { expand(it, definitions, resolving) })
        else -> element
    }

    private fun normalizeType(value: JsonElement): JsonElement? {
        if (value !is JsonArray) return value
        val nonNullTypes = value.mapNotNull { it.jsonPrimitive.contentOrNull }
            .filterNot { it == "null" }
        return nonNullTypes.firstOrNull()?.let(::JsonPrimitive)
    }
}
