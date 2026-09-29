package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T08 — Dynamic Schema Engine (plan §11, ADR-005).
 *
 * One schema is the single source of truth for a canonical node kind:
 * fields, types, declared defaults, validation, conditional visibility,
 * conditional requirement, conflicts, capability linkage, security
 * classification, and summary formatting. Schemas are data — serializable,
 * deterministic, and UI-independent; the configurator renders them (T12) and
 * the compiler validates against them (T09).
 *
 * Invariants:
 * - No hidden runtime defaults: every default a runtime may apply must be
 *   declared here; [defaultsOf] is the only default-producing function.
 * - No undeclared runtime configuration: every field a runtime reads must be
 *   declared in the schema (mirrors the legacy check_node_contracts gate).
 */
@Serializable
enum class NodeSchemaLevel {
    BASIC,
    ADVANCED,
    EXPERT,
}

/**
 * Security classification of a node schema (plan §11 security class).
 * Reuses the established capability-layer vocabulary.
 */
@Serializable
enum class NodeSecurityClass {
    STANDARD,
    SENSITIVE,
    HIGH_RISK,
    DESTRUCTIVE,
}

/**
 * Which node kinds a schema describes. One schema describes one kind; the
 * registry enforces uniqueness per kind.
 */
@Serializable
enum class NodeSchemaKind {
    TRIGGER,
    ACTION,
}

/**
 * Typed field types allowed in canonical schemas. The list is the escape
 * hatch boundary: every value a schema field carries is one of these kinds,
 * never an untyped string.
 */
@Serializable
enum class NodeFieldType {
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
    COLLECTION,
    SECRET_REFERENCE,
}

/**
 * A declarable default for one field. Factory functions are the ONLY way a
 * default enters the engine, and [defaultsOf] is the only way a default
 * leaves it — so "no hidden runtime defaults" is structural.
 */
@Serializable
data class NodeFieldDefault(
    val raw: CanonicalValue,
) {
    init {
        require(raw.kind in DEFAULTABLE_KINDS) {
            "Default for kind ${raw.kind} is not declarable; use a factory function"
        }
        require(raw !is ExpressionValue) {
            "ExpressionValue is never a default"
        }
    }

    companion object {
        private val DEFAULTABLE_KINDS = setOf(
            CanonicalValueKind.BOOLEAN,
            CanonicalValueKind.INTEGER,
            CanonicalValueKind.DECIMAL,
            CanonicalValueKind.TEXT,
            CanonicalValueKind.PERCENTAGE,
            CanonicalValueKind.DURATION_MS,
            CanonicalValueKind.TIMESTAMP_MS,
            CanonicalValueKind.TIME_OF_DAY,
            CanonicalValueKind.DATE,
            CanonicalValueKind.TIMEZONE_ID,
            CanonicalValueKind.PACKAGE_ID,
            CanonicalValueKind.URI,
            CanonicalValueKind.ENUM_TOKEN,
            CanonicalValueKind.JSON,
        )

        fun ofBoolean(value: Boolean) = NodeFieldDefault(BooleanValue(value))
        fun ofInteger(value: Long) = NodeFieldDefault(IntegerValue(value))
        fun ofDecimal(value: String) = NodeFieldDefault(DecimalValue(value))
        fun ofText(value: String) = NodeFieldDefault(TextValue(value))
        fun ofPercentage(value: String) = NodeFieldDefault(PercentageValue(value))
        fun ofDuration(value: Long) = NodeFieldDefault(DurationValue(value))
        fun ofTimeOfDay(value: Int) = NodeFieldDefault(TimeOfDayValue(value))
        fun ofPackage(value: String) = NodeFieldDefault(PackageIdValue(value))
        fun ofUri(value: String) = NodeFieldDefault(UriValue(value))
        fun ofEnumToken(enumType: String, token: String) =
            NodeFieldDefault(EnumTokenValue(enumType, token))
    }
}

/** Declarative visibility/requirement predicates over sibling field values. */
@Serializable
sealed interface NodeFieldCondition {
    @Serializable
    data class Equals(val field: CanonicalFieldId, val expected: Boolean) : NodeFieldCondition

    @Serializable
    data class NotEquals(val field: CanonicalFieldId, val expected: Boolean) : NodeFieldCondition

