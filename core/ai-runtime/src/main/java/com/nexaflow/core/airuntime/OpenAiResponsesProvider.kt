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

data class OpenAiResponsesProviderConfig(
    val id: String = "openai",
    val enabled: Boolean = false,
    val displayName: String = "OpenAI",
    val baseUrl: String = "https://api.openai.com/v1",
    val modelId: String = "",
    val reasoningEffort: String? = null,
    val gatewaySession: Boolean = false
)

data class OpenAiResponsesTransportResponse(
    val statusCode: Int,
    val body: String
)

interface OpenAiResponsesTransport {
    suspend fun postResponses(
        config: OpenAiResponsesProviderConfig,
        body: JsonObject,
        apiKey: String
    ): OpenAiResponsesTransportResponse

    suspend fun postResponsesWithHeaders(
        config: OpenAiResponsesProviderConfig,
        body: JsonObject,
        apiKey: String,
        headers: Map<String, String>
    ): OpenAiResponsesTransportResponse =
        postResponses(config, body, apiKey)

    suspend fun getModels(
        config: OpenAiResponsesProviderConfig,
        apiKey: String
    ): OpenAiResponsesTransportResponse
}

object OpenAiResponsesEndpointPolicy {
    fun responsesUri(config: OpenAiResponsesProviderConfig): URI =
        endpoint(config, "/responses")

    fun modelsUri(config: OpenAiResponsesProviderConfig): URI =
        endpoint(config, "/models")

    fun requireAddresses(addresses: List<java.net.InetAddress>) =
        EndpointSecurityPolicy.requireAddressScope(addresses, local = false)

    private fun endpoint(config: OpenAiResponsesProviderConfig, suffix: String): URI {
        val base = EndpointSecurityPolicy.validateBaseUri(
            raw = config.baseUrl.trim().trimEnd('/'),
            allowHttp = false,
            local = false,
            hasCredential = true
        )
        val path = base.path.orEmpty().trimEnd('/') + suffix
        return URI(base.scheme, null, base.host, base.port, path, null, null)
    }
}

