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