    @Serializable
    data class Present(val field: CanonicalFieldId) : NodeFieldCondition
}

/**
 * One schema field. Typed, bounded, and declaratively conditioned.
 */
@Serializable
data class NodeSchemaField(
    val id: CanonicalFieldId,
    val type: NodeFieldType,
    val level: NodeSchemaLevel = NodeSchemaLevel.BASIC,
    /** Declared default; null means "no default" — never an implicit one. */
    val default: NodeFieldDefault? = null,
    /** When present, the field is visible only if every condition holds. */
    val visibleWhen: List<NodeFieldCondition> = emptyList(),
    /** When present, the field is required only if every condition holds. */
    val requiredWhen: List<NodeFieldCondition> = emptyList(),
    /** Unconditionally required fields set this to true. */
    val alwaysRequired: Boolean = false,
    val helpText: String? = null,
    /** Bounds for numeric-ish fields; validated against supplied values. */
    val minimum: Long? = null,
    val maximum: Long? = null,
    /** For ENUM_TOKEN fields: the enum type id tokens belong to. */
    val enumType: String? = null,
    /** For ENUM_TOKEN fields: the bounded token allowlist. */
    val allowedTokens: List<String> = emptyList(),
    /** Whether a typed expression may stand in for this field at runtime. */
    val expressionCapable: Boolean = false,
) {
    init {
        require(minimum == null || maximum == null || minimum <= maximum) {
            "minimum must not exceed maximum"
        }
        require(enumType == null || type == NodeFieldType.ENUM_TOKEN) {
            "enumType is only valid on ENUM_TOKEN fields"
        }
        require(allowedTokens.isEmpty() || type == NodeFieldType.ENUM_TOKEN) {
            "allowedTokens is only valid on ENUM_TOKEN fields"
        }
        require(!alwaysRequired || requiredWhen.isEmpty()) {
            "alwaysRequired and requiredWhen are mutually exclusive"
        }
    }
}

/** A conflict declaration: these field values cannot coexist on one node. */
@Serializable
data class NodeSchemaConflict(
    /** First side of the conflict: field id plus forbidden value. */
    val field: CanonicalFieldId,
    /** Second side of the conflict: field id plus forbidden value. */
    val againstField: CanonicalFieldId,
    val againstValue: Boolean,
    val message: String,
)

/**
 * Capability linkage for a schema. Declared, not inferred: the configurator
 * and the planner read the same declaration.
 */
@Serializable
data class NodeSchemaCapability(
    val capabilityId: String,
    val required: Boolean = true,
)

/**
 * The complete contract for one canonical node kind.
 */
@Serializable
data class NodeSchema(
    /** Stable schema id; matches the target the schema describes. */
    val schemaId: String,
    val kind: NodeSchemaKind,
    /** Stable target this schema configures, e.g. core.connectivity.wifi. */
    val target: TargetId,
    /** Stable operation (actions) or predicate (triggers) being configured. */
    val operation: OperationId? = null,
    val predicate: PredicateId? = null,
    val title: String,
    val fields: List<NodeSchemaField>,
    val conflicts: List<NodeSchemaConflict> = emptyList(),
    val capabilities: List<NodeSchemaCapability> = emptyList(),
    val securityClass: NodeSecurityClass = NodeSecurityClass.STANDARD,
    /** Whether the operation accepts multiple targets (T05 linkage). */
    val selectionMode: TargetSelectionMode = TargetSelectionMode.SINGLE,
    /** Summary template; {fieldId} placeholders resolve from node arguments. */
    val summaryTemplate: String,
) {
    init {
        require(schemaId.matches(SCHEMA_ID)) {
            "NodeSchema.schemaId must be a lowercase dotted stable id: $schemaId"
        }
        require(fields.map { it.id }.distinct().size == fields.size) {
            "NodeSchema fields must have unique ids"
        }
        require((operation == null) != (predicate == null)) {
            "NodeSchema declares exactly one of operation or predicate"
        }
        require(summaryTemplate.length <= MAX_SUMMARY_LENGTH) {
            "summaryTemplate exceeds $MAX_SUMMARY_LENGTH characters"
        }
    }

    fun field(id: CanonicalFieldId): NodeSchemaField? = fields.firstOrNull { it.id == id }

    companion object {
        private val SCHEMA_ID = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
        private const val MAX_SUMMARY_LENGTH = 512
    }
}

