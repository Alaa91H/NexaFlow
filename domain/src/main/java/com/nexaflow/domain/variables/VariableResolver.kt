package com.nexaflow.domain.variables

/**
 * Tasker-style variable substitution engine (pure Kotlin, JVM-testable).
 *
 * A placeholder is `%NAME` — a `%` followed by a name that starts with a
 * letter or underscore and continues with letters, digits or underscores.
 * Lookup is case-insensitive (Tasker convention keeps globals UPPERCASE but
 * users often type them in lowercase), and any placeholder that has no match
 * in the provided map is left untouched — exactly like Tasker leaves unknown
 * variables as-is. `%%` escapes a literal percent sign.
 *
 * The [variables] map should carry both built-in/device-context values
 * (e.g. `DATE`, `TIME`, `BATTERY`) and user-defined globals. Locals are just
 * entries in the same map resolved at run time.
 */
object VariableResolver {

    private val PLACEHOLDER = Regex("%([A-Za-z_][A-Za-z0-9_]*)")
    private const val ESCAPED_PERCENT = "\u0000"

    /**
     * Replaces every known `%NAME` placeholder in [text] with its value from
     * [variables]. Unknown placeholders and escaped `%%` are preserved.
     */
    fun resolve(text: String, variables: Map<String, String>): String {
        if (text.indexOf('%') < 0) return text
        val lookup = HashMap<String, String>(variables.size * 2)
        variables.forEach { (name, value) -> lookup[name.lowercase()] = value }
        val unescaped = text.replace("%%", ESCAPED_PERCENT)
        return PLACEHOLDER.replace(unescaped) { match ->
            lookup[match.groupValues[1].lowercase()] ?: match.value
        }.replace(ESCAPED_PERCENT, "%")
    }

    /**
     * The distinct placeholder names referenced in [text], in order of first
     * appearance. Used by the editor to show which variables a text uses.
     */
    fun referencedPlaceholders(text: String): List<String> {
        if (text.indexOf('%') < 0) return emptyList()
        val names = LinkedHashSet<String>()
        PLACEHOLDER.findAll(text).forEach { names.add(it.groupValues[1]) }
        return names.toList()
    }
}
