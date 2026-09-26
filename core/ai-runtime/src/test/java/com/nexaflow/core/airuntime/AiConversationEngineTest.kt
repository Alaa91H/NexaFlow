package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConversationEngineTest {

    @Test
    fun unavailableProviderReturnsMachineReadableEvent() = runTest {
        val engine = AiConversationEngine(AiProviderRegistry())
        val events = engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "hello"))
        ).toList()

        assertEquals(
            listOf(AiConversationEvent.Unavailable("no_provider")),
            events
        )
    }

    @Test
    fun streamsTextAndCompletesTranscript() = runTest {
        val provider = FakeProvider(
            listOf(
                AiProviderEvent.TextDelta("hello"),
                AiProviderEvent.TextDelta(" world"),
                AiProviderEvent.Finished()
            )
        )
        val engine = AiConversationEngine(AiProviderRegistry(listOf(provider)))

        val events = engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "hi"))
        ).toList()

        assertEquals("hello", (events[0] as AiConversationEvent.AssistantDelta).text)
        assertEquals(" world", (events[1] as AiConversationEvent.AssistantDelta).text)
        val completed = events.last() as AiConversationEvent.Completed
        assertEquals("hello world", completed.messages.last().text)
    }

    @Test
    fun executesToolThenReturnsToProvider() = runTest {
        val provider = SequencedProvider()
        val executor = FakeToolExecutor()
        val engine = AiConversationEngine(
            registry = AiProviderRegistry(listOf(provider)),
            toolExecutor = executor,
            maxToolIterations = 3
        )

        val events = engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "list tasks"))
        ).toList()

        assertEquals(1, executor.calls)
        assertEquals(2, provider.turns)
        assertTrue(events.any { it is AiConversationEvent.ToolStarted })
        assertTrue(events.last() is AiConversationEvent.Completed)
    }

    private class FakeProvider(
        private val events: List<AiProviderEvent>
    ) : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "fake",
                displayName = "Fake",
                available = true
            )
        )

        override fun stream(request: AiProviderRequest) = flow {
            events.forEach { emit(it) }
        }
    }

    private class SequencedProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "fake",
                displayName = "Fake",
                capabilities = AiProviderCapabilities(toolCalling = true),
                available = true
            )
        )
        var turns = 0

        override fun stream(request: AiProviderRequest) = flow {
            turns += 1
            if (turns == 1) {
                emit(
                    AiProviderEvent.ToolCall(
                        AiToolCall(
                            id = "call-1",
                            name = "nexaflow.list_tasks",
                            arguments = buildJsonObject {}
                        )
                    )
                )
            } else {
                emit(AiProviderEvent.TextDelta("done"))
            }
            emit(AiProviderEvent.Finished())
        }
    }

    private class FakeToolExecutor : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(
                AiToolDefinition(
                    name = "nexaflow.list_tasks",
                    description = "List tasks",
                    inputSchema = buildJsonObject { put("type", "object") }
                )
            )
        )
        var calls = 0

        override suspend fun execute(call: AiToolCall): AiToolResult {
            calls += 1
            return AiToolResult(
                callId = call.id,
                toolName = call.name,
                output = buildJsonObject { put("tasks", 0) }
            )
        }
    }
}
