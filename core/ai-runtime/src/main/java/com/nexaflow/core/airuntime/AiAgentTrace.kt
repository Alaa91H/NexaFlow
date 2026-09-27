package com.nexaflow.core.airuntime

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase 16 - replayable, redacted agent traces.
 *
 * A trace records the *shape* of one agent turn (tool names, redacted
 * argument/output previews, outcome) so a failed turn can be re-analyzed
 * later. Raw prompt text, assistant text and secret values are never
 * stored: only character counts and redacted previews are kept.
 *
 * Re-analysis ([AiTraceReplayAnalyzer]) is side-effect free debugging. It
 * never re-executes tools or mutates automations.
 */
@Serializable
enum class AiAgentTraceOutcome {
    COMPLETED,
    UNAVAILABLE,
    FAILED,
    TOOL_ITERATION_LIMIT
}

@Serializable
data class AiTracedToolCallV1(
    val callId: String,
    val name: String,
    val redactedArguments: JsonObject,
    val isError: Boolean = false,
    /** Redacted output preview, bounded to [AiTraceRedactor.MAX_PREVIEW_CHARS]. */
    val outputPreview: String = "",
    val outputChars: Int = 0
)

@Serializable
data class AiAgentTraceV1(
    val traceId: String,
    val conversationId: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: AiAgentTraceOutcome,
    val outcomeDetail: String = "",
    val userChars: Int = 0,
    val assistantChars: Int = 0,
    val toolCalls: List<AiTracedToolCallV1> = emptyList()
)

/** Sink fed by [AiConversationEngine]; null-safe no-op by default. */
interface AiAgentTraceSink {
    fun onAssistantDelta(text: String)
    fun onToolCall(call: AiToolCall)
    fun onToolResult(result: AiToolResult)
    fun onTerminal(outcome: AiAgentTraceOutcome, detail: String = "")
}

object AiTraceRedactor {
    const val MAX_PREVIEW_CHARS = 2048
    const val MAX_STRING_CHARS = 512
    const val MASK = "[REDACTED]"

    private val sensitiveKey = Regex(
        "token|secret|password|passwd|api[_-]?key|authorization|credential|bearer|private[_-]?key|client[_-]?secret|refresh[_-]?token|access[_-]?token|session[_-]?key",
        RegexOption.IGNORE_CASE
    )

    fun redactElement(element: JsonElement, depth: Int = 0): JsonElement {
        if (depth > 12) return JsonPrimitive(MASK)
        return when (element) {
            is JsonObject -> JsonObject(
                element.entries.associate { (key, value) ->
                    key to if (sensitiveKey.containsMatchIn(key)) {
                        JsonPrimitive(MASK)
                    } else {
                        redactElement(value, depth + 1)
                    }
                }
            )
            is JsonArray -> JsonArray(
                element.take(64).map { redactElement(it, depth + 1) }
            )
            is JsonPrimitive -> if (element.isString) {
                val text = element.contentOrNull.orEmpty()
                JsonPrimitive(if (text.length > MAX_STRING_CHARS) text.take(MAX_STRING_CHARS) + "…" else text)
            } else {
                element
            }
            is JsonNull -> JsonNull
        }
    }

    fun preview(output: JsonElement): Pair<String, Int> {
        val text = redactElement(output).toString()
        val preview = if (text.length > MAX_PREVIEW_CHARS) {
            text.take(MAX_PREVIEW_CHARS) + "…[truncated]"
        } else {
            text
        }
        return preview to text.length
    }
}

/**
 * Bounded in-memory trace store. Newest traces evict oldest; cancelled turns
 * (no terminal signal) are dropped when their session is discarded.
 */