/** Deterministic schema violations with stable rule names. */
sealed interface NodeSchemaViolation {
    val message: String
    val rule: String
}

data class UnknownField(
    val field: CanonicalFieldId,
    override val rule: String = "unknown_field",
    override val message: String = "field ${field.value} is not declared by the schema",
) : NodeSchemaViolation

data class FieldTypeMismatch(
    val field: CanonicalFieldId,
    val expected: NodeFieldType,
    val actual: CanonicalValueKind,
    override val rule: String = "field_type_mismatch",
    override val message: String =
        "field ${field.value} expects $expected but a value of kind $actual was supplied",
) : NodeSchemaViolation

data class FieldValueOutOfBounds(
    val field: CanonicalFieldId,
    val minimum: Long?,
    val maximum: Long?,
    override val rule: String = "field_value_out_of_bounds",
    override val message: String =
        "field ${field.value} value is outside ${minimum ?: "−∞"}..${maximum ?: "∞"}",
) : NodeSchemaViolation

data class EnumTokenNotAllowed(
    val field: CanonicalFieldId,
    val token: String,
    override val rule: String = "enum_token_not_allowed",
    override val message: String = "token $token is not allowed for field ${field.value}",
) : NodeSchemaViolation


data class MissingRequiredField(
    val field: CanonicalFieldId,
    override val rule: String = "missing_required_field",
    override val message: String = "required field ${field.value} is missing",
) : NodeSchemaViolation

data class InvisibleFieldSupplied(
    val field: CanonicalFieldId,
    override val rule: String = "invisible_field_supplied",
    override val message: String =
        "field ${field.value} is not visible under the current sibling values",
) : NodeSchemaViolation

data class SchemaConflictDetected(
    override val message: String,
    override val rule: String = "schema_conflict_detected",
) : NodeSchemaViolation

data class InvalidSummaryTemplate(
    val template: String,
    override val rule: String = "invalid_summary_template",
    override val message: String = "summary template references undeclared fields: $template",
) : NodeSchemaViolation

/** The value a field carries in a configurator or a compiled node. */
@Serializable
data class NodeFieldValue(
    val field: CanonicalFieldId,
    val value: CanonicalValue,
)

/**
 * Applies the declared defaults of [schema] to [values]. This is the ONLY
 * default-producing function in the engine: a runtime that applies defaults
 * through anything else violates the no-hidden-defaults invariant.
 */
fun defaultsOf(schema: NodeSchema): List<NodeFieldValue> = schema.fields
    .filter { it.default != null }
    .map { NodeFieldValue(it.id, it.default!!.raw) }

/**
 * Validates [values] against [schema]. Fail-closed and deterministic:
 * violations come back in schema-declaration order per rule, grouped in the
 * fixed rule order of [NodeSchemaViolation] subtypes. Rules evaluated:
 * unknown fields, visibility, type match, bounds, enum allowlist, required
 * fields (unconditional and conditional), and declared conflicts.
 */
