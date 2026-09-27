package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTest {

    @Test
    fun disabledConfigurationIsUnavailable() {
        val provider = provider(FakeTransport())

        provider.configure(
            OpenAiCompatibleProviderConfig(
                enabled = false,
                baseUrl = "http://127.0.0.1:11434/v1",
                modelId = "qwen3"
            )
        )

        assertFalse(provider.descriptor.value.available)
    }

    @Test
    fun requestContainsToolsAndPreservedToolHistory() = runTest {
        val transport = FakeTransport()
        val provider = provider(transport)
        provider.configure(config())

        provider.stream(
            AiProviderRequest(
                conversationId = "c1",
                messages = listOf(
                    AiConversationMessage(AiRole.USER, "list tasks"),
                    AiConversationMessage(
                        role = AiRole.ASSISTANT,
                        text = "",
                        toolCalls = listOf(
                            AiToolCall(
                                id = "call-1",
                                name = "nexaflow.list_tasks",
                                arguments = buildJsonObject {}
                            )
                        )
                    ),
                    AiConversationMessage(
                        role = AiRole.TOOL,
                        text = "[]",
                        toolCallId = "call-1",
                        toolName = "nexaflow.list_tasks"
                    )
                ),
                tools = listOf(
                    AiToolDefinition(
                        name = "nexaflow.list_tasks",
                        description = "List tasks",
                        inputSchema = buildJsonObject { put("type", "object") }
                    )
                ),
                maxOutputCharacters = 1024
            )
        ).toList()

        val body = requireNotNull(transport.lastBody)
        assertEquals("qwen3", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, body["tools"]!!.jsonArray.size)
        val messages = body["messages"]!!.jsonArray
        val assistant = messages[1].jsonObject
        assertEquals(1, assistant["tool_calls"]!!.jsonArray.size)
        assertEquals(
            "{}",
            assistant["tool_calls"]!!.jsonArray[0]
                .jsonObject["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content
        )
        assertEquals(
            "call-1",
            messages[2].jsonObject["tool_call_id"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun parsesBufferedFallbackResponse() = runTest {
        val provider = provider(FakeTransport())
        provider.configure(config())

        val events = provider.stream(request("inspect")).toList()

        assertEquals("ok", (events[0] as AiProviderEvent.TextDelta).text)
        assertTrue(events.last() is AiProviderEvent.Finished)
    }

    @Test
    fun streamsTextAndReassemblesFragmentedToolCall() = runTest {
        val provider = provider(
            StreamingTransport(
                listOf(
                    """{"choices":[{"delta":{"content":"Check "},"finish_reason":null}]}""",
                    """{"choices":[{"delta":{"content":"done","tool_calls":[{"index":0,"id":"call-1","function":{"name":"nexaflow.get_history","arguments":"{\"limit\":"}}]},"finish_reason":null}]}""",
                    """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"5}"}}]},"finish_reason":"tool_calls"}]}"""
                )
            )
        )
        provider.configure(config())

        val events = provider.stream(request("inspect")).toList()

        assertEquals("Check ", (events[0] as AiProviderEvent.TextDelta).text)
        assertEquals("done", (events[1] as AiProviderEvent.TextDelta).text)
        val tool = (events[2] as AiProviderEvent.ToolCall).call
        assertEquals("call-1", tool.id)
        assertEquals("nexaflow.get_history", tool.name)
        assertEquals("5", tool.arguments["limit"]!!.jsonPrimitive.content)
        assertEquals(
            "tool_calls",
            (events.last() as AiProviderEvent.Finished).reason
        )
    }

    @Test
    fun structuredFallbackUsesOnlyKnownToolAfterCapabilityProbe() = runTest {
        val transport = FallbackCapabilityTransport(
            fallbackContent =
                """{"tool":"nexaflow.list_tasks","arguments":{}}"""
        )
        val provider = OpenAiCompatibleProvider(
            transport = transport,
            fallbackToolCallIdGenerator = { "fallback-test" }
        )
        provider.configure(config())

        val probe = provider.probe()

        assertTrue(probe.success)
        assertFalse(probe.capabilities.toolCalling)
        assertTrue(probe.capabilities.structuredOutput)
        assertFalse(probe.capabilities.streaming)

        val events = provider.stream(
            requestWithTool("list tasks")
        ).toList()

        val tool = (events.first() as AiProviderEvent.ToolCall).call
        assertEquals("fallback-test", tool.id)
        assertEquals("nexaflow.list_tasks", tool.name)
        assertTrue(tool.arguments.isEmpty())
        assertEquals(
            "structured_tool_call",
            (events.last() as AiProviderEvent.Finished).reason
        )

        val fallbackBody = transport.postBodies.last()
        assertEquals(
            "json_object",
            fallbackBody["response_format"]!!
                .jsonObject["type"]!!.jsonPrimitive.content
        )
        assertFalse("Native tools must not be sent in fallback mode", "tools" in fallbackBody)
    }

    @Test
    fun structuredFallbackCanReturnAssistantMessage() = runTest {
        val transport = FallbackCapabilityTransport(
            fallbackContent = """{"message":"No tool is needed."}"""
        )
        val provider = OpenAiCompatibleProvider(
            transport = transport,
            fallbackToolCallIdGenerator = { "fallback-test" }
        )
        provider.configure(config())
        provider.probe()

        val events = provider.stream(
            requestWithTool("explain status")
        ).toList()

        assertEquals(
            "No tool is needed.",
            (events.first() as AiProviderEvent.TextDelta).text
        )
        assertEquals(
            "structured_message",
            (events.last() as AiProviderEvent.Finished).reason
        )
    }

    @Test
    fun structuredFallbackRejectsUnknownTool() = runTest {
        val transport = FallbackCapabilityTransport(
            fallbackContent = """{"tool":"nexaflow.unknown","arguments":{}}"""
        )
        val provider = OpenAiCompatibleProvider(
            transport = transport,
            fallbackToolCallIdGenerator = { "fallback-test" }
        )
        provider.configure(config())
        provider.probe()

        val failure = runCatching {
            provider.stream(requestWithTool("do it")).toList()
        }

        assertTrue(failure.isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonObjectToolArguments() = runTest {
        val provider = provider(
            StreamingTransport(
                listOf(
                    """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-a","function":{"name":"nexaflow.list_tasks","arguments":"[]"}}]},"finish_reason":"tool_calls"}]}"""
                )
            )
        )
        provider.configure(config())

        provider.stream(request("inspect")).toList()
    }

    private fun provider(transport: OpenAiCompatibleTransport) =
        OpenAiCompatibleProvider(transport = transport)

    private fun requestWithTool(text: String) = AiProviderRequest(
        conversationId = "c1",
        messages = listOf(AiConversationMessage(AiRole.USER, text)),
        tools = listOf(
            AiToolDefinition(
                name = "nexaflow.list_tasks",
                description = "List tasks",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("additionalProperties", false)
                }
            )
        ),
        maxOutputCharacters = 1024
    )

    private fun request(text: String) = AiProviderRequest(
        conversationId = "c1",
        messages = listOf(AiConversationMessage(AiRole.USER, text)),
        tools = emptyList(),
        maxOutputCharacters = 1024
    )

    private fun config() = OpenAiCompatibleProviderConfig(
        enabled = true,
        displayName = "Local model",
        baseUrl = "http://127.0.0.1:11434/v1",
        modelId = "qwen3",
        local = true
    )

    private class FakeTransport : OpenAiCompatibleTransport {
        var lastBody: JsonObject? = null

        override suspend fun postChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ): OpenAiCompatibleTransportResponse {
            lastBody = body
            return OpenAiCompatibleTransportResponse(
                200,
                """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}"""
            )
        }
    }

    private class FallbackCapabilityTransport(
        private val fallbackContent: String
    ) : OpenAiCompatibleTransport {
        val postBodies = mutableListOf<JsonObject>()
        private var postCount = 0

        override suspend fun postChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ): OpenAiCompatibleTransportResponse {
            postBodies += body
            postCount += 1
            val content = when (postCount) {
                1 -> "OK"
                2 -> "Native tools unavailable"
                3 -> """{"ok":true}"""
                else -> fallbackContent
            }
            return OpenAiCompatibleTransportResponse(
                200,
                buildResponse(content)
            )
        }

        override fun streamChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ) = flow {
            emit(buildResponse("OK"))
        }

        private fun buildResponse(content: String): String =
            buildJsonObject {
                putJsonArray("choices") {
                    add(
                        buildJsonObject {
                            putJsonObject("message") {
                                put("role", "assistant")
                                put("content", content)
                            }
                            put("finish_reason", "stop")
                        }
                    )
                }
            }.toString()
    }

    private class StreamingTransport(
        private val chunks: List<String>
    ) : OpenAiCompatibleTransport {
        override suspend fun postChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ) = OpenAiCompatibleTransportResponse(200, "{}")

        override fun streamChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ) = flow {
            chunks.forEach { emit(it) }
        }
    }
}
