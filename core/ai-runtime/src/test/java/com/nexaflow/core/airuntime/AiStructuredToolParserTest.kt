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

class AiStructuredToolParserTest {

    private val tools = setOf("nexaflow.list_tasks", "nexaflow.create_task")

    @Test
    fun bareJsonObjectIsParsed() {
        val calls = AiStructuredToolParser.parse(
            """{"tool": "nexaflow.list_tasks", "arguments": {}}""",
            tools
        )

        assertEquals(1, calls.size)
        assertEquals("nexaflow.list_tasks", calls.single().name)
    }

    @Test
    fun fencedPayloadIsParsed() {
        val calls = AiStructuredToolParser.parse(
            "Here you go:\n```json\n{\"tool\": \"nexaflow.create_task\", \"arguments\": {\"a\": 1}}\n```",
            tools
        )

        assertEquals(1, calls.size)
        assertEquals("structured-1", calls.single().id)
    }

    @Test
    fun unknownToolsFailClosed() {
        val calls = AiStructuredToolParser.parse(
            """{"tool": "nexaflow.delete_everything", "arguments": {}}""",
            tools
        )

        assertTrue(calls.isEmpty())
    }

    @Test
    fun nonObjectArgumentsAreRejected() {
        val calls = AiStructuredToolParser.parse(
            """{"tool": "nexaflow.list_tasks", "arguments": [1, 2]}""",
            tools
        )

        assertTrue(calls.isEmpty())
    }

    @Test
    fun plainTextYieldsNothing() {
        assertTrue(AiStructuredToolParser.parse("Just a normal reply.", tools).isEmpty())
        assertTrue(AiStructuredToolParser.parse("", tools).isEmpty())
        assertTrue(AiStructuredToolParser.parse("{not json", tools).isEmpty())
    }

    @Test
    fun engineExecutesStructuredCallsForTextOnlyProviders() = runTest {
        val provider = StructuredFakeProvider()
        val executor = StructuredFakeTools()
        val engine = AiConversationEngine(
            registry = AiProviderRegistry(listOf(provider)),
            toolExecutor = executor,
            maxToolIterations = 3
        )

        val events = engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "list"))
        ).toList()

        assertEquals(1, executor.calls)
        assertEquals("nexaflow.list_tasks", executor.lastName)
        assertTrue(events.any { it is AiConversationEvent.ToolStarted })
        assertTrue(events.last() is AiConversationEvent.Completed)
        // The tool instruction is provider-bound only, never stored.
        val completed = events.last() as AiConversationEvent.Completed
        assertTrue(completed.messages.none { it.role == AiRole.SYSTEM })
        assertTrue(provider.seenInstructions)
    }

    @Test
    fun nativeToolCallingProvidersSkipStructuredParsing() = runTest {
        val provider = NativeFakeProvider()
        val executor = StructuredFakeTools()
        val engine = AiConversationEngine(
            registry = AiProviderRegistry(listOf(provider)),
            toolExecutor = executor,
            maxToolIterations = 3
        )

        engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "hi"))
        ).toList()

        assertEquals(0, executor.calls)
        assertTrue(provider.instructionFree)
    }

    private class StructuredFakeProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "text-only",
                displayName = "Text Only",
                capabilities = AiProviderCapabilities(
                    toolCalling = false,
                    structuredOutput = true,
                    local = true
                ),
                available = true
            )
        )
        var seenInstructions = false
        private var turns = 0

        override fun stream(request: AiProviderRequest) = flow {
            seenInstructions = seenInstructions ||
                request.messages.any { it.role == AiRole.SYSTEM }
            turns += 1
            if (turns == 1) {
                emit(AiProviderEvent.TextDelta("""{"tool": "nexaflow.list_tasks", "arguments": {}}"""))
                emit(AiProviderEvent.Finished())
            } else {
                emit(AiProviderEvent.TextDelta("done"))
                emit(AiProviderEvent.Finished())
            }
        }
    }

    private class NativeFakeProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "native",
                displayName = "Native",
                capabilities = AiProviderCapabilities(toolCalling = true, local = true),
                available = true
            )
        )
        var instructionFree = true

        override fun stream(request: AiProviderRequest) = flow {
            instructionFree = instructionFree &&
                request.messages.none { it.role == AiRole.SYSTEM }
            emit(AiProviderEvent.TextDelta("hello"))
            emit(AiProviderEvent.Finished())
        }
    }

    private class StructuredFakeTools : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(AiToolDefinition("nexaflow.list_tasks", "List", buildJsonObject {}))
        )
        var calls = 0
        var lastName: String? = null

        override suspend fun execute(call: AiToolCall): AiToolResult {
            calls += 1
            lastName = call.name
            return AiToolResult(call.id, call.name, buildJsonObject { put("tasks", 0) })
        }
    }
}
