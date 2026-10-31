package com.nexaflow.core.airuntime

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in checks against the real Gemini API.
 *
 * These tests hit the network and bill the account behind the key, so they are
 * skipped unless `GEMINI_API_KEY` (environment) or `-Dgemini.apiKey` is set.
 * `GEMINI_MODEL` pins a model; otherwise a chat-capable model is picked from the
 * account's model list. The key is not a task input, so pass `--rerun-tasks` to
 * stop Gradle from reusing a previous (skipped) result.
 */
class GeminiLiveProviderTest {

    private val apiKey: String? =
        System.getenv("GEMINI_API_KEY")?.trim()?.takeIf(String::isNotEmpty)
            ?: System.getProperty("gemini.apiKey")?.trim()?.takeIf(String::isNotEmpty)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun liveModelDiscoveryAndConnectionVerification() {
        val key = requireKey()
        runBlocking {
            val transport = JvmGeminiTransport()
            val model = pickModel(transport, key)
            val provider = provider(transport, key, model)

            val started = System.currentTimeMillis()
            val verification = provider.verifyConnection()
            val discovery = provider.listModels()
            val elapsed = System.currentTimeMillis() - started
            report(
                "verify model=$model success=${verification.success} " +
                    "failure=${verification.failure} http=${verification.httpStatus} " +
                    "latencyMs=${verification.latencyMs} models=${discovery.models.size} " +
                    "discoverySuccess=${discovery.success} elapsedMs=$elapsed"
            )

            assertTrue("verification failed: ${verification.failure}", verification.success)
            assertNull(verification.failure)
            assertTrue("model discovery failed: ${discovery.failure}", discovery.success)
            assertTrue(
                "the configured model is missing from discovery",
                discovery.models.any { it.id == model }
            )
        }
    }

    @Test
    fun liveGenerationReturnsTheAnswerAndATerminalEvent() {
        val key = requireKey()
        runBlocking {
            val model = pickModel(JvmGeminiTransport(), key)
            val transport = JvmGeminiTransport()
            val provider = provider(transport, key, model)

            val started = System.currentTimeMillis()
            val events = try {
                provider.stream(
                    AiProviderRequest(
                        conversationId = "gemini-live-text",
                        messages = listOf(
                            AiConversationMessage(
                                role = AiRole.USER,
                                text = "Reply with exactly this token and nothing else: NEXAFLOW_LIVE_OK"
                            )
                        ),
                        tools = emptyList(),
                        maxOutputCharacters = 4_096
                    )
                ).toList()
            } catch (failure: AiProviderRequestException) {
                report(
                    "generate error model=$model http=${failure.httpStatus} " +
                        "failure=${failure.failure} providerCode=${failure.providerCode} " +
                        "providerMessage=${failure.providerMessage}"
                )
                throw failure
            }
            val elapsed = System.currentTimeMillis() - started
            val deltas = events.filterIsInstance<AiProviderEvent.TextDelta>()
            val text = deltas.joinToString("") { it.text }
            report(
                "generate model=$model deltas=${deltas.size} chars=${text.length} " +
                    "terminal=${events.lastOrNull()} elapsedMs=$elapsed " +
                    "text=${text.take(120).replace('\n', ' ')}"
            )

            assertTrue("Gemini returned no text", text.isNotBlank())
            assertTrue(
                "the provider turn did not finish",
                events.lastOrNull() is AiProviderEvent.Finished
            )
        }
    }

