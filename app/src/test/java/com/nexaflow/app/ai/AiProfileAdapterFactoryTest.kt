package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiApiDialect
import com.nexaflow.core.airuntime.AiCredentialReference
import com.nexaflow.core.airuntime.AiCredentialStore
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.airuntime.AnthropicMessagesTransportResponse
import com.nexaflow.core.airuntime.GeminiNativeProviderConfig
import com.nexaflow.core.airuntime.GeminiNativeTransport
import com.nexaflow.core.airuntime.GeminiNativeTransportResponse
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.OpenAiCompatibleTransportResponse
import com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig
import com.nexaflow.core.airuntime.OpenAiResponsesTransport
import com.nexaflow.core.airuntime.OpenAiResponsesTransportResponse
import com.nexaflow.core.datastore.AiProviderProfileSettings
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProfileAdapterFactoryTest {

    private val factory = AiProfileAdapterFactory(
        chatTransport = ChatTransport(),
        responsesTransport = ResponsesTransport(),
        anthropicTransport = AnthropicTransport(),
        geminiTransport = GeminiTransport(),
        credentialStore = EmptyCredentialStore()
    )

    @Test
    fun openCodeZenUsesModelSpecificDialects() {
        assertEquals(
            AiApiDialect.OPENAI_RESPONSES,
            factory.effectiveDialect(profile("opencode_zen", "gpt-5.6-sol"))
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            factory.effectiveDialect(profile("opencode_zen", "claude-sonnet-5"))
        )
        assertEquals(
            AiApiDialect.GEMINI_GENERATE_CONTENT,
            factory.effectiveDialect(profile("opencode_zen", "gemini-3.7-flash"))
        )
        assertEquals(
            AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            factory.effectiveDialect(profile("opencode_zen", "deepseek-v4-pro"))
        )
    }

    @Test
    fun openCodeGoAndZenDoNotShareMinimaxRouting() {
        assertEquals(
            AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            factory.effectiveDialect(profile("opencode_zen", "minimax-m3"))
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            factory.effectiveDialect(profile("opencode_go", "minimax-m3"))
        )
    }

    @Test
    fun ordinaryProfilesKeepTheirStoredLegacyProtocol() {
        val value = profile(
            presetId = "custom",
            modelId = "model",
            protocol = "ANTHROPIC_MESSAGES"
        )

        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, factory.effectiveDialect(value))
    }

    private fun profile(
        presetId: String,
        modelId: String,
        protocol: String = "OPENAI_CHAT_COMPLETIONS"
    ) = AiProviderProfileSettings(
        id = "profile",
        presetId = presetId,
        displayName = "Provider",
        protocol = protocol,
        baseUrl = "https://example.com/v1",
        modelId = modelId,
        local = false,
        enabled = true
    )

    private class EmptyCredentialStore : AiCredentialStore {
        override suspend fun resolve(reference: AiCredentialReference): String? = null
        override suspend fun store(reference: AiCredentialReference, value: String) = Unit
        override suspend fun delete(reference: AiCredentialReference) = Unit
    }

    private class ChatTransport : OpenAiCompatibleTransport {
        override suspend fun postChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ) = OpenAiCompatibleTransportResponse(200, "{}")
    }

    private class ResponsesTransport : OpenAiResponsesTransport {
        override suspend fun postResponses(
            config: OpenAiResponsesProviderConfig,
            body: JsonObject,
            apiKey: String
        ) = OpenAiResponsesTransportResponse(200, "{}")

        override suspend fun getModels(
            config: OpenAiResponsesProviderConfig,
            apiKey: String
        ) = OpenAiResponsesTransportResponse(200, """{"data":[]}""")
    }

    private class AnthropicTransport : AnthropicMessagesTransport {
        override suspend fun postMessages(
            config: AnthropicMessagesProviderConfig,
            body: JsonObject,
            apiKey: String?
        ) = AnthropicMessagesTransportResponse(200, "{}")

        override suspend fun getModels(
            config: AnthropicMessagesProviderConfig,
            apiKey: String?
        ) = AnthropicMessagesTransportResponse(200, """{"data":[]}""")
    }

    private class GeminiTransport : GeminiNativeTransport {
        override suspend fun generateContent(
            config: GeminiNativeProviderConfig,
            body: JsonObject,
            apiKey: String
        ) = GeminiNativeTransportResponse(200, "{}")

        override suspend fun getModels(
            config: GeminiNativeProviderConfig,
            apiKey: String
        ) = GeminiNativeTransportResponse(200, """{"models":[]}""")
    }
}
