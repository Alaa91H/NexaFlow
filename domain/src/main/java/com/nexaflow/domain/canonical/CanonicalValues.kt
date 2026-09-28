package com.nexaflow.domain.canonical

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Runtime-independent type tag used by schema/type checking in later phases. */
@Serializable
enum class CanonicalValueKind {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    TEXT,
    PERCENTAGE,
    DURATION_MS,
    TIMESTAMP_MS,
    TIME_OF_DAY,
    DATE,
    TIMEZONE_ID,
    PACKAGE_ID,
    URI,
    COORDINATE,
    ENUM_TOKEN,
    JSON,
    SECRET_REFERENCE,
    COLLECTION,
    EXPRESSION
}

/**
 * Persistable typed value. No canonical node stores untyped string
 * configuration maps; strings only appear inside explicitly typed values.
 */
@Serializable
sealed interface CanonicalValue {
    val kind: CanonicalValueKind
}

@Serializable
@SerialName("boolean")
data class BooleanValue(val value: Boolean) : CanonicalValue {
    override val kind: CanonicalValueKind = CanonicalValueKind.BOOLEAN
}

@Serializable
@SerialName("integer")
data class IntegerValue(val value: Long) : CanonicalValue {
    override val kind: CanonicalValueKind = CanonicalValueKind.INTEGER
}

@Serializable
@SerialName("decimal")
data class DecimalValue(val value: String) : CanonicalValue {
    init {
        require(value.toBigDecimalOrNull() != null) { "DecimalValue must contain a finite decimal" }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.DECIMAL
}

@Serializable
@SerialName("text")
data class TextValue(val value: String) : CanonicalValue {
    init { require(value.length <= MAX_TEXT_LENGTH) { "TextValue exceeds $MAX_TEXT_LENGTH characters" } }
    override val kind: CanonicalValueKind = CanonicalValueKind.TEXT