    @Test
    fun liveToolCallsRoundTripThroughTheConversationEngine() {
        val key = requireKey()
        runBlocking {
            val transport = RecordingGeminiTransport(JvmGeminiTransport())
            val model = pickModel(transport, key)
            val provider = provider(transport, key, model)
            val registry = AiProviderRegistry(listOf(provider)).apply {
                updateRoutingPolicy(AiRoutingPolicy(mode = AiRoutingMode.CLOUD_ONLY))
            }
            val executor = TwoToolExecutor()
            val engine = AiConversationEngine(
                registry = registry,
                toolExecutor = executor,
                turnTimeoutMillis = 600_000L,
                maxToolIterations = 4
            )

            val started = System.currentTimeMillis()
            val events = engine.stream(
                conversationId = "gemini-live-tools",
                messages = listOf(
                    AiConversationMessage(
                        role = AiRole.USER,
                        text = "Call both tools nexaflow.get_status and nexaflow.list_tasks " +
                            "in the same turn, then reply with exactly TOOLS_DONE."
                    )
                ),
                outputCharacterLimit = 4_096
            ).toList()
            val elapsed = System.currentTimeMillis() - started
            val startedCalls = events.filterIsInstance<AiConversationEvent.ToolStarted>()
                .map { it.call.name }
            val failure = events.filterIsInstance<AiConversationEvent.Failed>().lastOrNull()
            val completed = events.filterIsInstance<AiConversationEvent.Completed>().lastOrNull()
            report(
                "tools model=$model calls=$startedCalls executed=${executor.executed} " +
                    "completed=${completed != null} failure=${failure?.code}/${failure?.reason} " +
                    "requests=${transport.bodies.size} elapsedMs=$elapsed"
            )

            assertNull("the tool run failed: ${failure?.code} (${failure?.reason})", failure)
            assertTrue("the model did not call any tool", startedCalls.isNotEmpty())
            assertTrue("the run produced no executed tool result", executor.executed.isNotEmpty())
            assertTrue("the conversation did not complete", completed != null)
            val finalText = completed!!.messages.lastOrNull { it.role == AiRole.ASSISTANT }
                ?.text.orEmpty()
            assertTrue("the final assistant answer is empty", finalText.isNotBlank())

            if (startedCalls.size >= 2) {
                // Every parallel result must be answered inside one Gemini turn.
                val responseTurns = transport.bodies.last()["contents"]!!.jsonArray
                    .map { it.jsonObject }
                    .filter { turn ->
                        turn["parts"]!!.jsonArray.all { part ->
                            part.jsonObject.containsKey("functionResponse")
                        }
                    }
                assertEquals(
                    "parallel tool results must share a single function-response turn",
                    1,
                    responseTurns.size
                )
                assertEquals(
                    "every parallel call must be answered",
                    startedCalls.size,
                    responseTurns.single()["parts"]!!.jsonArray.size
                )
            }
        }
    }

    private fun requireKey(): String {
        assumeTrue(
            "GEMINI_API_KEY is not set; live Gemini checks are skipped",
            apiKey != null
        )
        return apiKey!!
    }

    private fun provider(
        transport: GeminiNativeTransport,
        key: String,
        model: String
    ): GeminiNativeProvider = GeminiNativeProvider(
        transport = transport,
        apiKeyProvider = { key }
    ).apply {
        configure(GeminiNativeProviderConfig(enabled = true, modelId = model))
    }

    private suspend fun pickModel(transport: GeminiNativeTransport, key: String): String {
        System.getenv("GEMINI_MODEL")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val page = transport.getModelsPage(baseConfig(), key, null, 1_000)
        check(page.statusCode in 200..299) {
            "model discovery returned HTTP ${page.statusCode}"
        }
        val chatModels = json.parseToJsonElement(page.body).jsonObject["models"]?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val id = item["name"]?.jsonPrimitive?.contentOrNull
                    ?.removePrefix("models/")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                val methods = item["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                id to methods
            }
            .filter { (_, methods) -> "generateContent" in methods }
            .map { it.first }
        check(chatModels.isNotEmpty()) { "the account exposes no generateContent model" }
        return firstUsableModel(transport, key, candidateModels(chatModels))
    }