fun validateNodeValues(
    schema: NodeSchema,
    values: List<NodeFieldValue>,
): List<NodeSchemaViolation> {
    val violations = mutableListOf<NodeSchemaViolation>()
    val byId = values.associateBy { it.field }
    val supplied = byId.keys

    // Rule 1 — unknown fields (fail closed: no undeclared configuration).
    for (value in values) {
        if (schema.field(value.field) == null) {
            violations += UnknownField(value.field)
        }
    }

    // Rule 2 — visibility: a supplied invisible field is a violation.
    for (field in schema.fields) {
        if (field.id !in supplied) continue
        if (field.visibleWhen.isNotEmpty() && !conditionsHold(field.visibleWhen, byId)) {
            violations += InvisibleFieldSupplied(field.id)
        }
    }

    // Rule 3 — type match, bounds, enum allowlist (schema-declaration order).
    for (field in schema.fields) {
        val value = byId[field.id] ?: continue
        val expectedKind = expectedValueKind(field.type)
        val expression = value.value as? ExpressionValue
        if (expression != null) {
            if (!field.expressionCapable || expression.resultKind != expectedKind) {
                violations += FieldTypeMismatch(field.id, field.type, value.value.kind)
            }
            // Dynamic expressions are type-checked here; bounds/enum values are
            // validated after resolution by the execution provider.
            continue
        }
        if (value.value.kind != expectedKind) {
            violations += FieldTypeMismatch(field.id, field.type, value.value.kind)
            continue
        }
        // Bounds (numeric payloads; compared as exact decimals).
        if (field.minimum != null || field.maximum != null) {
            val numeric = when (value.value) {
                is IntegerValue -> java.math.BigDecimal.valueOf(value.value.value)
                is PercentageValue -> value.value.value.toBigDecimalOrNull()
                is DecimalValue -> value.value.value.toBigDecimalOrNull()
                is DurationValue -> java.math.BigDecimal.valueOf(value.value.milliseconds)
                else -> null
            }
            if (numeric != null) {
                val below = field.minimum != null &&
                    numeric < java.math.BigDecimal.valueOf(field.minimum)
                val above = field.maximum != null &&
                    numeric > java.math.BigDecimal.valueOf(field.maximum)
                if (below || above) {
                    violations += FieldValueOutOfBounds(field.id, field.minimum, field.maximum)
                }
            }
        }
        // Enum allowlist.
        if (field.type == NodeFieldType.ENUM_TOKEN && value.value is EnumTokenValue) {
            val token = value.value
            val wrongType = field.enumType != null && token.enumType != field.enumType
            val notAllowed = field.allowedTokens.isNotEmpty() && token.token !in field.allowedTokens
            if (wrongType || notAllowed) {
                violations += EnumTokenNotAllowed(field.id, token.token)
            }
        }
    }

    // Rule 4 — required fields: unconditional, then conditional.
    for (field in schema.fields) {
        val required = field.alwaysRequired ||
            (field.requiredWhen.isNotEmpty() && conditionsHold(field.requiredWhen, byId))
        if (required && field.id !in supplied) {
            violations += MissingRequiredField(field.id)
        }
    }

    // Rule 5 — declared conflicts.
    for (conflict in schema.conflicts) {
        val lhs = byId[conflict.field]
        val rhsBoolean = (byId[conflict.againstField]?.value as? BooleanValue)?.value
        if (lhs != null && rhsBoolean == conflict.againstValue) {
            violations += SchemaConflictDetected(conflict.message)
        }
    }

    return violations
}

/** Internal: shared by the schema validator and the T12 configurator state. */
internal fun conditionsHold(
    conditions: List<NodeFieldCondition>,
    byId: Map<CanonicalFieldId, NodeFieldValue>,
): Boolean = conditions.all { condition ->
    when (condition) {
        is NodeFieldCondition.Equals ->
            (byId[condition.field]?.value as? BooleanValue)?.value == condition.expected
        is NodeFieldCondition.NotEquals -> {
            val suppliedValue = byId[condition.field]?.value as? BooleanValue
            suppliedValue == null || suppliedValue.value != condition.expected
        }
        is NodeFieldCondition.Present -> condition.field in byId
    }
}

internal fun expectedValueKind(type: NodeFieldType): CanonicalValueKind = when (type) {
    NodeFieldType.BOOLEAN -> CanonicalValueKind.BOOLEAN
    NodeFieldType.INTEGER -> CanonicalValueKind.INTEGER
    NodeFieldType.DECIMAL -> CanonicalValueKind.DECIMAL
    NodeFieldType.TEXT -> CanonicalValueKind.TEXT
    NodeFieldType.PERCENTAGE -> CanonicalValueKind.PERCENTAGE
    NodeFieldType.DURATION_MS -> CanonicalValueKind.DURATION_MS
    NodeFieldType.TIMESTAMP_MS -> CanonicalValueKind.TIMESTAMP_MS
    NodeFieldType.TIME_OF_DAY -> CanonicalValueKind.TIME_OF_DAY
    NodeFieldType.DATE -> CanonicalValueKind.DATE
    NodeFieldType.TIMEZONE_ID -> CanonicalValueKind.TIMEZONE_ID
    NodeFieldType.PACKAGE_ID -> CanonicalValueKind.PACKAGE_ID
    NodeFieldType.URI -> CanonicalValueKind.URI
    NodeFieldType.COORDINATE -> CanonicalValueKind.COORDINATE
    NodeFieldType.ENUM_TOKEN -> CanonicalValueKind.ENUM_TOKEN
    NodeFieldType.JSON -> CanonicalValueKind.JSON
    NodeFieldType.COLLECTION -> CanonicalValueKind.COLLECTION
    NodeFieldType.SECRET_REFERENCE -> CanonicalValueKind.SECRET_REFERENCE
}

