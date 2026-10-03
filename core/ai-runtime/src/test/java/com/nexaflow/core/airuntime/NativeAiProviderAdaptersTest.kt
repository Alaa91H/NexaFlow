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
    fun geminiUsesJsonSchemaFieldForToolDeclarationsAfterModelListVerification() = runTest {
        var generationRequest: JsonObject? = null
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    generationRequest = body
                    return GeminiNativeTransportResponse(
                        200,
                        """{"candidates":[{"content":{"parts":[{"text":"ready"}]}}]}"""
                    )
                }

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }

        assertTrue(provider.verifyConnection().success)
        provider.stream(request()).toList()

        val declaration = generationRequest!!["tools"].toString()
        assertTrue(declaration.contains("parametersJsonSchema"))
        assertFalse(declaration.contains("\"parameters\":"))
    }

    @Test
    fun openAiResponsesPreservesReasoningAndToolCallAcrossToolRoundTrip() = runTest {
        val requests = mutableListOf<JsonObject>()
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): OpenAiResponsesTransportResponse {
                    requests += body
                    return if (requests.size == 1) {
                        OpenAiResponsesTransportResponse(
                            200,
                            """{"status":"completed","output":[{"type":"reasoning","id":"rs_1","encrypted_content":"opaque"},{"type":"function_call","call_id":"call-1","name":"device_status","arguments":"{\"full\":true}"}]}"""
                        )
                    } else {
                        OpenAiResponsesTransportResponse(200, """{"status":"completed","output":[]}""")
                    }
                }

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(200, """{"data":[{"id":"gpt-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(OpenAiResponsesProviderConfig(enabled = true, modelId = "gpt-test"))
        }
        val firstEvents = provider.stream(request()).toList()
        val call = (firstEvents.first { it is AiProviderEvent.ToolCall } as AiProviderEvent.ToolCall).call
        val context = (firstEvents.first { it is AiProviderEvent.WireContext } as AiProviderEvent.WireContext)
            .context
        val nextRequest = request().copy(
            messages = request().messages + AiConversationMessage(
                role = AiRole.ASSISTANT,
                text = "",
                toolCalls = listOf(call),
                providerContext = context
            ) + AiConversationMessage(
                role = AiRole.TOOL,
                text = "{\"ok\":true}",
                toolCallId = call.id,
                toolName = call.name
            )
        )

        provider.stream(nextRequest).toList()

        val resumedInput = requests[1]["input"].toString()
        assertTrue(resumedInput.contains("encrypted_content"))
        assertTrue(resumedInput.contains("opaque"))
        assertTrue(resumedInput.contains("function_call"))
        assertTrue(resumedInput.contains("call-1"))
        assertTrue(resumedInput.contains("function_call_output"))
        assertTrue(resumedInput.contains("{\\\"ok\\\":true}"))
    }

    @Test
    fun geminiPreservesThoughtSignatureAndFunctionCallAcrossToolRoundTrip() = runTest {
        val requests = mutableListOf<JsonObject>()
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    requests += body
                    return if (requests.size == 1) {
                        GeminiNativeTransportResponse(
                            200,
                            """{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"functionCall":{"name":"device_status","args":{"full":true}},"thoughtSignature":"opaque-signature"}]}}]}"""
                        )
                    } else {
                        GeminiNativeTransportResponse(200, """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"done"}]}}]}""")
                    }
                }

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" },
            callIdGenerator = { "gemini-call-1" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }
        val firstEvents = provider.stream(request()).toList()
        val call = (firstEvents.first { it is AiProviderEvent.ToolCall } as AiProviderEvent.ToolCall).call
        val context = (firstEvents.first { it is AiProviderEvent.WireContext } as AiProviderEvent.WireContext)
            .context
        val nextRequest = request().copy(
            messages = request().messages + AiConversationMessage(
                role = AiRole.ASSISTANT,
                text = "",
                toolCalls = listOf(call),
                providerContext = context
            ) + AiConversationMessage(
                role = AiRole.TOOL,
                text = "{\"ok\":true}",
                toolCallId = call.id,
                toolName = call.name
            )
        )

        provider.stream(nextRequest).toList()

        val resumedContents = requests[1]["contents"].toString()
        assertTrue(resumedContents.contains("thoughtSignature"))
        assertTrue(resumedContents.contains("opaque-signature"))
        assertTrue(resumedContents.contains("functionCall"))
        assertTrue(resumedContents.contains("functionResponse"))
        assertTrue(resumedContents.contains("device_status"))
    }

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

    @Test
    fun openAiResponsesStreamExposesTypedRateLimitFailure() = runTest {
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    429,
                    """{"error":"rate_limited"}"""
                )

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    200,
                    """{"data":[{"id":"gpt-test"}]}"""
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

        val failure = runCatching {
            provider.stream(request()).toList()
        }.exceptionOrNull()

        assertTrue(failure is AiProviderRequestException)
        failure as AiProviderRequestException
        assertEquals(429, failure.httpStatus)
        assertEquals(AiConnectionFailure.RATE_LIMITED, failure.failure)
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
