package com.nexaflow.core.airuntime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class AiConversationEngine(
    private val registry: AiProviderRegistry,
    private val toolExecutor: AiToolExecutor = EmptyAiToolExecutor,
    private val turnTimeoutMillis: Long = DEFAULT_TURN_TIMEOUT_MS,
    private val maxToolIterations: Int = DEFAULT_MAX_TOOL_ITERATIONS
) {
    fun stream(
        conversationId: String,
        messages: List<AiConversationMessage>
    ): Flow<AiConversationEvent> = flow {
        if (!validConversationId(conversationId) || !validMessages(messages)) {
            emit(AiConversationEvent.Failed("invalid_conversation"))
            return@flow
        }

        val provider = registry.selectedProvider()
        if (provider == null || !provider.descriptor.value.available) {
            emit(AiConversationEvent.Unavailable("no_provider"))
            return@flow
        }

        val transcript = messages.toMutableList()
        var iteration = 0
        while (iteration < maxToolIterations) {
            iteration += 1
            val pendingCalls = mutableListOf<AiToolCall>()
            val assistantText = StringBuilder()
            try {
                withTimeout(turnTimeoutMillis) {
                    provider.stream(
                        AiProviderRequest(
                            conversationId = conversationId,
                            messages = transcript.toList(),
                            tools = toolExecutor.tools.value,
                            maxOutputCharacters = MAX_OUTPUT_CHARACTERS
                        )
                    ).collect { event ->
                        when (event) {
                            is AiProviderEvent.TextDelta -> {
                                if (
                                    assistantText.length + event.text.length >
                                    MAX_OUTPUT_CHARACTERS
                                ) {
                                    throw OutputLimitException()
                                }
                                assistantText.append(event.text)
                                emit(AiConversationEvent.AssistantDelta(event.text))
                            }
                            is AiProviderEvent.ToolCall -> {
                                if (
                                    pendingCalls.size >= MAX_TOOL_CALLS_PER_TURN ||
                                    event.call.name.length > MAX_TOOL_NAME_LENGTH ||
                                    event.call.arguments.toString().length >
                                    MAX_TOOL_ARGUMENT_CHARACTERS
                                ) {
                                    throw ToolLimitException()
                                }
                                pendingCalls += event.call
                            }
                            is AiProviderEvent.Finished -> Unit
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutputLimitException) {
                emit(AiConversationEvent.Failed("output_limit"))
                return@flow
            } catch (_: ToolLimitException) {
                emit(AiConversationEvent.Failed("tool_limit"))
                return@flow
            } catch (_: Exception) {
                emit(AiConversationEvent.Failed("provider_failure"))
                return@flow
            }

            if (assistantText.isNotEmpty()) {
                transcript += AiConversationMessage(
                    role = AiRole.ASSISTANT,
                    text = assistantText.toString()
                )
            }

            if (pendingCalls.isEmpty()) {
                emit(AiConversationEvent.Completed(transcript.toList()))
                return@flow
            }

            for (call in pendingCalls) {
                emit(AiConversationEvent.ToolStarted(call))
                val result = try {
                    toolExecutor.execute(call)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AiToolResult(
                        callId = call.id,
                        toolName = call.name,
                        output = kotlinx.serialization.json.buildJsonObject {
                            put("error", "tool_execution_failed")
                        },
                        isError = true
                    )
                }
                emit(AiConversationEvent.ToolFinished(result))
                transcript += AiConversationMessage(
                    role = AiRole.ASSISTANT,
                    text = "",
                    toolCallId = call.id,
                    toolName = call.name
                )
                transcript += AiConversationMessage(
                    role = AiRole.TOOL,
                    text = result.output.toString(),
                    toolCallId = result.callId,
                    toolName = result.toolName,
                    isError = result.isError
                )
            }
        }

        emit(AiConversationEvent.Failed("tool_iteration_limit"))
    }

    private fun validConversationId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_CONVERSATION_ID_LENGTH

    private fun validMessages(messages: List<AiConversationMessage>): Boolean {
        if (messages.isEmpty() || messages.size > MAX_MESSAGES) return false
        var total = 0
        for (message in messages) {
            if (message.text.length > MAX_MESSAGE_CHARACTERS) return false
            total += message.text.length
            if (total > MAX_TRANSCRIPT_CHARACTERS) return false
        }
        return true
    }

    private class OutputLimitException : RuntimeException()
    private class ToolLimitException : RuntimeException()

    companion object {
        const val DEFAULT_MAX_TOOL_ITERATIONS = 8
        const val DEFAULT_TURN_TIMEOUT_MS = 45_000L
        const val MAX_MESSAGES = 128
        const val MAX_MESSAGE_CHARACTERS = 32_768
        const val MAX_TRANSCRIPT_CHARACTERS = 131_072
        const val MAX_OUTPUT_CHARACTERS = 65_536
        const val MAX_TOOL_CALLS_PER_TURN = 16
        const val MAX_TOOL_ARGUMENT_CHARACTERS = 32_768
        const val MAX_TOOL_NAME_LENGTH = 128
        const val MAX_CONVERSATION_ID_LENGTH = 128
    }
}
