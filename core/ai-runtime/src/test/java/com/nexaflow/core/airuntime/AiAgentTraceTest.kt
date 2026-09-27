package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgentTraceTest {

    @Test
    fun secretsNeverReachTheStoredTrace() {
        val recorder = AiAgentTraceRecorder()
        val session = recorder.startSession("conversation-1", userChars = 12)
        session.onToolCall(
            AiToolCall(
                id = "call-1",
                name = "nexaflow.create_task",
                arguments = buildJsonObject {
                    put("task", buildJsonObject { put("name", "x") })
                    put("api_key", "sk-live-1234567890")
                    put("config", buildJsonObject { put("password", "hunter2-hunter2") })
                }
            )
        )
        session.onToolResult(
            AiToolResult(
                callId = "call-1",
                toolName = "nexaflow.create_task",
                output = buildJsonObject {
                    put("authorization", "Bearer abcdefghijklmnop")
                    put("ok", true)
                }
            )
        )
        session.onTerminal(AiAgentTraceOutcome.COMPLETED)

        val trace = recorder.get(session.traceId)!!
        val stored = trace.toolCalls.single()
        assertFalse(stored.redactedArguments.toString().contains("sk-live"))
        assertFalse(stored.redactedArguments.toString().contains("hunter2"))
        assertFalse(stored.outputPreview.contains("abcdefghijklmnop"))
        assertTrue(stored.redactedArguments.toString().contains("[REDACTED]"))
        assertEquals(12, trace.userChars)
    }

    @Test
    fun assistantTextIsCountedButNeverStored() {
        val recorder = AiAgentTraceRecorder()
        val session = recorder.startSession("conversation-1")
        session.onAssistantDelta("secret-plans-abc")
        session.onTerminal(AiAgentTraceOutcome.COMPLETED)

        val trace = recorder.get(session.traceId)!!
        assertEquals(16, trace.assistantChars)
        assertFalse(trace.toString().contains("secret-plans-abc"))
    }

    @Test
    fun engineFeedsTraceSinkAndOutcome() = runTest {
        val recorder = AiAgentTraceRecorder()
        val session = recorder.startSession("conversation-1", userChars = 4)
        val provider = TurnTraceProvider()
        val engine = AiConversationEngine(
            registry = AiProviderRegistry(listOf(provider)),
            toolExecutor = FakeTraceTools(),
            maxToolIterations = 3,
            traceSink = session
        )

        engine.stream(
            "conversation-1",
            listOf(AiConversationMessage(AiRole.USER, "list"))
        ).toList()

        val trace = recorder.get(session.traceId)!!
        assertEquals(AiAgentTraceOutcome.COMPLETED, trace.outcome)
        assertEquals(6, trace.assistantChars)
        assertEquals(1, trace.toolCalls.size)
        assertEquals("nexaflow.list_tasks", trace.toolCalls.single().name)
        assertFalse(trace.toolCalls.single().isError)
    }

    @Test
    fun replayAnalyzerSummarizesWithoutSideEffects() {
        val trace = AiAgentTraceV1(
            traceId = "trace-1",
            conversationId = "conversation-1",
            startedAt = 0L,
            endedAt = 1L,
            outcome = AiAgentTraceOutcome.FAILED,
            toolCalls = listOf(
                AiTracedToolCallV1(
                    callId = "call-1",
                    name = "nexaflow.validate_task",
                    redactedArguments = buildJsonObject {},
                    isError = true,
                    outputPreview = "{}",
                    outputChars = 2
                )
            )
        )

        val analysis = AiTraceReplayAnalyzer.analyze(trace)

        assertEquals(listOf("nexaflow.validate_task"), analysis.errorToolNames)
        assertEquals("re-validate fixed draft", analysis.suggestedNextStep)
        assertTrue(analysis.findings.any { it.startsWith("draft_invalid") })
    }

    @Test
    fun storeEvictsOldestTracesFirst() {
        val recorder = AiAgentTraceRecorder(capacity = 2)
        repeat(3) { index ->
            val session = recorder.startSession("conversation-$index")
            session.onTerminal(AiAgentTraceOutcome.COMPLETED)
        }

        assertEquals(2, recorder.list().size)
    }

    private class TurnTraceProvider : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "fake",
                displayName = "Fake",
                capabilities = AiProviderCapabilities(toolCalling = true, local = true),
                available = true
            )
        )
        private var turns = 0

        override fun stream(request: AiProviderRequest) = flow {
            turns += 1
            if (turns == 1) {
                emit(AiProviderEvent.TextDelta("hi"))
                emit(
                    AiProviderEvent.ToolCall(
                        AiToolCall("call-1", "nexaflow.list_tasks", buildJsonObject {})
                    )
                )
                emit(AiProviderEvent.Finished())
            } else {
                emit(AiProviderEvent.TextDelta("done"))
                emit(AiProviderEvent.Finished())
            }
        }
    }

    private class FakeTraceTools : AiToolExecutor {
        override val tools = MutableStateFlow(
            listOf(AiToolDefinition("nexaflow.list_tasks", "List", buildJsonObject {}))
        )

        override suspend fun execute(call: AiToolCall): AiToolResult =
            AiToolResult(call.id, call.name, buildJsonObject { put("tasks", 0) })
    }
}
