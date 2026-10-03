package com.nexaflow.core.airuntime

import com.nexaflow.core.common.EndpointSecurityPolicy
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    val body: String
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
}

object GeminiNativeEndpointPolicy {
    private val modelIdPattern = Regex("[A-Za-z0-9._:-]{1,256}")

    fun generateContentUri(config: GeminiNativeProviderConfig): URI {
        require(config.modelId.matches(modelIdPattern)) { "Gemini model id is invalid" }
        return endpoint(config, "/models/" + config.modelId + ":generateContent")
    }

    fun modelsUri(config: GeminiNativeProviderConfig): URI =
        endpoint(config, "/models")

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
        val reason = when {
            !discovery.success -> discovery.failure
            !modelFound -> AiConnectionFailure.MODEL_NOT_FOUND
            else -> null
        }
        return AiConnectionTestResult(
            success = reason == null,
            providerId = snapshot.id,
            dialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            httpStatus = discovery.httpStatus,
            failure = reason,
            latencyMs = elapsedMillis(startedAt)
        )
    }

    override suspend fun listModels(): AiModelDiscoveryResult {
        val snapshot = config
        if (!isConfigured(snapshot)) {
            return AiModelDiscoveryResult(false, failure = AiConnectionFailure.UNKNOWN)
        }
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: return AiModelDiscoveryResult(false, failure = AiConnectionFailure.AUTHENTICATION)
        val response = try {
            transport.getModels(snapshot, key)
        } catch (failure: Throwable) {
            return AiModelDiscoveryResult(
                false,
                failure = AiProviderFailureClassifier.fromThrowable(failure)
            )
        }
        if (response.statusCode !in 200..299) {
            return AiModelDiscoveryResult(
                false,
                httpStatus = response.statusCode,
                failure = AiProviderFailureClassifier.fromHttpStatus(response.statusCode)
            )
        }
        val models = runCatching {
            json.parseToJsonElement(response.body).jsonObject["models"]?.jsonArray
                ?.mapNotNull { item ->
                    item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                        ?.removePrefix("models/")
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }
                ?.distinct()
                ?.take(MAX_MODELS)
                .orEmpty()
        }.getOrElse {
            return AiModelDiscoveryResult(
                false,
                httpStatus = response.statusCode,
                failure = AiConnectionFailure.INVALID_RESPONSE
            )
        }
        return AiModelDiscoveryResult(
            true,
            models.map(::AiDiscoveredModel),
            httpStatus = response.statusCode
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
            throw AiProviderRequestException.fromHttpStatus(response.statusCode)
        }
        val root = json.parseToJsonElement(response.body).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
            .forEach { partElement ->
                val part = partElement.jsonObject
                part["text"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotEmpty)
                    ?.let { emit(AiProviderEvent.TextDelta(it)) }
                part["functionCall"]?.jsonObject?.let { call ->
                    val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = call["args"] as? JsonObject ?: JsonObject(emptyMap())
                    if (name.isNotBlank()) {
                        emit(
                            AiProviderEvent.ToolCall(
                                AiToolCall(callIdGenerator(), name, args)
                            )
                        )
                    }
                }
            }
        val responseParts = candidate?.get("content")?.jsonObject?.get("parts")
        if (responseParts is kotlinx.serialization.json.JsonArray) {
            emit(
                AiProviderEvent.WireContext(
                    buildJsonObject { put(GEMINI_CONTENT_PARTS_CONTEXT, responseParts) }
                )
            )
        }
        emit(
            AiProviderEvent.Finished(
                candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
            )
        )
    }

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
            messages.filter { it.role != AiRole.SYSTEM }.forEach { message ->
                add(
                    buildJsonObject {
                        put("role", if (message.role == AiRole.ASSISTANT) "model" else "user")
                        putJsonArray("parts") {
                            if (message.role == AiRole.TOOL) {
                                add(
                                    buildJsonObject {
                                        putJsonObject("functionResponse") {
                                            put("name", message.toolName.orEmpty())
                                            put(
                                                "response",
                                                buildJsonObject { put("result", message.text) }
                                            )
                                        }
                                    }
                                )
                            } else {
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
                    }
                )
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
                                        put("parameters", tool.inputSchema)
                                    }
                                )
                            }
                        }
                    }
                )
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
    }
}

private const val GEMINI_CONTENT_PARTS_CONTEXT = "gemini.content.parts"
