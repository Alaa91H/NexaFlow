package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiApiDialect
import com.nexaflow.core.airuntime.AiProviderProtocol
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.airuntime.AnthropicMessagesTransportResponse
import com.nexaflow.core.airuntime.GeminiNativeTransport
import com.nexaflow.core.airuntime.GeminiNativeTransportResponse
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.OpenAiCompatibleTransportResponse
import com.nexaflow.core.airuntime.OpenAiResponsesTransport
import com.nexaflow.core.airuntime.OpenAiResponsesTransportResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiProviderDraftAdapterFactoryTest {

    @Test
    fun openAiPresetUsesResponsesDialectDuringVerification() = runTest {
        val factory = factory()
        val adapter = factory.create(
            profileId = null,
            presetId = "openai",
            protocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-test",
            local = false,
            reasoningEffort = null,
            apiKeyProvider = { "secret" }
        )

        assertNotNull(adapter)
        assertEquals(AiApiDialect.OPENAI_RESPONSES, adapter!!.verifyConnection().dialect)
    }

    @Test
    fun persistedGeminiDialectOverridesLegacyProtocolProjection() = runTest {
        val factory = factory()
        val adapter = factory.create(
            profileId = "custom-profile",
            presetId = null,
            protocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            explicitDialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            displayName = "Gemini custom",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelId = "gemini-test",
            local = false,
            reasoningEffort = null,
            apiKeyProvider = { "secret" }
        )

        assertNotNull(adapter)
        assertEquals(AiApiDialect.GEMINI_GENERATE_CONTENT, adapter!!.verifyConnection().dialect)
    }

    private fun factory() = AiProviderDraftAdapterFactory(
        compatibleTransport = object : OpenAiCompatibleTransport {
            override suspend fun postChatCompletions(
                config: com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig,
                body: kotlinx.serialization.json.JsonObject,
                apiKey: String?
            ) = OpenAiCompatibleTransportResponse(501, "")

            override suspend fun getModels(
                config: com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig,
                apiKey: String?
            ) = OpenAiCompatibleTransportResponse(200, """{"data":[{"id":"gpt-test"}]}""")
        },
        responsesTransport = object : OpenAiResponsesTransport {
            override suspend fun postResponses(
                config: com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig,
                body: kotlinx.serialization.json.JsonObject,
                apiKey: String
            ) = OpenAiResponsesTransportResponse(200, "")

            override suspend fun getModels(
                config: com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig,
                apiKey: String
            ) = OpenAiResponsesTransportResponse(200, """{"data":[{"id":"gpt-test"}]}""")
        },
        anthropicTransport = object : AnthropicMessagesTransport {
            override suspend fun postMessages(
                config: com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig,
                body: kotlinx.serialization.json.JsonObject,
                apiKey: String?
            ) = AnthropicMessagesTransportResponse(501, "")

            override suspend fun getModels(
                config: com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig,
                apiKey: String?
            ) = AnthropicMessagesTransportResponse(501, "")
        },
        geminiTransport = object : GeminiNativeTransport {
            override suspend fun generateContent(
                config: com.nexaflow.core.airuntime.GeminiNativeProviderConfig,
                body: kotlinx.serialization.json.JsonObject,
                apiKey: String
            ) = GeminiNativeTransportResponse(200, "")

            override suspend fun getModels(
                config: com.nexaflow.core.airuntime.GeminiNativeProviderConfig,
                apiKey: String
            ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
        }
    )
}
