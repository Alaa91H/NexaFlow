package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiGatewaySessionPropagationTest {

    @Test
    fun chatAdapterPropagatesStableGatewaySessionHeaders() = runTest {
        var captured = emptyMap<String, String>()
        val provider = OpenAiCompatibleProvider(
            transport = object : OpenAiCompatibleTransport {
                override suspend fun postChatCompletions(
                    config: OpenAiCompatibleProviderConfig,
                    body: JsonObject,
                    apiKey: String?
                ) = OpenAiCompatibleTransportResponse(200, """{"choices":[]}""")

                override fun streamChatCompletionsWithHeaders(
                    config: OpenAiCompatibleProviderConfig,
                    body: JsonObject,
                    apiKey: String?,
                    headers: Map<String, String>
                ) = flowOf("""{"choices":[{"message":{"content":"ok"}}]}""").also {
                    captured = headers
                }
            }
        )
        provider.configure(
            OpenAiCompatibleProviderConfig(
                enabled = true,
                providerId = "gateway-chat",
                baseUrl = "https://example.com/v1",
                modelId = "model",
                local = false,
                gatewaySession = true
            )
        )

        provider.stream(request()).toList()

        assertGatewayHeaders(captured)
    }

    @Test
    fun responsesAdapterPropagatesStableGatewaySessionHeaders() = runTest {
        var captured = emptyMap<String, String>()
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(200, """{"output":[]}""")

                override suspend fun postResponsesWithHeaders(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String,
                    headers: Map<String, String>
                ): OpenAiResponsesTransportResponse {
                    captured = headers
                    return OpenAiResponsesTransportResponse(
                        200,
                        """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"ok"}]}]}"""
                    )
                }

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(200, """{"data":[{"id":"model"}]}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            OpenAiResponsesProviderConfig(
                enabled = true,
                baseUrl = "https://example.com/v1",
                modelId = "model",
                gatewaySession = true
            )
        )

        provider.stream(request()).toList()

        assertGatewayHeaders(captured)
    }

    @Test
    fun anthropicAdapterPropagatesStableGatewaySessionHeaders() = runTest {
        var captured = emptyMap<String, String>()
        val provider = AnthropicMessagesProvider(
            transport = object : AnthropicMessagesTransport {
                override suspend fun postMessages(
                    config: AnthropicMessagesProviderConfig,
                    body: JsonObject,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(200, """{"content":[]}""")

                override suspend fun postMessagesWithHeaders(
                    config: AnthropicMessagesProviderConfig,
                    body: JsonObject,
                    apiKey: String?,
                    headers: Map<String, String>
                ): AnthropicMessagesTransportResponse {
                    captured = headers
                    return AnthropicMessagesTransportResponse(
                        200,
                        """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
                    )
                }

                override suspend fun getModels(
                    config: AnthropicMessagesProviderConfig,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(200, """{"data":[{"id":"model"}]}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            AnthropicMessagesProviderConfig(
                id = "gateway-anthropic",
                enabled = true,
                baseUrl = "https://example.com/v1",
                modelId = "model",
                gatewaySession = true
            )
        )

        provider.stream(request()).toList()

        assertGatewayHeaders(captured)
    }

    @Test
    fun geminiAdapterPropagatesStableGatewaySessionHeaders() = runTest {
        var captured = emptyMap<String, String>()
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"candidates":[]}""")

                override suspend fun generateContentWithHeaders(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String,
                    headers: Map<String, String>
                ): GeminiNativeTransportResponse {
                    captured = headers
                    return GeminiNativeTransportResponse(
                        200,
                        """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"ok"}]}}]}"""
                    )
                }

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/model"}]}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            GeminiNativeProviderConfig(
                id = "gateway-gemini",
                enabled = true,
                baseUrl = "https://example.com/v1",
                modelId = "model",
                gatewaySession = true
            )
        )

        provider.stream(request()).toList()

        assertGatewayHeaders(captured)
    }

    private fun request() = AiProviderRequest(
        conversationId = " conversation / 42 ",
        messages = listOf(AiConversationMessage(AiRole.USER, "hello")),
        tools = emptyList(),
        maxOutputCharacters = 512
    )

    private fun assertGatewayHeaders(headers: Map<String, String>) {
        assertEquals(
            "conversation---42",
            headers[AiGatewaySessionPolicy.HEADER_NAME]
        )
        assertEquals(
            AiGatewaySessionPolicy.CLIENT_USER_AGENT,
            headers[AiGatewaySessionPolicy.USER_AGENT_HEADER]
        )
    }
}
