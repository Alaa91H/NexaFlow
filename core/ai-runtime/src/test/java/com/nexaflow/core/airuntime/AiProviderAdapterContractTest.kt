package com.nexaflow.core.airuntime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderAdapterContractTest {

    @Test
    fun openAiConnectionFailurePreservesHttpStatusAndClassification() = runTest {
        val provider = OpenAiCompatibleProvider(
            transport = object : OpenAiCompatibleTransport {
                override suspend fun postChatCompletions(
                    config: OpenAiCompatibleProviderConfig,
                    body: JsonObject,
                    apiKey: String?
                ) = OpenAiCompatibleTransportResponse(401, """{"error":"unauthorized"}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            OpenAiCompatibleProviderConfig(
                enabled = true,
                providerId = "openai-test",
                baseUrl = "https://api.openai.com/v1",
                modelId = "test-model",
                local = false
            )
        )

        val result = provider.verifyConnection()

        assertFalse(result.success)
        assertEquals(401, result.httpStatus)
        assertEquals(AiConnectionFailure.AUTHENTICATION, result.failure)
        assertEquals(AiApiDialect.OPENAI_CHAT_COMPLETIONS, result.dialect)
        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun anthropicConnectionFailurePreservesHttpStatusAndClassification() = runTest {
        val provider = AnthropicMessagesProvider(
            transport = object : AnthropicMessagesTransport {
                override suspend fun postMessages(
                    config: AnthropicMessagesProviderConfig,
                    body: JsonObject,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(200, """{"content":[]}""")

                override suspend fun getModels(
                    config: AnthropicMessagesProviderConfig,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(403, """{"error":"forbidden"}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            AnthropicMessagesProviderConfig(
                id = "anthropic-test",
                enabled = true,
                baseUrl = "https://api.anthropic.com/v1",
                modelId = "claude-test"
            )
        )

        val result = provider.verifyConnection()

        assertFalse(result.success)
        assertEquals(403, result.httpStatus)
        assertEquals(AiConnectionFailure.PERMISSION, result.failure)
        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, result.dialect)
    }

    @Test
    fun statusClassifierIsDeterministic() {
        assertEquals(
            AiConnectionFailure.ENDPOINT_NOT_FOUND,
            AiProviderFailureClassifier.fromHttpStatus(404)
        )
        assertEquals(
            AiConnectionFailure.RATE_LIMITED,
            AiProviderFailureClassifier.fromHttpStatus(429)
        )
        assertEquals(
            AiConnectionFailure.SERVER_ERROR,
            AiProviderFailureClassifier.fromHttpStatus(503)
        )
    }
}
