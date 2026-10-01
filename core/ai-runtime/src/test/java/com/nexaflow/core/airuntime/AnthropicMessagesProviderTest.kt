package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicMessagesProviderTest {
    @Test
    fun sendsNativeMessagesShapeAndEmitsTextAndToolCalls() = runBlocking {
        var sent: JsonObject? = null
        val provider = AnthropicMessagesProvider(
            transport = object : AnthropicMessagesTransport {
                override suspend fun postMessages(
                    config: AnthropicMessagesProviderConfig,
                    body: JsonObject,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(
                    200,
                    """{"content":[{"type":"text","text":"hello"},{"type":"tool_use","id":"call-1","name":"device_status","input":{"full":true}}],"stop_reason":"tool_use"}"""
                ).also { sent = body }

                override suspend fun getModels(
                    config: AnthropicMessagesProviderConfig,
                    apiKey: String?
                ) = AnthropicMessagesTransportResponse(200, """{"data":[]}""")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(
            AnthropicMessagesProviderConfig(
                id = "claude-main",
                enabled = true,
                displayName = "Claude",
                baseUrl = "https://api.anthropic.com/v1",
                modelId = "claude-test",
                reasoningEffort = "high"
            )
        )

        val events = provider.stream(
            AiProviderRequest(
                conversationId = "conversation",
                messages = listOf(
                    AiConversationMessage(AiRole.SYSTEM, "Be concise"),
                    AiConversationMessage(AiRole.USER, "Check device"),
                    AiConversationMessage(
                        AiRole.ASSISTANT,
                        text = "",
                        toolCalls = listOf(
                            AiToolCall("call-previous", "device_status", buildJsonObject {
                                put("full", true)
                            })
                        )
                    ),
                    AiConversationMessage(
                        AiRole.TOOL,
                        text = "ready",
                        toolCallId = "call-previous"
                    )
                ),
                tools = listOf(
                    AiToolDefinition("device_status", "Read device status", JsonObject(emptyMap()))
                ),
                maxOutputCharacters = 1024
            )
        ).toList()

        assertEquals("Be concise", sent?.get("system")?.jsonPrimitive?.content)
        assertEquals("claude-test", sent?.get("model")?.jsonPrimitive?.content)
        assertEquals(
            "high",
            sent?.get("output_config")?.jsonObject?.get("effort")?.jsonPrimitive?.content
        )
        assertEquals("device_status", sent?.get("tools")?.jsonArray?.single()
            ?.jsonObject?.get("name")?.jsonPrimitive?.content)
        val messages = sent?.get("messages")?.jsonArray.orEmpty()
        assertEquals("tool_use", messages[1].jsonObject.getValue("content").jsonArray
            .single().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("tool_result", messages[2].jsonObject.getValue("content").jsonArray
            .single().jsonObject.getValue("type").jsonPrimitive.content)
        assertTrue(events.contains(AiProviderEvent.TextDelta("hello")))
        assertTrue(events.any { it is AiProviderEvent.ToolCall && it.call.id == "call-1" })
        assertEquals(AiProviderEvent.Finished("tool_use"), events.last())
    }

    @Test
    fun anthropicCredentialsCannotUseCleartextOrLocalEndpoints() {
        val remoteHttp = AnthropicMessagesProviderConfig(
            enabled = true,
            baseUrl = "http://api.anthropic.com/v1",
            modelId = "claude-test"
        )
        val privateHttps = AnthropicMessagesProviderConfig(
            enabled = true,
            baseUrl = "https://127.0.0.1/v1",
            modelId = "claude-test",
            local = true
        )

        assertTrue(runCatching {
            AnthropicEndpointPolicy.messagesUri(remoteHttp, hasApiKey = true)
        }.isFailure)
        assertTrue(runCatching {
            AnthropicEndpointPolicy.messagesUri(privateHttps, hasApiKey = true)
        }.isFailure)
    }
}
