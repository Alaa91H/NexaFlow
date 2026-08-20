package com.nexaflow.core.engine

import android.os.Bundle
import com.nexaflow.core.execution.plugin.PluginValueRuntimeAdapter
import com.nexaflow.core.pluginsdk.PluginValue
import com.nexaflow.domain.variables.RuntimeValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Bounded, JSON-safe conversion result for an external plugin event payload. */
sealed interface PluginEventPayloadConversion {
    data class Accepted(val payload: JsonObject) : PluginEventPayloadConversion
    data class Rejected(val reason: String) : PluginEventPayloadConversion
}

/**
 * The sole Android Bundle boundary for plugin event payloads. It first converts
 * accepted primitives into the closed [PluginValue] algebra, then delegates
 * depth/item/string validation to [PluginValueRuntimeAdapter] before building
 * JSON for [com.nexaflow.domain.events.NexaFlowEvent].
 */
object PluginEventPayloadAdapter {
    private const val MAX_TOP_LEVEL_KEYS = 64
    private const val MAX_ARRAY_ITEMS = 128

    fun toJson(bundle: Bundle?): PluginEventPayloadConversion {
        if (bundle == null || bundle.isEmpty) return PluginEventPayloadConversion.Accepted(JsonObject(emptyMap()))
        val keys = bundle.keySet().sorted()
        if (keys.size > MAX_TOP_LEVEL_KEYS) {
            return PluginEventPayloadConversion.Rejected("Plugin event payload has too many keys")
        }
        val values = LinkedHashMap<String, PluginValue>(keys.size)
        for (key in keys) {
            val parsed = toPluginValue(key, rawValue(bundle, key))
                ?: return PluginEventPayloadConversion.Rejected("Plugin event payload contains unsupported value for '$key'")
            values[key] = parsed
        }
        val conversion = PluginValueRuntimeAdapter.toRuntime(PluginValue.MapValue(values))
        val runtime = conversion.value
            ?: return PluginEventPayloadConversion.Rejected("Plugin event payload violates value limits: ${conversion.issue}")
        val root = runtime as? RuntimeValue.ObjectValue
            ?: return PluginEventPayloadConversion.Rejected("Plugin event payload is not an object")
        val payload = root.values.mapValues { (_, value) -> value.toJsonElement() }
        return PluginEventPayloadConversion.Accepted(JsonObject(payload))
    }

    @Suppress("DEPRECATION")
    private fun rawValue(bundle: Bundle, key: String): Any? = bundle.get(key)

    private fun toPluginValue(key: String, value: Any?): PluginValue? {
        if (key.isBlank() || key.length > RuntimeValue.MAX_OBJECT_KEY_LENGTH) return null
        return when (value) {
            is String -> PluginValue.StringValue(value)
            is Boolean -> PluginValue.BooleanValue(value)
            is Int -> PluginValue.IntegerValue(value)
            is Long -> PluginValue.LongValue(value)
            is Float -> PluginValue.FloatValue(value)
            is Double -> PluginValue.DoubleValue(value)
            is Array<*> -> arrayToPluginValue(value.asList())
            is ArrayList<*> -> arrayToPluginValue(value)
            else -> null
        }
    }

    private fun arrayToPluginValue(values: List<*>): PluginValue? {
        if (values.size > MAX_ARRAY_ITEMS) return null
        val converted = values.map { value ->
            when (value) {
                is String -> PluginValue.StringValue(value)
                is Boolean -> PluginValue.BooleanValue(value)
                is Int -> PluginValue.IntegerValue(value)
                is Long -> PluginValue.LongValue(value)
                is Float -> PluginValue.FloatValue(value)
                is Double -> PluginValue.DoubleValue(value)
                else -> return null
            }
        }
        return PluginValue.ListValue(converted)
    }

    private fun RuntimeValue.toJsonElement(): JsonElement = when (this) {
        RuntimeValue.NullValue -> JsonNull
        is RuntimeValue.StringValue -> JsonPrimitive(value)
        is RuntimeValue.BooleanValue -> JsonPrimitive(value)
        is RuntimeValue.IntValue -> JsonPrimitive(value)
        is RuntimeValue.LongValue -> JsonPrimitive(value)
        is RuntimeValue.DoubleValue -> JsonPrimitive(value)
        is RuntimeValue.ListValue -> JsonArray(values.map { it.toJsonElement() })
        is RuntimeValue.ObjectValue -> JsonObject(values.mapValues { (_, value) -> value.toJsonElement() })
    }
}