class AiAgentTraceRecorder(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    init {
        require(capacity in 1..MAX_CAPACITY) { "Trace capacity is out of range" }
    }

    private val lock = Any()
    private val traces = LinkedHashMap<String, AiAgentTraceV1>(capacity + 8, 0.75f, true)

    fun startSession(
        conversationId: String,
        userChars: Int = 0,
        providerId: String? = null,
        modelId: String? = null
    ): Session = Session(
        traceId = idGenerator().also { require(it.isNotBlank()) },
        conversationId = conversationId,
        userChars = userChars.coerceAtLeast(0),
        providerId = providerId?.take(128),
        modelId = modelId?.take(128),
        startedAt = clockMillis()
    )

    fun list(): List<AiAgentTraceV1> = synchronized(lock) {
        traces.values.toList()
    }

    fun get(traceId: String): AiAgentTraceV1? = synchronized(lock) {
        traces[traceId]
    }

    inner class Session(
        val traceId: String,
        private val conversationId: String,
        private val userChars: Int,
        private val providerId: String?,
        private val modelId: String?,
        private val startedAt: Long
    ) : AiAgentTraceSink {
        private val calls = ArrayList<AiTracedToolCallV1>()
        private var assistantChars = 0
        private var finished = false

        @Synchronized
        override fun onAssistantDelta(text: String) {
            if (!finished) assistantChars += text.length
        }

        @Synchronized
        override fun onToolCall(call: AiToolCall) {
            if (finished) return
            calls += AiTracedToolCallV1(
                callId = call.id.take(128),
                name = call.name.take(128),
                redactedArguments = AiTraceRedactor.redactElement(call.arguments) as? JsonObject
                    ?: JsonObject(emptyMap())
            )
        }

        @Synchronized
        override fun onToolResult(result: AiToolResult) {
            if (finished) return
            val index = calls.indexOfLast { it.callId == result.callId }
            val (preview, chars) = AiTraceRedactor.preview(result.output)
            val updated = AiTracedToolCallV1(
                callId = result.callId.take(128),
                name = result.toolName.take(128),
                redactedArguments = calls.getOrNull(index)?.redactedArguments
                    ?: JsonObject(emptyMap()),
                isError = result.isError,
                outputPreview = preview,
                outputChars = chars
            )
            if (index >= 0) calls[index] = updated else calls += updated
        }

        @Synchronized
        override fun onTerminal(outcome: AiAgentTraceOutcome, detail: String) {
            if (finished) return
            finished = true
            val trace = AiAgentTraceV1(
                traceId = traceId,
                conversationId = conversationId.take(128),
                providerId = providerId,
                modelId = modelId,
                startedAt = startedAt,
                endedAt = clockMillis(),
                outcome = outcome,
                outcomeDetail = detail.take(256),
                userChars = userChars,
                assistantChars = assistantChars,
                toolCalls = calls.toList()
            )
            synchronized(this@AiAgentTraceRecorder.lock) {
                traces[traceId] = trace
                while (traces.size > capacity) {
                    traces.remove(traces.keys.first())
                }
            }
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 64
        const val MAX_CAPACITY = 512
    }
}

/**
 * Pure re-analysis of a recorded trace. Findings are debugging hints;
 * acting on them always goes through the normal validation/mutation path.
 */
object AiTraceReplayAnalyzer {

    data class Analysis(
        val findings: List<String>,
        val errorToolNames: List<String>,
        val suggestedNextStep: String
    )

    fun analyze(trace: AiAgentTraceV1): Analysis {
        val errors = trace.toolCalls.filter { it.isError }
        val findings = ArrayList<String>()
        if (trace.toolCalls.isEmpty() && trace.outcome == AiAgentTraceOutcome.FAILED) {
            findings += "model_turn_failed_before_tool: provider failed before any tool executed (${trace.outcomeDetail})"
        }
        if (errors.isNotEmpty()) {
            findings += "tool_errors:${errors.size}: " + errors.map { it.name }.distinct().joinToString(",")
        }
        val repeatedValidations = trace.toolCalls.count {
            it.name == "nexaflow.validate_task" && it.isError
        }
        if (repeatedValidations > 0) {
            findings += "draft_invalid: validation rejected the draft; fix inputs and re-validate before create/update"
        }
        if (trace.outcome == AiAgentTraceOutcome.TOOL_ITERATION_LIMIT) {
            findings += "loop_bound_reached: turn exceeded the tool iteration bound; narrow the request"
        }
        if (trace.outcome == AiAgentTraceOutcome.UNAVAILABLE) {
            findings += "no_provider: no model provider was available (${trace.outcomeDetail})"
        }
        if (findings.isEmpty()) {
            findings += if (trace.outcome == AiAgentTraceOutcome.COMPLETED) {
                "healthy: turn completed without tool errors"
            } else {
                "no_signal: turn ended as ${trace.outcome} without tool evidence"
            }
        }
        val next = when {
            repeatedValidations > 0 -> "re-validate fixed draft"
            errors.isNotEmpty() -> "inspect failing tool inputs"
            trace.outcome == AiAgentTraceOutcome.TOOL_ITERATION_LIMIT -> "retry with narrower request"
            trace.outcome == AiAgentTraceOutcome.UNAVAILABLE -> "configure provider"
            else -> "none"
        }
        return Analysis(
            findings = findings.toList(),
            errorToolNames = errors.map { it.name }.distinct(),
            suggestedNextStep = next
        )
    }
}