    private companion object {
        const val MAX_TEXT_LENGTH = 65_536
    }
}

@Serializable
@SerialName("percentage")
data class PercentageValue(val value: String) : CanonicalValue {
    init {
        val decimal = value.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("PercentageValue must contain a decimal")
        require(decimal >= BigDecimal.ZERO && decimal <= BigDecimal("100")) {
            "PercentageValue must be in 0..100"
        }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.PERCENTAGE
}

@Serializable
@SerialName("duration_ms")
data class DurationValue(val milliseconds: Long) : CanonicalValue {
    init { require(milliseconds >= 0L) { "DurationValue must be non-negative" } }
    override val kind: CanonicalValueKind = CanonicalValueKind.DURATION_MS
}

@Serializable
@SerialName("timestamp_ms")
data class TimestampValue(val epochMilliseconds: Long) : CanonicalValue {
    override val kind: CanonicalValueKind = CanonicalValueKind.TIMESTAMP_MS
}

@Serializable
@SerialName("time_of_day")
data class TimeOfDayValue(val minuteOfDay: Int) : CanonicalValue {
    init { require(minuteOfDay in 0..1439) { "minuteOfDay must be in 0..1439" } }
    override val kind: CanonicalValueKind = CanonicalValueKind.TIME_OF_DAY
}

@Serializable
@SerialName("date")
data class DateValue(val isoDate: String) : CanonicalValue {
    init {
        runCatching { LocalDate.parse(isoDate) }
            .getOrElse { throw IllegalArgumentException("DateValue must use ISO-8601 yyyy-MM-dd", it) }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.DATE
}

@Serializable
@SerialName("timezone")
data class TimezoneValue(val zoneId: String) : CanonicalValue {
    init {
        runCatching { ZoneId.of(zoneId) }
            .getOrElse { throw IllegalArgumentException("Unknown timezone: $zoneId", it) }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.TIMEZONE_ID
}

@Serializable
@SerialName("package")
data class PackageIdValue(val packageName: String) : CanonicalValue {
    init {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid Android package name" }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.PACKAGE_ID

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}

@Serializable
@SerialName("uri")
data class UriValue(val value: String) : CanonicalValue {
    init {
        require(value.length in 1..4096) { "UriValue length must be in 1..4096" }
        require(URI_SCHEME.containsMatchIn(value)) { "UriValue must contain an explicit URI scheme" }
        require(!value.any(Char::isWhitespace)) { "UriValue must not contain whitespace" }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.URI

    private companion object {
        val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }
}

@Serializable
@SerialName("coordinate")
data class CoordinateValue(
    val latitude: Double,
    val longitude: Double
) : CanonicalValue {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "latitude must be finite and in -90..90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "longitude must be finite and in -180..180" }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.COORDINATE
}

@Serializable
@SerialName("enum_token")
data class EnumTokenValue(
    val enumType: String,
    val token: String
) : CanonicalValue {
    init {
        require(TYPE_ID.matches(enumType)) { "Invalid enum type id" }
        require(TOKEN.matches(token)) { "Invalid enum token" }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.ENUM_TOKEN

    private companion object {
        val TYPE_ID = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
        val TOKEN = Regex("[A-Z][A-Z0-9_]{0,127}")
    }
}

@Serializable
@SerialName("json")
data class JsonValue(val value: JsonElement) : CanonicalValue {
    override val kind: CanonicalValueKind = CanonicalValueKind.JSON
}

/**
 * Secrets never live directly in the workflow AST. The reference is resolved
 * through a protected secret store at execution time.
 */
@Serializable
@SerialName("secret_reference")
data class SecretReferenceValue(val referenceId: String) : CanonicalValue {
    init { require(REFERENCE.matches(referenceId)) { "Invalid secret reference id" } }
    override val kind: CanonicalValueKind = CanonicalValueKind.SECRET_REFERENCE

    private companion object {
        val REFERENCE = Regex("[a-z][a-z0-9_.-]{2,127}")
    }
}

@Serializable
@SerialName("collection")
data class CollectionValue(
    val elementKind: CanonicalValueKind,
    val values: List<CanonicalValue>
) : CanonicalValue {
    init {
        require(values.size <= MAX_ITEMS) { "CollectionValue exceeds $MAX_ITEMS items" }
        require(values.all { it.kind == elementKind }) {
            "CollectionValue elements must all match elementKind"
        }
        require(elementKind != CanonicalValueKind.COLLECTION) {
            "Nested collections are intentionally unsupported in canonical v1"
        }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.COLLECTION

    private companion object {
        const val MAX_ITEMS = 1024
    }
}

/**
 * Typed expression source. Parsing/evaluation belongs to later compiler work;
 * T04 only makes the expected result type explicit and bounded.
 */
@Serializable
@SerialName("expression")
data class ExpressionValue(
    val source: String,
    val resultKind: CanonicalValueKind
) : CanonicalValue {
    init {
        require(source.isNotBlank()) { "Expression source must not be blank" }
        require(source.length <= MAX_EXPRESSION_LENGTH) {
            "Expression source exceeds $MAX_EXPRESSION_LENGTH characters"
        }
        require(resultKind != CanonicalValueKind.EXPRESSION) {
            "Expression resultKind cannot itself be EXPRESSION"
        }
    }
    override val kind: CanonicalValueKind = CanonicalValueKind.EXPRESSION

    private companion object {
        const val MAX_EXPRESSION_LENGTH = 4096
    }
}

/** Stable field name used by canonical arguments; never a legacy config-map key. */
@Serializable
@JvmInline
value class CanonicalFieldId(val value: String) {
    init {
        require(FIELD.matches(value)) {
            "CanonicalFieldId must be lower camel/snake compatible and bounded"
        }
    }
    override fun toString(): String = value

    private companion object {
        val FIELD = Regex("[a-z][A-Za-z0-9_]{0,63}")
    }
}

@Serializable
data class CanonicalArgument(
    val id: CanonicalFieldId,
    val value: CanonicalValue
)

/**
 * Ordered, duplicate-free typed argument bag. Ordering is retained for stable
 * serialization/golden tests, but semantic lookup is by [CanonicalFieldId].
 */
@Serializable
data class CanonicalArguments(
    val entries: List<CanonicalArgument> = emptyList()
) {
    init {
        require(entries.size <= MAX_ARGUMENTS) { "Too many canonical arguments" }
        require(entries.map { it.id }.distinct().size == entries.size) {
            "Canonical argument ids must be unique"
        }
    }

    operator fun get(id: CanonicalFieldId): CanonicalValue? =
        entries.firstOrNull { it.id == id }?.value

    companion object {
        const val MAX_ARGUMENTS = 128
        val EMPTY = CanonicalArguments()
    }
}
