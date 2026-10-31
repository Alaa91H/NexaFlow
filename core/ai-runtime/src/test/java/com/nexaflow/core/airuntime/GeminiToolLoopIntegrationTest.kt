package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real conversation engine against the Gemini native adapter, so a
 * regression in either layer fails here instead of only on a device.
 */
class GeminiToolLoopIntegrationTest {

    @Test
    fun parallelToolCallsAreAnsweredInOneGeminiFunctionTurn() = runTest {
        val sentBodies = mutableListOf<JsonObject>()
        var turn = 0
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ): GeminiNativeTransportResponse {
                    sentBodies += body
                    turn += 1
                    return if (turn == 1) {
                        GeminiNativeTransportResponse(
                            200,
                            """
                            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
                              {"functionCall":{"name":"nexaflow.get_status","args":{}}},
                              {"functionCall":{"name":"nexaflow.list_tasks","args":{}}}
                            ]}}]}
                            """.trimIndent()
                        )
                    } else {
                        GeminiNativeTransportResponse(
                            200,
                            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"done"}]}}]}"""
                        )
                    }
                }

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"models":[{"name":"models/gemini-test"}]}"""
                )
            },
            apiKeyProvider = { "secret" },
            callIdGenerator = { "gemini-call" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }
        val registry = AiProviderRegistry(listOf(provider)).apply {
            updateRoutingPolicy(AiRoutingPolicy(mode = AiRoutingMode.CLOUD_ONLY))
        }
        val engine = AiConversationEngine(
            registry = registry,
            toolExecutor = TwoToolExecutor(),
            maxToolIterations = 4
        )

        val events = engine.stream(
            "gemini-parallel",
            listOf(AiConversationMessage(AiRole.USER, "check status and tasks"))
        ).toList()

        assertEquals(2, events.count { it is AiConversationEvent.ToolStarted })
        assertEquals(2, events.count { it is AiConversationEvent.ToolFinished })
        val completed = events.last() as AiConversationEvent.Completed
        assertEquals("done", completed.messages.last().text)

        // Second request carries both results in a single function-response turn.
        val contents = sentBodies[1]["contents"]!!.jsonArray
        assertEquals(3, contents.size)
        val responseTurn = contents.last().jsonObject
        assertEquals("user", responseTurn["role"]?.jsonPrimitive?.content)
        val responseParts = responseTurn["parts"]!!.jsonArray
        assertEquals(2, responseParts.size)
        assertTrue(
            responseParts.all { it.jsonObject["functionResponse"] != null }
        )
        val names = responseParts.map {
            it.jsonObject["functionResponse"]!!.jsonObject["name"]!!.jsonPrimitive.content
        }.toSet()
        assertEquals(setOf("nexaflow.get_status", "nexaflow.list_tasks"), names)
    }

    @Test
    fun blockedGeminiPromptFailsTheTurnInsteadOfCompletingEmpty() = runTest {
        val provider = GeminiNativeProvider(
            transport = object : GeminiNativeTransport {
                override suspend fun generateContent(
                    config: GeminiNativeProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}"""
                )

                override suspend fun getModels(
                    config: GeminiNativeProviderConfig,
                    apiKey: String
                ) = GeminiNativeTransportResponse(
                    200,
                    """{"models":[{"name":"models/gemini-test"}]}"""
                )
            },
            apiKeyProvider = { "secret" }
        ).apply {
            configure(GeminiNativeProviderConfig(enabled = true, modelId = "gemini-test"))
        }
        val registry = AiProviderRegistry(listOf(provider)).apply {
            updateRoutingPolicy(AiRoutingPolicy(mode = AiRoutingMode.CLOUD_ONLY))
        }
        val engine = AiConversationEngine(registry)

        val events = engine.stream(
            "gemini-blocked",
            listOf(AiConversationMessage(AiRole.USER, "anything"))
        ).toList()

        val failed = events.last() as AiConversationEvent.Failed
        assertEquals("provider_failure", failed.code)
        assertEquals(AiConnectionFailure.INVALID_RESPONSE, failed.reason)
        assertTrue(events.none { it is AiConversationEvent.Completed })
    }

    private class TwoToolExecutor : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(
                AiToolDefinition(
                    name = "nexaflow.get_status",
                    description = "status",
                    inputSchema = buildJsonObject { put("type", "object") }
                ),
                AiToolDefinition(
                    name = "nexaflow.list_tasks",
                    description = "tasks",
                    inputSchema = buildJsonObject { put("type", "object") }
                )
            )
        )

        override suspend fun execute(call: AiToolCall): AiToolResult = AiToolResult(
            callId = call.id,
            toolName = call.name,
            output = buildJsonObject { put("ok", true) }
        )
    }
}
