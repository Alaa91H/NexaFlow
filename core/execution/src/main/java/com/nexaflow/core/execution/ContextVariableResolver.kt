package com.nexaflow.core.execution

/**
 * Resolves `%CTX.<jsonpath>` reference placeholders against a
 * [WorkflowRunContext] (Step 5, Appendix A.4.1).
 *
 * A node A that published its outcome via `outputPath` (e.g. at
 * `$.fetch.result`) can feed a later node B by referencing that path in any
 * text-bearing config value:
 *
 * ```
 * node A:  HttpRequestHandler with outputPath = "$.fetch.result"
 *          → context contains {status: 200, body: "..."} at that path
 * node B:  body = "{ \"temp\": %CTX.$.fetch.result.body }"
 *          → resolved by the engine BEFORE the handler sees it
 * ```
 *
 * Syntax mirrors [com.nexaflow.domain.variables.VariableResolver]: a `%CTX.`
 * prefix (case-insensitive) followed by a JSONPath that must start with `$`.
 * Unknown or missing paths are left untouched — exactly like unknown `%NAME`
 * variables — so a path that only exists at run time degrades gracefully.
 * `%%` escapes a literal percent sign (handled by VariableResolver first, so
 * this resolver only sees real `%CTX.` occurrences).
 *
 * Values are stringified for substitution: scalars via toString, maps and
 * lists as compact JSON. Pure JVM (no Android imports) — atomically testable.
 */
object ContextVariableResolver {

    // %CTX.<path> matching the JSONPath subset WorkflowRunContext accepts: $
    // followed by any number of `.key` or `[index]` groups. A key is a letter
    // or underscore then letters/digits/underscores — dash is deliberately NOT
    // a continuation char so `-`, `,`, `]`, `}` etc. in the surrounding text
    // cleanly terminate a reference (a node output is `status`/`body`; keys
    // containing dashes are not resolvable through this selector DSL).
    private val REFERENCE = Regex(
        """%CTX\.(\$(?:\.[A-Za-z_][A-Za-z0-9_]*|\[\d+\])*)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Replaces every `%CTX.<jsonpath>` in [text] with the value read from
     * [context] at that path, stringified. Missing paths (or a null stored
     * value) keep the placeholder untouched.
     */
    fun resolve(text: String, context: WorkflowRunContext): String {
        if (text.indexOf('%') < 0) return text
        return REFERENCE.replace(text) { match ->
            val path = match.groupValues[1]
            val value = runCatching { context.get(path) }.getOrNull()
            if (value == null) match.value else stringify(value)
        }
    }

    /** The distinct `%CTX.` JSONPaths referenced in [text], in order. */
    fun referencedPaths(text: String): List<String> {
        if (text.indexOf('%') < 0) return emptyList()
        val paths = LinkedHashSet<String>()
        REFERENCE.findAll(text).forEach { paths.add(it.groupValues[1]) }
        return paths.toList()
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Boolean, is Number -> value.toString()
        is Map<*, *> -> compactJson(value)
        is List<*> -> compactJson(value)
        else -> value.toString()
    }

    /** Minimal compact JSON renderer for maps/lists of scalars, maps, lists. */
    private fun compactJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean, is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${compactJson(v)}"
        }
        is List<*> -> value.joinToString(",", "[", "]") { compactJson(it) }
        else -> "\"$value\""
    }
}
