package com.nexaflow.domain.variables

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Closed, JSON-safe value algebra for runtime variables. It intentionally
 * excludes Android objects, functions, file handles, and arbitrary classes so
 * variables can be serialized, compared, and persisted deterministically.
 */
@Serializable
sealed interface RuntimeValue {
    @Serializable
    @SerialName("null")
    data object NullValue : RuntimeValue

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : RuntimeValue

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : RuntimeValue

    @Serializable
    @SerialName("int")
    data class IntValue(val value: Int) : RuntimeValue

    @Serializable
    @SerialName("long")
    data class LongValue(val value: Long) : RuntimeValue

    @Serializable
    @SerialName("double")
    data class DoubleValue(val value: Double) : RuntimeValue {
        init {
            require(value.isFinite()) { "RuntimeValue.DoubleValue must be finite" }
        }
    }

    @Serializable
    @SerialName("list")
    data class ListValue(val values: List<RuntimeValue>) : RuntimeValue

    @Serializable
    @SerialName("object")
    data class ObjectValue(val values: Map<String, RuntimeValue>) : RuntimeValue {
        init {
            require(values.keys.all(::isValidObjectKey)) {
                "Runtime object keys must be non-blank and under $MAX_OBJECT_KEY_LENGTH characters"
            }
        }
    }

    companion object {
        const val MAX_OBJECT_KEY_LENGTH = 256

        private fun isValidObjectKey(key: String): Boolean =
            key.isNotBlank() && key.length <= MAX_OBJECT_KEY_LENGTH
    }
}

/** Persistence scopes. Only GLOBAL is durable in the initial repository layer. */
@Serializable
enum class VariableScope {
    GLOBAL,
    WORKFLOW,
    EXECUTION,
    NODE,
    ACTION
}

/** A typed variable including scope/version metadata for snapshots and debugging. */
@Immutable
@Serializable
data class RuntimeVariable(
    val name: String,
    val value: RuntimeValue,
    val scope: VariableScope,
    val version: Long = 1L,
    val sensitive: Boolean = false
) {
    init {
        require(VARIABLE_NAME.matches(name)) {
            "Variable name must start with a letter or underscore and contain only letters, digits or underscores"
        }
        require(version >= 1L) { "Variable version must be at least one" }
    }

    companion object {
        val VARIABLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/** Immutable checkpoint of one scope. Secrets remain references/secure-store entries. */
@Immutable
@Serializable
data class VariableSnapshot(
    val scope: VariableScope,
    val variables: List<RuntimeVariable>,
    val capturedAt: Long,
    val schemaVersion: Int = 1
) {
    init {
        require(schemaVersion >= 1) { "Variable snapshot schemaVersion must be positive" }
        require(variables.map { it.name.lowercase() }.distinct().size == variables.size) {
            "Variable snapshot contains duplicate names"
        }
    }
}

/** Central codec for typed values persisted by the existing VariableRepository. */
object RuntimeValueCodec {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(value: RuntimeValue): String = json.encodeToString(RuntimeValue.serializer(), value)

    fun decode(serialized: String): RuntimeValue =
        json.decodeFromString(RuntimeValue.serializer(), serialized)

    /** Stable string substitution for the legacy `%NAME` action-text contract. */
    fun display(value: RuntimeValue): String = when (value) {
        RuntimeValue.NullValue -> ""
        is RuntimeValue.StringValue -> value.value
        is RuntimeValue.BooleanValue -> value.value.toString()
        is RuntimeValue.IntValue -> value.value.toString()
        is RuntimeValue.LongValue -> value.value.toString()
        is RuntimeValue.DoubleValue -> value.value.toString()
        is RuntimeValue.ListValue, is RuntimeValue.ObjectValue -> encode(value)
    }

    /** Existing persisted strings are valid legacy text values, not malformed state. */
    fun decodeOrLegacyText(serialized: String): RuntimeValue =
        runCatching { decode(serialized) }.getOrElse { RuntimeValue.StringValue(serialized) }
}
