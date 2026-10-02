package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAiProviderAdaptersTest {

    @Test
    fun openAiResponsesListsModelsAndParsesTextAndToolCalls() = runTest {
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    200,
                    """{
                      "status":"completed",
                      "output":[
                        {"type":"message","content":[{"type":"output_text","text":"hello"}]},
                        {"type":"function_call","call_id":"call-1","name":"device_status","arguments":"{\"full\":true}"}
                      ]
                    }""".trimIndent()
                )

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    200,
                    """{"data":[{"id":"gpt-test"},{"id":"gpt-other"}]}"""
                )
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            OpenAiResponsesProviderConfig(
                enabled = true,
                modelId = "gpt-test"
            )
        )

        val verification = provider.verifyConnection()
        val events = provider.stream(request()).toList()

        assertTrue(verification.success)
        assertEquals(AiApiDialect.OPENAI_RESPONSES, verification.dialect)
        assertEquals("hello", (events[0] as AiProviderEvent.TextDelta).text)
        val call = (events[1] as AiProviderEvent.ToolCall).call
        assertEquals("call-1", call.id)
        assertEquals("device_status", call.name)
    }

    @Test
    fun geminiNativeListsModelsAndParsesTextAndFunctionCalls() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{
                      "candidates":[{
                        "finishReason":"STOP",
                        "content":{"parts":[
                          {"text":"hello"},
                          {"functionCall":{"name":"device_status","args":{"full":true}}}
                        ]}
                      }]
                    }""".trimIndent()
                )

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"models":[{"name":"models/gemini-test"},{"name":"models/gemini-other"}]}"""
                )
            },
            apiKeyProvider = { "secret" },
            callIdGenerator = { "gemini-call-1" }
        )
        provider.configure(
            GeminiNativeProviderConfig(
                enabled = true,
                modelId = "gemini-test"
            )
        )

        val verification = provider.verifyConnection()
        val events = provider.stream(request()).toList()

        assertTrue(verification.success)
        assertEquals(AiApiDialect.GEMINI_GENERATE_CONTENT, verification.dialect)
        assertEquals("hello", (events[0] as AiProviderEvent.TextDelta).text)
        val call = (events[1] as AiProviderEvent.ToolCall).call
        assertEquals("gemini-call-1", call.id)
        assertEquals("device_status", call.name)
    }

    @Test
    fun nativeCloudEndpointsRejectCleartext() {
        val openAi = OpenAiResponsesProviderConfig(
            enabled = true,
            baseUrl = "http://api.openai.com/v1",
            modelId = "gpt-test"
        )
        val gemini = GeminiNativeProviderConfig(
            enabled = true,
            baseUrl = "http://generativelanguage.googleapis.com/v1beta",
            modelId = "gemini-test"
        )

        assertFalse(runCatching { OpenAiResponsesEndpointPolicy.responsesUri(openAi) }.isSuccess)
        assertFalse(runCatching { GeminiNativeEndpointPolicy.generateContentUri(gemini) }.isSuccess)
    }

    @Test
    fun nativeProvidersClassifyMissingModelWithoutLosingHttpStatus() = runTest {
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(200, """{"output":[]}""")

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    200,
                    """{"data":[{"id":"different-model"}]}"""
                )
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            OpenAiResponsesProviderConfig(
                enabled = true,
                modelId = "missing-model"
            )
        )

        val verification = provider.verifyConnection()

        assertFalse(verification.success)
        assertEquals(200, verification.httpStatus)
        assertEquals(AiConnectionFailure.MODEL_NOT_FOUND, verification.failure)
    }

    private fun request() = AiProviderRequest(
        conversationId = "conversation",
        messages = listOf(AiConversationMessage(AiRole.USER, "hello")),
        tools = listOf(
            AiToolDefinition(
                name = "device_status",
                description = "Read device status",
                inputSchema = JsonObject(emptyMap())
            )
        ),
        maxOutputCharacters = 1024
    )
}
