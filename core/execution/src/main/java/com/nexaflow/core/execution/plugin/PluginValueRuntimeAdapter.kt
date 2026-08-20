package com.nexaflow.core.execution.plugin

import com.nexaflow.core.pluginsdk.PluginValue
import com.nexaflow.domain.variables.RuntimeValue

/** Result of a bounded external-value conversion. */
data class PluginValueConversion(
    val value: RuntimeValue? = null,
    val issue: PluginValueConversionIssue? = null
) {
    val isSuccess: Boolean get() = value != null && issue == null
}

enum class PluginValueConversionIssue {
    MAX_DEPTH_EXCEEDED,
    MAX_ITEMS_EXCEEDED,
    STRING_TOO_LONG,
    INVALID_MAP_KEY,
    NON_FINITE_NUMBER
}

/**
 * Converts only the closed `PluginValue` algebra into the closed runtime value
 * algebra. The adapter has no Android dependencies and cannot accept Bundle,
 * Parcelable, Serializable, binder, or arbitrary plugin class instances.
 */
object PluginValueRuntimeAdapter {
    const val DEFAULT_MAX_DEPTH = 8
    const val DEFAULT_MAX_ITEMS = 256
    const val DEFAULT_MAX_STRING_LENGTH = 4_096

    fun toRuntime(
        value: PluginValue,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxItems: Int = DEFAULT_MAX_ITEMS,
        maxStringLength: Int = DEFAULT_MAX_STRING_LENGTH
    ): PluginValueConversion {
        require(maxDepth in 1..32) { "maxDepth must be in 1..32" }
        require(maxItems in 1..4_096) { "maxItems must be in 1..4096" }
        require(maxStringLength in 1..16_384) { "maxStringLength must be in 1..16384" }
        var itemCount = 0

        fun convert(input: PluginValue, depth: Int): PluginValueConversion {
            if (depth > maxDepth) return PluginValueConversion(issue = PluginValueConversionIssue.MAX_DEPTH_EXCEEDED)
            itemCount++
            if (itemCount > maxItems) return PluginValueConversion(issue = PluginValueConversionIssue.MAX_ITEMS_EXCEEDED)
            return when (input) {
                is PluginValue.StringValue -> {
                    if (input.value.length > maxStringLength) {
                        PluginValueConversion(issue = PluginValueConversionIssue.STRING_TOO_LONG)
                    } else {
                        PluginValueConversion(RuntimeValue.StringValue(input.value))
                    }
                }
                is PluginValue.BooleanValue -> PluginValueConversion(RuntimeValue.BooleanValue(input.value))
                is PluginValue.IntegerValue -> PluginValueConversion(RuntimeValue.IntValue(input.value))
                is PluginValue.LongValue -> PluginValueConversion(RuntimeValue.LongValue(input.value))
                is PluginValue.FloatValue -> {
                    if (input.value.isFinite()) PluginValueConversion(RuntimeValue.DoubleValue(input.value.toDouble()))
                    else PluginValueConversion(issue = PluginValueConversionIssue.NON_FINITE_NUMBER)
                }
                is PluginValue.DoubleValue -> {
                    if (input.value.isFinite()) PluginValueConversion(RuntimeValue.DoubleValue(input.value))
                    else PluginValueConversion(issue = PluginValueConversionIssue.NON_FINITE_NUMBER)
                }
                is PluginValue.ListValue -> {
                    val values = ArrayList<RuntimeValue>(input.value.size)
                    input.value.forEach { child ->
                        val converted = convert(child, depth + 1)
                        val runtime = converted.value ?: return converted
                        values += runtime
                    }
                    PluginValueConversion(RuntimeValue.ListValue(values))
                }
                is PluginValue.MapValue -> {
                    if (input.value.keys.any { it.isBlank() || it.length > RuntimeValue.MAX_OBJECT_KEY_LENGTH }) {
                        return PluginValueConversion(issue = PluginValueConversionIssue.INVALID_MAP_KEY)
                    }
                    val values = LinkedHashMap<String, RuntimeValue>(input.value.size)
                    input.value.forEach { (key, child) ->
                        val converted = convert(child, depth + 1)
                        val runtime = converted.value ?: return converted
                        values[key] = runtime
                    }
                    PluginValueConversion(RuntimeValue.ObjectValue(values))
                }
            }
        }

        return convert(value, depth = 1)
    }
}
