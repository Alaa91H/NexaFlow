package com.nexaflow.core.airuntime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Plan §22 - structured-JSON tool fallback for models without native
 * function calling.
 *
 * When the routed provider reports `structuredOutput` but not `toolCalling`,
 * the engine invites plain `{"tool": name, "arguments": {...}}` payloads and
 * this parser converts them into [AiToolCall]s for the exact same execution
 * pipeline native calls use. Anything that does not reference a known tool
 * with a JSON-object argument map is ignored (left as assistant text), so a
 * chatty model can never smuggle an unknown operation into execution:
 * unknown names fail closed before any validation or mutation layer.
 */
object AiStructuredToolParser {

    const val MAX_CALLS = 16
    const val MAX_ARGUMENT_CHARS = 32_768

    private val toolName = Regex("[A-Za-z0-9._:-]{1,128}")
    private val fence = Regex("```(?:json)?\\s*\\{.*?```", RegexOption.DOT_MATCHES_ALL)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Extracts up to [MAX_CALLS] calls referencing [knownTools], in textual
     * order. Code-fenced payloads are preferred; bare objects are accepted.
     */
    fun parse(text: String, knownTools: Set<String>): List<AiToolCall> {
        if (text.isBlank() || knownTools.isEmpty()) return emptyList()
        val calls = ArrayList<AiToolCall>(4)
        for (candidate in candidates(text)) {
            if (calls.size >= MAX_CALLS) break
            toCall(candidate, knownTools, calls.size)?.let(calls::add)
        }
        return calls
    }

    private fun candidates(text: String): List<String> {
        val fenced = fence.findAll(text.take(MAX_CANDIDATE_CHARS))
            .map { it.value.removePrefix("```json").removePrefix("```").removeSuffix("```") }
            .toList()
        if (fenced.isNotEmpty()) return fenced
        return braceObjects(text.take(MAX_CANDIDATE_CHARS))
    }

    /** Naively extracts top-level `{...}` spans, respecting strings/escapes. */
    private fun braceObjects(text: String): List<String> {
        val spans = ArrayList<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in text.indices) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                '}' -> {
                    if (depth > 0) {
                        depth -= 1
                        if (depth == 0 && start >= 0) {
                            spans += text.substring(start, index + 1)
                            if (spans.size >= MAX_CALLS) return spans
                            start = -1
                        }
                    }
                }
            }
        }
        return spans
    }

    private fun toCall(candidate: String, knownTools: Set<String>, index: Int): AiToolCall? {
        if (candidate.length > MAX_ARGUMENT_CHARS) return null
        val root = runCatching { json.parseToJsonElement(candidate) }.getOrNull()
        if (root !is JsonObject) return null
        val name = (root["tool"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { toolName.matches(it) && it in knownTools }
            ?: return null
        val arguments = root["arguments"] as? JsonObject ?: return null
        if (arguments.toString().length > MAX_ARGUMENT_CHARS) return null
        return AiToolCall(id = "structured-${index + 1}", name = name, arguments = arguments)
    }

    private const val MAX_CANDIDATE_CHARS = 65_536
}