class OpenAiResponsesProvider(
    private val transport: OpenAiResponsesTransport,
    private val apiKeyProvider: suspend () -> String?,
    private val callIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProviderAdapter {

    private val _descriptor = MutableStateFlow(descriptorFor(OpenAiResponsesProviderConfig()))
    override val descriptor: StateFlow<AiProviderDescriptor> = _descriptor.asStateFlow()

    @Volatile
    private var config = OpenAiResponsesProviderConfig()

    fun configure(value: OpenAiResponsesProviderConfig) {
        config = value.copy(
            id = value.id.trim().ifBlank { "openai" },
            displayName = value.displayName.trim().ifBlank { "OpenAI" },
            baseUrl = value.baseUrl.trim().trimEnd('/'),
            modelId = value.modelId.trim()
        )
        _descriptor.value = descriptorFor(config)
    }

    override suspend fun verifyConnection(): AiConnectionTestResult {
        val startedAt = System.nanoTime()
        val snapshot = config
        if (!isConfigured(snapshot)) {
            return connectionFailure(snapshot, startedAt, AiConnectionFailure.UNKNOWN)
        }
        val key = apiKeyProvider()?.takeIf(String::isNotBlank)
            ?: return connectionFailure(snapshot, startedAt, AiConnectionFailure.AUTHENTICATION)
        val discovery = listModelsWithKey(snapshot, key)
        val modelFound = discovery.success && discovery.models.any { it.id == snapshot.modelId }
        val classified = when {
            !discovery.success -> discovery.failure
            !modelFound -> AiConnectionFailure.MODEL_NOT_FOUND
            else -> null
        }
        return AiConnectionTestResult(
            success = classified == null,
            providerId = snapshot.id,
            dialect = AiApiDialect.OPENAI_RESPONSES,
            httpStatus = discovery.httpStatus,
            failure = classified,
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
        return listModelsWithKey(snapshot, key)
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
        val response = transport.postResponsesWithHeaders(
            snapshot,
            request.toBody(snapshot),
            key,
            headers
        )
        if (response.statusCode !in 200..299) {
            throw AiProviderRequestException.fromHttpStatus(response.statusCode)
        }
        val root = json.parseToJsonElement(response.body).jsonObject
        var emittedText = false
        root["output"]?.jsonArray.orEmpty().forEach { itemElement ->
            val item = itemElement.jsonObject
            when (item["type"]?.jsonPrimitive?.contentOrNull) {
                "message" -> item["content"]?.jsonArray.orEmpty().forEach { contentElement ->
                    val value = contentElement.jsonObject
                    if (value["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                        value["text"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf(String::isNotEmpty)
                            ?.let {
                                emit(AiProviderEvent.TextDelta(it))
                                emittedText = true
                            }
                    }
                }
                "function_call" -> {
                    val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val raw = item["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                    val arguments = json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())
                    if (name.isNotBlank()) {
                        emit(
                            AiProviderEvent.ToolCall(
                                AiToolCall(
                                    id = item["call_id"]?.jsonPrimitive?.contentOrNull
                                        ?.takeIf(String::isNotBlank) ?: callIdGenerator(),
                                    name = name,
                                    arguments = arguments
                                )
                            )
                        )
                    }
                }
            }
        }
        if (!emittedText) {
            root["output_text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotEmpty)
                ?.let { emit(AiProviderEvent.TextDelta(it)) }
        }
        emit(AiProviderEvent.Finished(root["status"]?.jsonPrimitive?.contentOrNull))
    }

    private suspend fun listModelsWithKey(
        snapshot: OpenAiResponsesProviderConfig,
        key: String
    ): AiModelDiscoveryResult {
        val response = try {
            transport.getModels(snapshot, key)
        } catch (failure: Throwable) {
            return AiModelDiscoveryResult(
                success = false,
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
            json.parseToJsonElement(response.body).jsonObject["data"]?.jsonArray
                ?.mapNotNull { item ->
                    item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
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

    private fun AiProviderRequest.toBody(snapshot: OpenAiResponsesProviderConfig) =
        buildJsonObject {
            put("model", snapshot.modelId)
            put("max_output_tokens", (maxOutputCharacters / CHARS_PER_TOKEN).coerceAtLeast(1))
            snapshot.reasoningEffort?.let { effort ->
                putJsonObject("reasoning") { put("effort", effort) }
            }
            put("input", buildJsonArray {
                messages.forEach { message ->
                    when (message.role) {
                        AiRole.TOOL -> add(
                            buildJsonObject {
                                put("type", "function_call_output")
                                put("call_id", message.toolCallId.orEmpty())
                                put("output", message.text)
                            }
                        )
                        else -> add(
                            buildJsonObject {
                                put(
                                    "role",
                                    when (message.role) {
                                        AiRole.SYSTEM -> "system"
                                        AiRole.USER -> "user"
                                        AiRole.ASSISTANT -> "assistant"
                                        AiRole.TOOL -> "user"
                                    }
                                )
                                putJsonArray("content") {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "type",
                                                if (message.role == AiRole.ASSISTANT) "output_text"
                                                else "input_text"
                                            )
                                            put("text", message.text)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            })
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            }
                        )
                    }
                }
            }
        }

    private fun isConfigured(value: OpenAiResponsesProviderConfig): Boolean =
        value.enabled &&
            value.modelId.isNotBlank() &&
            runCatching { OpenAiResponsesEndpointPolicy.responsesUri(value) }.isSuccess

    private fun descriptorFor(value: OpenAiResponsesProviderConfig) =
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
        structuredOutput = true,
        streaming = false,
        reasoning = true,
        local = false
    )

    private fun connectionFailure(
        snapshot: OpenAiResponsesProviderConfig,
        startedAt: Long,
        reason: AiConnectionFailure
    ) = AiConnectionTestResult(
        success = false,
        providerId = snapshot.id,
        dialect = AiApiDialect.OPENAI_RESPONSES,
        failure = reason,
        latencyMs = elapsedMillis(startedAt)
    )

    private companion object {
        const val MAX_MODELS = 512
        const val CHARS_PER_TOKEN = 4
    }
}
