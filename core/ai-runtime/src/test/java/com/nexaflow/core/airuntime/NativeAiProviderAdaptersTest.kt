package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAiProviderAdaptersTest {

    @Test
    fun geminiUsesJsonSchemaFieldForToolDeclarationsAfterModelListVerification() = runTest {
        var generationRequest: JsonObject? = null
        var verificationGenerationCount = 0
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    generationRequest = body
                    verificationGenerationCount++
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
        assertEquals(2, verificationGenerationCount)
    }

    @Test
    fun geminiVerificationFailsWhenModelListingWorksButGenerationIsRejected() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(403, "permission denied")

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }

        val verification = provider.verifyConnection()

        assertFalse(verification.success)
        assertEquals(403, verification.httpStatus)
        assertEquals(AiConnectionFailure.PERMISSION, verification.failure)
    }

    @Test
    fun geminiExpandsDraftSchemaReferencesForToolParameters() = runTest {
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
                        """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""
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
        val schema = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","${'$'}defs":{"item":{"type":"object","properties":{"id":{"type":"integer","const":1},"label":{"type":"string"}}}},"type":"object","properties":{"items":{"type":"array","items":{"${'$'}ref":"#/${'$'}defs/item"}},"config":{"type":"object","propertyNames":{"minLength":1},"additionalProperties":{"type":"string"}},"note":{"type":["string","null"]}}}"""
        ).jsonObject
        val chatRequest = request().copy(
            tools = listOf(AiToolDefinition("create_task", "Create task", schema))
        )

        provider.stream(chatRequest).toList()

        val body = generationRequest!!
        val serialized = body.toString()
        assertFalse(serialized.contains("\"${'$'}schema\""))
        assertFalse(serialized.contains("\"${'$'}defs\""))
        assertFalse(serialized.contains("\"${'$'}ref\""))
        assertFalse(serialized.contains("\"propertyNames\""))
        assertTrue(serialized.contains("\"enum\":[1]"))
        val nullableNote = body["tools"]!!.jsonArray[0].jsonObject
            .getValue("functionDeclarations").jsonArray[0].jsonObject
            .getValue("parametersJsonSchema").jsonObject
            .getValue("properties").jsonObject.getValue("note").jsonObject
        assertEquals("string", nullableNote.getValue("type").jsonPrimitive.content)
        assertEquals(true, nullableNote.getValue("nullable").jsonPrimitive.content.toBoolean())
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

    @Test
    fun geminiBatchesParallelToolResponsesIntoSingleTurn() = runTest {
        var sent: JsonObject? = null
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    sent = body
                    return GeminiNativeTransportResponse(200, """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
                }

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" },
            callIdGenerator = { "gemini-call" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }
        val calls = listOf(
            AiToolCall("call-a", "device_status", JsonObject(emptyMap())),
            AiToolCall("call-b", "nexaflow.list_tasks", JsonObject(emptyMap()))
        )
        val messages = listOf(
            AiConversationMessage(AiRole.USER, "check two things"),
            AiConversationMessage(AiRole.ASSISTANT, text = "", toolCalls = calls),
            AiConversationMessage(AiRole.TOOL, """{"ok":1}""", toolCallId = "call-a", toolName = "device_status"),
            AiConversationMessage(AiRole.TOOL, """{"ok":2}""", toolCallId = "call-b", toolName = "nexaflow.list_tasks")
        )

        provider.stream(request().copy(messages = messages)).toList()

        val contents = sentOrThrow(sent)["contents"].toString()
        val contentsArray = sentOrThrow(sent)["contents"]!!.jsonArray
        assertEquals(3, contentsArray.size)
        val responseParts = contentsArray.last().jsonObject.getValue("parts").jsonArray
        assertEquals(2, responseParts.size)
        assertTrue(contents.contains("functionResponse"))
        assertEquals(2, Regex("\"functionResponse\"").findAll(contents).count())
    }

    @Test
    fun geminiSendsExplicitAutoToolConfigWithTools() = runTest {
        var sent: JsonObject? = null
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    sent = body
                    return GeminiNativeTransportResponse(200, """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
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

        provider.stream(request()).toList()

        assertEquals(
            "AUTO",
            sentOrThrow(sent)["toolConfig"]?.jsonObject
                ?.get("functionCallingConfig")?.jsonObject
                ?.get("mode")?.jsonPrimitive?.content
        )
    }

    @Test
    fun geminiRejectsBlockedPromptInsteadOfCompletingEmpty() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"promptFeedback":{"blockReason":"SAFETY"}}"""
                )

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }

        val failure = runCatching {
            provider.stream(request()).toList()
        }.exceptionOrNull()

        assertTrue(failure is AiProviderRequestException)
        failure as AiProviderRequestException
        assertEquals(AiConnectionFailure.INVALID_RESPONSE, failure.failure)
        assertEquals("prompt_blocked_safety", failure.providerCode)
        assertTrue(failure.providerMessage!!.contains("SAFETY"))
    }

    @Test
    fun geminiRejectsSafetyFinishReasonWithoutContent() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}"""
                )

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }

        val failure = runCatching {
            provider.stream(request()).toList()
        }.exceptionOrNull()

        assertEquals("finish_safety", (failure as AiProviderRequestException).providerCode)
    }

    @Test
    fun geminiDiscoversModelsAcrossPages() = runTest {
        val requestedPages = mutableListOf<String?>()
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"candidates":[]}""")

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = error("paginated discovery must not use the legacy single page")

                override suspend fun getModelsPage(
                    config: GeminiNativeProviderConfig,
                    apiKey: String,
                    pageToken: String?,
                    pageSize: Int
                ): GeminiNativeTransportResponse {
                    requestedPages += pageToken
                    assertEquals(1000, pageSize)
                    return if (pageToken == null) {
                        GeminiNativeTransportResponse(
                            200,
                            """{"models":[{"name":"models/gemini-a"}],"nextPageToken":"page-2"}"""
                        )
                    } else {
                        GeminiNativeTransportResponse(
                            200,
                            """{"models":[{"name":"models/gemini-b"}]}"""
                        )
                    }
                }
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-b"))
        }

        val discovery = provider.listModels()

        assertTrue(discovery.success)
        assertEquals(listOf("gemini-a", "gemini-b"), discovery.models.map { it.id })
        assertEquals(listOf(null, "page-2"), requestedPages)
        assertTrue(provider.verifyConnection().success)
    }

    @Test
    fun geminiStreamFailureCarriesProviderMessageFromErrorBody() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    400,
                    """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""",
                    2_000L
                )

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(200, """{"models":[{"name":"models/gemini-test"}]}""")
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }

        val failure = runCatching {
            provider.stream(request()).toList()
        }.exceptionOrNull() as AiProviderRequestException

        assertEquals(AiConnectionFailure.AUTHENTICATION, failure.failure)
        assertEquals(400, failure.httpStatus)
        assertEquals(2_000L, failure.retryAfterMs)
        assertEquals("INVALID_ARGUMENT", failure.providerCode)
        assertTrue(failure.providerMessage!!.contains("API key not valid"))
    }

    private fun sentOrThrow(value: JsonObject?): JsonObject {
        assertTrue("provider request body was not captured", value != null)
        return value!!
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
