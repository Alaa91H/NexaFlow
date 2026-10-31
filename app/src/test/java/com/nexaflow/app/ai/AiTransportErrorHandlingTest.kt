package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiConnectionFailure
import com.nexaflow.core.airuntime.AiProviderRequestException
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.airuntime.GeminiNativeProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises the Android transports without a network or a TLS trust override. */
class AiTransportErrorHandlingTest {

    private data class FakeResponse(
        val status: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val contentType: String? = "application/json"
    )

    private var response: FakeResponse? = null
    private var lastConnection: FakeHttpsConnection? = null

    private val gemini = GeminiNativeProviderConfig(
        enabled = true,
        modelId = "gemini-3.6-flash"
    )
    private val anthropic = AnthropicMessagesProviderConfig(
        enabled = true,
        modelId = "claude-sonnet-4-5",
        baseUrl = "https://api.anthropic.com"
    )
    private val responses = OpenAiResponsesProviderConfig(
        enabled = true,
        modelId = "gpt-5.1"
    )
    private val compatible = OpenAiCompatibleProviderConfig(
        enabled = true,
        baseUrl = "https://api.openai.com/v1",
        modelId = "gpt-5.1",
        local = false
    )

    @Before
    fun installFakeConnection() {
        AiHttpConnections.opener = { uri ->
            val spec = requireNotNull(response) { "no fake response configured" }
            FakeHttpsConnection(uri, spec.status, spec.body, spec.headers, spec.contentType)
                .also { lastConnection = it }
        }
    }

    @After
    fun restoreConnection() {
        response = null
        lastConnection = null
        AiHttpConnections.opener = { uri ->
            uri.toURL().openConnection() as HttpsURLConnection
        }
    }

    @Test
    fun geminiErrorCarriesTheRetryAfterDelay() = runBlocking {
        response = FakeResponse(
            status = 429,
            body = """{"error":{"code":429,"message":"Resource exhausted"}}""",
            headers = mapOf("Retry-After" to "7")
        )

        val result = AndroidGeminiNativeTransport()
            .generateContent(gemini, JsonObject(emptyMap()), "key")

        assertEquals(429, result.statusCode)
        assertEquals(7_000L, result.retryAfterMs)
        assertTrue(result.body.contains("Resource exhausted"))
    }

    @Test
    fun geminiRedirectIsRejectedWithItsDelay() = runBlocking {
        response = FakeResponse(
            status = 302,
            body = "",
            headers = mapOf("Retry-After" to "3")
        )

        val result = AndroidGeminiNativeTransport()
            .generateContent(gemini, JsonObject(emptyMap()), "key")

        assertTrue(result.body.contains("redirect_rejected"))
        assertEquals(3_000L, result.retryAfterMs)
    }

    @Test
    fun geminiDiscoverySendsThePageTokenAndKeepsTheDelay() = runBlocking {
        response = FakeResponse(
            status = 429,
            body = """{"error":{"message":"quota exceeded"}}""",
            headers = mapOf("retry-after" to "12")
        )

        AndroidGeminiNativeTransport().getModelsPage(gemini, "key", "next/token", 50)

        val query = requireNotNull(lastConnection).uri.rawQuery
        assertTrue(query, query.contains("pageToken=next%2Ftoken"))
        assertTrue(query, query.contains("pageSize=50"))
    }

    @Test
    fun responsesErrorCarriesTheRetryAfterDelay() = runBlocking {
        response = FakeResponse(
            status = 503,
            body = """{"error":{"message":"busy"}}""",
            headers = mapOf("Retry-After" to "4")
        )

        val result = AndroidOpenAiResponsesTransport()
            .postResponses(responses, JsonObject(emptyMap()), "key")

        assertEquals(503, result.statusCode)
        assertEquals(4_000L, result.retryAfterMs)
    }

    @Test
    fun anthropicErrorCarriesTheRetryAfterDelay() = runBlocking {
        response = FakeResponse(
            status = 429,
            body = """{"error":{"type":"rate_limit_error","message":"slow down"}}""",
            headers = mapOf("Retry-After" to "5")
        )

        val result = AndroidAnthropicMessagesTransport()
            .postMessages(anthropic, JsonObject(emptyMap()), "key")

        assertEquals(429, result.statusCode)
        assertEquals(5_000L, result.retryAfterMs)
        assertTrue(result.body.contains("rate_limit_error"))
    }

    @Test
    fun anthropicRedirectIsRejectedAsAProviderError() = runBlocking {
        response = FakeResponse(status = 307, body = "")

        val result = AndroidAnthropicMessagesTransport()
            .postMessages(anthropic, JsonObject(emptyMap()), "key")

        assertTrue(result.body.contains("redirect_rejected"))
        assertNull(result.retryAfterMs)
    }

    @Test
    fun openAiCompatibleErrorCarriesTheRetryAfterDelay() = runBlocking {
        response = FakeResponse(
            status = 429,
            body = """{"error":{"code":"rate_limit_exceeded","message":"slow down"}}""",
            headers = mapOf("Retry-After" to "9")
        )

        val result = AndroidOpenAiCompatibleTransport()
            .postChatCompletions(compatible, JsonObject(emptyMap()), "key")

        assertEquals(429, result.statusCode)
        assertEquals(9_000L, result.retryAfterMs)
    }

    @Test
    fun openAiCompatibleRedirectIsRejectedWithItsDelay() = runBlocking {
        response = FakeResponse(
            status = 301,
            body = "",
            headers = mapOf("Retry-After" to "1")
        )

        val result = AndroidOpenAiCompatibleTransport()
            .postChatCompletions(compatible, JsonObject(emptyMap()), "key")

        assertTrue(result.body.contains("redirect_rejected"))
        assertEquals(1_000L, result.retryAfterMs)
    }

    @Test
    fun streamingClassifiesTheErrorBodyAndKeepsTheDelay() = runBlocking {
        response = FakeResponse(
            status = 429,
            body = """{"error":{"code":"rate_limit_exceeded","message":"slow down"}}""",
            headers = mapOf("Retry-After" to "3")
        )

        val failure = runCatching {
            AndroidOpenAiStreamingTransport
                .stream(compatible, JsonObject(emptyMap()), "key")
                .toList()
        }.exceptionOrNull() as AiProviderRequestException

        assertEquals(429, failure.httpStatus)
        assertEquals(AiConnectionFailure.RATE_LIMITED, failure.failure)
        assertEquals(3_000L, failure.retryAfterMs)
        assertEquals("rate_limit_exceeded", failure.providerCode)
    }

    @Test
    fun streamingEmitsSuccessfulServerSentEvents() = runBlocking {
        response = FakeResponse(
            status = 200,
            body = "data: {\"delta\":\"hello\"}\n\ndata: [DONE]\n\n",
            contentType = "text/event-stream"
        )

        val payloads = AndroidOpenAiStreamingTransport
            .stream(compatible, JsonObject(emptyMap()), "key")
            .toList()

        assertEquals(listOf("{\"delta\":\"hello\"}"), payloads)
    }
}