    /**
     * Gemini retires models for new accounts (`gemini-2.5-flash` answers 404
     * "no longer available"), so candidates are probed newest-first instead of
     * pinning a version that a given account may not be allowed to use.
     */
    private suspend fun firstUsableModel(
        transport: GeminiNativeTransport,
        key: String,
        candidates: List<String>
    ): String {
        var lastFailure = "no candidate"
        for (model in candidates) {
            val probe = try {
                transport.generateContent(
                    baseConfig().copy(modelId = model),
                    buildJsonObject {
                        putJsonArray("contents") {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    putJsonArray("parts") {
                                        add(buildJsonObject { put("text", "Reply OK") })
                                    }
                                }
                            )
                        }
                        putJsonObject("generationConfig") { put("maxOutputTokens", 16) }
                    },
                    key
                )
            } catch (failure: Exception) {
                lastFailure = "$model: ${failure.javaClass.simpleName}"
                continue
            }
            if (probe.statusCode in 200..299) return model
            lastFailure = "$model: HTTP ${probe.statusCode} ${probe.body.take(160)}"
            if (probe.statusCode == 404 || probe.body.contains("no longer available", true)) {
                continue
            }
            // Quota, demand or auth problems are the run's real result; report
            // them through the actual check instead of hunting for another model.
            return model
        }
        error("no usable Gemini model — $lastFailure")
    }

    private fun candidateModels(ids: List<String>): List<String> {
        val usable = ids.filterNot { id ->
            EXCLUDED_MODEL_MARKERS.any { marker -> id.contains(marker, ignoreCase = true) }
        }
        val flash = usable.filter { it.contains("flash") && !it.contains("lite") }
        val lite = usable.filter { it.contains("flash") && it.contains("lite") }
        val other = usable.filterNot { it.contains("flash") }
        return flash.sortedByDescending(::modelVersion) +
            other.sortedByDescending(::modelVersion) +
            lite.sortedByDescending(::modelVersion)
    }

    private fun modelVersion(id: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)")
            .find(id)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: 0.0

    private fun baseConfig() = GeminiNativeProviderConfig(
        enabled = true,
        modelId = "model-discovery"
    )

    private fun report(line: String) = println("GEMINI-LIVE $line")

    private class RecordingGeminiTransport(
        private val delegate: GeminiNativeTransport
    ) : GeminiNativeTransport {
        val bodies = mutableListOf<JsonObject>()

        override suspend fun generateContent(
            config: GeminiNativeProviderConfig,
            body: JsonObject,
            apiKey: String
        ): GeminiNativeTransportResponse {
            bodies += body
            return delegate.generateContent(config, body, apiKey)
        }

        override suspend fun getModels(
            config: GeminiNativeProviderConfig,
            apiKey: String
        ): GeminiNativeTransportResponse = delegate.getModels(config, apiKey)

        override suspend fun getModelsPage(
            config: GeminiNativeProviderConfig,
            apiKey: String,
            pageToken: String?,
            pageSize: Int
        ): GeminiNativeTransportResponse =
            delegate.getModelsPage(config, apiKey, pageToken, pageSize)
    }

    private class TwoToolExecutor : AiToolExecutor {
        val executed = mutableListOf<String>()

        override val tools: StateFlow<List<AiToolDefinition>> = MutableStateFlow(
            listOf(
                AiToolDefinition(
                    name = "nexaflow.get_status",
                    description = "Returns the current NexaFlow status summary.",
                    inputSchema = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                ),
                AiToolDefinition(
                    name = "nexaflow.list_tasks",
                    description = "Lists the NexaFlow tasks that are open.",
                    inputSchema = buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                )
            )
        )

        override suspend fun execute(call: AiToolCall): AiToolResult {
            executed += call.name
            return AiToolResult(
                callId = call.id,
                toolName = call.name,
                output = buildJsonObject {
                    put("status", "ok")
                    put("tasks", buildJsonArray { add(JsonPrimitive("sample-task")) })
                }
            )
        }
    }

    /** Minimal HTTPS transport so the live test does not depend on Android classes. */
    private class JvmGeminiTransport : GeminiNativeTransport {
        override suspend fun generateContent(
            config: GeminiNativeProviderConfig,
            body: JsonObject,
            apiKey: String
        ): GeminiNativeTransportResponse = withContext(Dispatchers.IO) {
            execute(
                method = "POST",
                endpoint = GeminiNativeEndpointPolicy.generateContentUri(config),
                apiKey = apiKey,
                payload = body.toString()
            )
        }

        override suspend fun getModels(
            config: GeminiNativeProviderConfig,
            apiKey: String
        ): GeminiNativeTransportResponse = getModelsPage(config, apiKey, null, 1_000)

        override suspend fun getModelsPage(
            config: GeminiNativeProviderConfig,
            apiKey: String,
            pageToken: String?,
            pageSize: Int
        ): GeminiNativeTransportResponse = withContext(Dispatchers.IO) {
            execute(
                method = "GET",
                endpoint = GeminiNativeEndpointPolicy.modelsPageUri(
                    config,
                    pageToken,
                    pageSize
                ),
                apiKey = apiKey,
                payload = null
            )
        }

        private fun execute(
            method: String,
            endpoint: URI,
            apiKey: String,
            payload: String?
        ): GeminiNativeTransportResponse {
            val connection = endpoint.toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 30_000
                // Free-tier flash models can queue for minutes under load.
                connection.readTimeout = 600_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("x-goog-api-key", apiKey)
                connection.setRequestProperty("Accept", "application/json")
                if (payload != null) {
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                    connection.outputStream.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return GeminiNativeTransportResponse(
                    statusCode = status,
                    body = body,
                    retryAfterMs = AiRetryAfter.parse(connection.getHeaderField("Retry-After"))
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val EXCLUDED_MODEL_MARKERS = listOf(
            "embedding", "aqa", "image", "tts", "transcribe", "audio",
            "computer-use", "robotics", "deep-research", "antigravity", "omni",
            "lyria", "nano-banana", "gemma", "native-audio", "live", "learnlm"
        )
    }
}
