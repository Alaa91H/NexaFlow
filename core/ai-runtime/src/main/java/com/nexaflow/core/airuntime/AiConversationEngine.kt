package com.nexaflow.core.airuntime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.put

class AiConversationEngine(
    private val registry: AiProviderRegistry,
    private val toolExecutor: AiToolExecutor = EmptyAiToolExecutor,
    private val turnTimeoutMillis: Long = DEFAULT_TURN_TIMEOUT_MS,
    private val maxToolIterations: Int = DEFAULT_MAX_TOOL_ITERATIONS,
    private val traceSink: AiAgentTraceSink? = null,
    private val allowTurn: () -> Boolean = { true }
) {
    fun stream(
        conversationId: String,
        messages: List<AiConversationMessage>,
        providerIdOverride: String? = null,
        allowCloudProvider: Boolean = true,
        outputCharacterLimit: Int = MAX_OUTPUT_CHARACTERS,
        toolCallLimitPerTurn: Int = MAX_TOOL_CALLS_PER_TURN
    ): Flow<AiConversationEvent> = flow {
        if (!validConversationId(conversationId) || !validMessages(messages) ||
            outputCharacterLimit !in 1..MAX_OUTPUT_CHARACTERS ||
            toolCallLimitPerTurn !in 1..MAX_TOOL_CALLS_PER_TURN
        ) {
            emit(AiConversationEvent.Failed("invalid_conversation"))
            traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "invalid_conversation")
            return@flow
        }

        val provider = registry.routeProvider(
            requirements = AiRoutingRequirements(
                requireTools = toolExecutor.tools.value.isNotEmpty()
            ),
            preferredProviderId = providerIdOverride,
            allowCloudProvider = allowCloudProvider
        )
        if (provider == null || !provider.descriptor.value.available) {
            emit(AiConversationEvent.Unavailable("no_provider"))
            traceSink?.onTerminal(AiAgentTraceOutcome.UNAVAILABLE, "no_provider")
            return@flow
        }

        val transcript = messages.toMutableList()
        var iteration = 0
        var totalAssistantCharacters = 0
        while (iteration < maxToolIterations) {
            if (!allowTurn()) {
                emit(AiConversationEvent.Failed("turn_limit"))
                traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "turn_limit")
                return@flow
            }
            iteration += 1
            val pendingCalls = mutableListOf<AiToolCall>()
            val assistantText = StringBuilder()
            var providerContext = kotlinx.serialization.json.JsonObject(emptyMap())
            val structuredMode = isStructuredFallback(provider)
            try {
                withTimeout(turnTimeoutMillis) {
                    provider.stream(
                        AiProviderRequest(
                            conversationId = conversationId,
                            messages = if (structuredMode) {
                                transcript.toList() + structuredInstruction()
                            } else {
                                transcript.toList()
                            },
                            tools = toolExecutor.tools.value,
                            maxOutputCharacters = outputCharacterLimit - totalAssistantCharacters
                        )
                    ).collect { event ->
                        when (event) {
                            is AiProviderEvent.TextDelta -> {
                                if (
                                totalAssistantCharacters + assistantText.length + event.text.length >
                                    outputCharacterLimit
                                ) {
                                    throw OutputLimitException()
                                }
                                assistantText.append(event.text)
                                totalAssistantCharacters += event.text.length
                                emit(AiConversationEvent.AssistantDelta(event.text))
                                traceSink?.onAssistantDelta(event.text)
                            }
                            is AiProviderEvent.ToolCall -> {
                                if (
                                    pendingCalls.size >= toolCallLimitPerTurn ||
                                    event.call.name.length > MAX_TOOL_NAME_LENGTH ||
                                    event.call.arguments.toString().length >
                                    MAX_TOOL_ARGUMENT_CHARACTERS
                                ) {
                                    throw ToolLimitException()
                                }
                                pendingCalls += event.call
                            }
                            is AiProviderEvent.WireContext -> {
                                if (event.context.toString().length > MAX_PROVIDER_CONTEXT_CHARACTERS) {
                                    throw ProviderContextLimitException()
                                }
                                providerContext = event.context
                            }
                            is AiProviderEvent.Finished -> Unit
                        }
                    }
                }
                registry.recordProviderSuccess(provider.descriptor.value.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutputLimitException) {
                emit(AiConversationEvent.Failed("output_limit"))
                traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "output_limit")
                return@flow
            } catch (_: ToolLimitException) {
                emit(AiConversationEvent.Failed("tool_limit"))
                traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "tool_limit")
                return@flow
            } catch (_: ProviderContextLimitException) {
                emit(AiConversationEvent.Failed("provider_context_limit"))
                traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "provider_context_limit")
                return@flow
            } catch (failure: Exception) {
                val requestFailure = failure as? AiProviderRequestException
                registry.recordProviderFailure(
                    providerId = provider.descriptor.value.id,
                    failure = requestFailure?.failure
                        ?: AiProviderFailureClassifier.fromThrowable(failure),
                    retryAfterMs = requestFailure?.retryAfterMs
                )
                emit(AiConversationEvent.Failed("provider_failure"))
                traceSink?.onTerminal(AiAgentTraceOutcome.FAILED, "provider_failure")
                return@flow
            }

            if (
                assistantText.isNotEmpty() || pendingCalls.isNotEmpty() ||
                providerContext.isNotEmpty()
            ) {
                transcript += AiConversationMessage(
                    role = AiRole.ASSISTANT,
                    text = assistantText.toString(),
                    toolCalls = pendingCalls.toList(),
                    providerContext = providerContext
                )
            }

            if (pendingCalls.isEmpty()) {
                if (structuredMode && assistantText.isNotEmpty()) {
                    pendingCalls += AiStructuredToolParser.parse(
                        assistantText.toString(),
                        toolExecutor.tools.value.mapTo(LinkedHashSet()) { it.name }
                        ).take(toolCallLimitPerTurn)
                }
            }

            if (pendingCalls.isEmpty()) {
                emit(AiConversationEvent.Completed(transcript.toList()))
                traceSink?.onTerminal(AiAgentTraceOutcome.COMPLETED)
                return@flow
            }

            for (call in pendingCalls) {
                emit(AiConversationEvent.ToolStarted(call))
                traceSink?.onToolCall(call)
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
                traceSink?.onToolResult(result)
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
        traceSink?.onTerminal(AiAgentTraceOutcome.TOOL_ITERATION_LIMIT, "tool_iteration_limit")
    }

    private fun isStructuredFallback(provider: AiModelProvider): Boolean {
        val capabilities = provider.descriptor.value.capabilities
        return !capabilities.toolCalling &&
            capabilities.structuredOutput &&
            toolExecutor.tools.value.isNotEmpty()
    }

    private fun structuredInstruction(): AiConversationMessage {
        val names = toolExecutor.tools.value
            .map { it.name }
            .sorted()
            .joinToString(", ")
            .take(MAX_STRUCTURED_INSTRUCTION_CHARS)
        return AiConversationMessage(
            role = AiRole.SYSTEM,
            text = "You cannot call functions directly. To use a NexaFlow tool, " +
                "output exactly one JSON object per call shaped " +
                "{\"tool\": \"<name>\", \"arguments\": {...}}. " +
                "Available tools: $names. Anything else is treated as plain text."
        )
    }

    private fun validConversationId(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_CONVERSATION_ID_LENGTH

    private fun validMessages(messages: List<AiConversationMessage>): Boolean {
        if (messages.isEmpty() || messages.size > MAX_MESSAGES) return false
        var total = 0
        for (message in messages) {
            val providerContextLength = message.providerContext.toString().length
            if (
                message.text.length > MAX_MESSAGE_CHARACTERS ||
                providerContextLength > MAX_PROVIDER_CONTEXT_CHARACTERS
            ) return false
            total += message.text.length + providerContextLength
            if (total > MAX_TRANSCRIPT_CHARACTERS) return false
        }
        return true
    }

    private class OutputLimitException : RuntimeException()
    private class ToolLimitException : RuntimeException()
    private class ProviderContextLimitException : RuntimeException()

    companion object {
        const val DEFAULT_MAX_TOOL_ITERATIONS = 8
        const val DEFAULT_TURN_TIMEOUT_MS = 45_000L
        const val MAX_STRUCTURED_INSTRUCTION_CHARS = 2048
        const val MAX_MESSAGES = 128
        const val MAX_MESSAGE_CHARACTERS = 32_768
        const val MAX_TRANSCRIPT_CHARACTERS = 131_072
        const val MAX_OUTPUT_CHARACTERS = 65_536
        const val MAX_TOOL_CALLS_PER_TURN = 16
        const val MAX_TOOL_ARGUMENT_CHARACTERS = 32_768
        const val MAX_PROVIDER_CONTEXT_CHARACTERS = 65_536
        const val MAX_TOOL_NAME_LENGTH = 128
        const val MAX_CONVERSATION_ID_LENGTH = 128
    }
}