/**
 * T08 summary formatting (plan §13 groundwork): resolves `{field}` placeholders
 * in [NodeSchema.summaryTemplate] from [values]. Declared defaults are NOT
 * auto-applied here; callers pass the exact values to render. Deterministic
 * and UI-independent so Builder, history, import preview and diagnostics all
 * render one identical summary (the full [NodeSummaryFormatter] UI wiring is
 * T13).
 */
object NodeSummaryFormatter {
    fun summarize(
        schema: NodeSchema,
        values: List<NodeFieldValue>,
    ): String {
        val byId = values.associateBy { it.field }
        var missingTemplateField: String? = null

        fun renderField(id: String): String? {
            val field = schema.field(CanonicalFieldId(id))
            if (field == null) {
                if (missingTemplateField == null) missingTemplateField = id
                return null
            }
            val value = byId[CanonicalFieldId(id)]?.value ?: return null
            return renderValue(value)
        }

        // 1) Optional groups: "{{ prefix {field} suffix }}" render only when
        //    the field carries a value, dropping the whole segment otherwise.
        var rendered = OPTIONAL_GROUP.replace(schema.summaryTemplate) { match ->
            val value = renderField(match.groupValues[2])
            if (value == null) "" else match.groupValues[1] + value + match.groupValues[3]
        }
        // 2) Plain placeholders: "{field}" renders the value or nothing.
        rendered = TEMPLATE_FIELD.replace(rendered) { match ->
            renderField(match.groupValues[1]) ?: ""
        }

        require(missingTemplateField == null) {
            "summary template references undeclared field: $missingTemplateField"
        }
        // Collapse blank gaps left by absent optional fields, then trim.
        return rendered.replace(EXTRA_SPACES, " ").trim()
    }

    private fun renderValue(value: CanonicalValue): String = when (value) {
        is SecretReferenceValue -> "••••"
        is EnumTokenValue -> value.token
        is CollectionValue -> value.values.joinToString(", ") { renderScalar(it) }
        else -> renderScalar(value)
    }

    private fun renderScalar(value: CanonicalValue): String = when (value) {
        is BooleanValue -> if (value.value) "On" else "Off"
        is PercentageValue -> "${value.value}%"
        is DurationValue -> "${value.milliseconds}ms"
        is IntegerValue -> value.value.toString()
        is DecimalValue -> value.value
        is TextValue -> value.value
        is PackageIdValue -> value.packageName
        else -> value.toString()
    }

    private val OPTIONAL_GROUP = Regex("\\{\\{([^{}]*)\\{([a-zA-Z0-9_]+)\\}([^{}]*)\\}\\}")
    private val TEMPLATE_FIELD = Regex("\\{([a-zA-Z0-9_]+)\\}")
    private val EXTRA_SPACES = Regex("\\s{2,}")
}

/**
 * Registry of schemas keyed by target+operation/predicate. Uniqueness is
 * enforced at construction so a duplicate registration fails immediately.
 */
class NodeSchemaRegistry(schemas: List<NodeSchema>) {
    private val byKey: Map<Pair<TargetId, String>, NodeSchema>

    init {
        val keys = schemas.map { keyOf(it) }
        require(keys.distinct().size == keys.size) { "Duplicate NodeSchema registration" }
        byKey = schemas.associateBy { keyOf(it) }
    }

    fun schemaFor(
        target: TargetId,
        operation: OperationId? = null,
        predicate: PredicateId? = null,
    ): NodeSchema? {
        require((operation == null) != (predicate == null)) {
            "schemaFor requires exactly one of operation or predicate"
        }
        val suffix = operation?.value ?: predicate!!.value
        return byKey[Pair(target, suffix)]
    }

    fun all(): List<NodeSchema> = byKey.values.sortedBy { it.schemaId }

    private fun keyOf(schema: NodeSchema): Pair<TargetId, String> = Pair(
        schema.target,
        schema.operation?.value ?: schema.predicate!!.value,
    )
}
