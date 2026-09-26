package com.nexaflow.core.airuntime

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
        val transport = FakeTransport(
            response = OpenAiCompatibleTransportResponse(
                200,
                """{"choices":[{"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}]}"""
            )
        )
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
    fun parsesTextAndMultipleToolCalls() = runTest {
        val provider = provider(
            FakeTransport(
                OpenAiCompatibleTransportResponse(
                    200,
                    """
                    {
                      "choices": [{
                        "message": {
                          "role": "assistant",
                          "content": "Checking",
                          "tool_calls": [
                            {
                              "id": "call-a",
                              "type": "function",
                              "function": {
                                "name": "nexaflow.list_tasks",
                                "arguments": "{}"
                              }
                            },
                            {
                              "id": "call-b",
                              "type": "function",
                              "function": {
                                "name": "nexaflow.get_history",
                                "arguments": "{\"limit\":5}"
                              }
                            }
                          ]
                        },
                        "finish_reason": "tool_calls"
                      }]
                    }
                    """.trimIndent()
                )
            )
        )
        provider.configure(config())

        val events = provider.stream(
            AiProviderRequest(
                conversationId = "c1",
                messages = listOf(AiConversationMessage(AiRole.USER, "inspect")),
                tools = emptyList(),
                maxOutputCharacters = 1024
            )
        ).toList()

        assertEquals("Checking", (events[0] as AiProviderEvent.TextDelta).text)
        assertEquals(
            "nexaflow.list_tasks",
            (events[1] as AiProviderEvent.ToolCall).call.name
        )
        assertEquals(
            "5",
            (events[2] as AiProviderEvent.ToolCall)
                .call.arguments["limit"]!!.jsonPrimitive.content
        )
        assertEquals(
            "tool_calls",
            (events.last() as AiProviderEvent.Finished).reason
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonObjectToolArguments() = runTest {
        val provider = provider(
            FakeTransport(
                OpenAiCompatibleTransportResponse(
                    200,
                    """
                    {"choices":[{"message":{"role":"assistant","tool_calls":[{
                      "id":"call-a","type":"function",
                      "function":{"name":"nexaflow.list_tasks","arguments":"[]"}
                    }]}}]}
                    """.trimIndent()
                )
            )
        )
        provider.configure(config())

        provider.stream(
            AiProviderRequest(
                conversationId = "c1",
                messages = listOf(AiConversationMessage(AiRole.USER, "inspect")),
                tools = emptyList(),
                maxOutputCharacters = 1024
            )
        ).toList()
    }

    private fun provider(transport: FakeTransport) =
        OpenAiCompatibleProvider(transport = transport)

    private fun config() = OpenAiCompatibleProviderConfig(
        enabled = true,
        displayName = "Local model",
        baseUrl = "http://127.0.0.1:11434/v1",
        modelId = "qwen3",
        local = true
    )

    private class FakeTransport(
        private val response: OpenAiCompatibleTransportResponse =
            OpenAiCompatibleTransportResponse(
                200,
                """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
            )
    ) : OpenAiCompatibleTransport {
        var lastBody: JsonObject? = null

        override suspend fun postChatCompletions(
            config: OpenAiCompatibleProviderConfig,
            body: JsonObject,
            apiKey: String?
        ): OpenAiCompatibleTransportResponse {
            lastBody = body
            return response
        }
    }
}
