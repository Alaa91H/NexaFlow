package com.nexaflow.domain.canonical

import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigField
import com.nexaflow.domain.catalog.NodeConfigValueType
import kotlinx.serialization.json.Json

/**
 * Shared T26/T27 compatibility contract normalizer.
 *
 * Both execution and persistence use this exact function, so a legacy config
 * key can never acquire one type/default in the runtime and another in V3.
 * The catalog remains the compatibility source for legacy keys while family
 * adapters contribute canonical derived arguments.
 */
data class LegacyCatalogCanonicalContract(
    val schema: NodeSchema,
    val values: List<NodeFieldValue>,
    val containsLegacySecretMaterial: Boolean,
)

object LegacyCatalogCanonicalContractNormalizer {

    fun normalize(
        definition: AutomationNodeDefinition,
        node: CanonicalNode,
        config: List<LegacyConfigEntry>,
        kind: NodeSchemaKind,
    ): LegacyCatalogCanonicalContract {
        val configByKey = config.associateBy { it.key }
        val catalogFields = definition.configuration.fields.map { field ->
            NodeSchemaField(
                id = CanonicalFieldId(field.key),
                type = canonicalFieldType(field.valueType),
                default = field.defaultValue?.let { raw ->
                    canonicalValue(
                        field = field,
                        legacyType = definition.legacyTypeName,
                        raw = raw,
                        allowExpression = false,
                    )
                        ?.takeUnless { it is SecretReferenceValue }
                        ?.let(::NodeFieldDefault)
                },
                alwaysRequired = field.required,
                minimum = field.minValue?.takeIf(::isWholeLong)?.toLong(),
                maximum = field.maxValue?.takeIf(::isWholeLong)?.toLong(),
                enumType = if (field.valueType == NodeConfigValueType.ENUM) {
                    enumTypeId(definition.legacyTypeName, field.key)
                } else {
                    null
                },
                allowedTokens = if (field.valueType == NodeConfigValueType.ENUM) {
                    field.allowedValues.map { it.uppercase() }
                } else {
                    emptyList()
                },
                expressionCapable = field.expressionCapable,
            )
        }

        val catalogValues = definition.configuration.fields.mapNotNull { field ->
            val raw = configByKey[field.key]?.rawValue
                ?: field.defaultValue
                ?: return@mapNotNull null
            canonicalValue(
                field = field,
                legacyType = definition.legacyTypeName,
                raw = raw,
                allowExpression = field.expressionCapable,
            )?.let { NodeFieldValue(CanonicalFieldId(field.key), it) }
        }

        val derivedValues = nodeArguments(node).entries.map {
            NodeFieldValue(it.id, it.value)
        }
        val catalogIds = catalogFields.mapTo(linkedSetOf()) { it.id }
        val derivedFields = derivedValues
            .filter { it.field !in catalogIds }
            .map(::inferredField)

        val fields = catalogFields + derivedFields
        val values = (derivedValues + catalogValues)
            .associateBy { it.field }
            .values
            .toList()

        val schema = NodeSchema(
            schemaId = "compat." + kind.name.lowercase() + "." +
                definition.legacyTypeName.lowercase(),
            kind = kind,
            target = targetOf(node),
            operation = if (kind == NodeSchemaKind.ACTION) operationOf(node) else null,
            predicate = if (kind == NodeSchemaKind.TRIGGER) predicateOf(node) else null,
            title = definition.legacyTypeName,
            fields = fields,
            securityClass = if (fields.any { it.type == NodeFieldType.SECRET_REFERENCE }) {
                NodeSecurityClass.SENSITIVE
            } else {
                NodeSecurityClass.STANDARD
            },
            selectionMode = TargetSelectionMode.SINGLE,
            summaryTemplate = definition.legacyTypeName,
        )

        val sensitiveKeys = definition.configuration.fields
            .filter { it.sensitive || it.valueType == NodeConfigValueType.SECRET }
            .mapTo(linkedSetOf()) { it.key }

        return LegacyCatalogCanonicalContract(
            schema = schema,
            values = values,
            containsLegacySecretMaterial =
                config.any { it.key in sensitiveKeys && it.rawValue.isNotEmpty() },
        )
    }

    fun legacyConfigEntries(config: Map<String, String>): List<LegacyConfigEntry> =
        config.entries
            .sortedBy { it.key }
            .map { LegacyConfigEntry(it.key, it.value) }

    fun sanitizedPreservedConfig(
        definition: AutomationNodeDefinition,
        preserved: List<LegacyConfigEntry>,
    ): List<LegacyConfigEntry> {
        val sensitiveKeys = definition.configuration.fields
            .filter { it.sensitive || it.valueType == NodeConfigValueType.SECRET }
            .mapTo(linkedSetOf()) { it.key }
        return preserved.filterNot { it.key in sensitiveKeys }
    }

    private fun canonicalValue(
        field: NodeConfigField,
        legacyType: String,
        raw: String,
        allowExpression: Boolean,
    ): CanonicalValue? {
        if (allowExpression && EXPRESSION_MARKER.containsMatchIn(raw)) {
            return ExpressionValue(raw, canonicalValueKind(field.valueType))
        }
        if (raw.isBlank() && field.valueType in NON_BLANK_TYPES) return null

        return when (field.valueType) {
            NodeConfigValueType.STRING -> TextValue(raw)
            NodeConfigValueType.INTEGER -> IntegerValue(raw.toLong())
            NodeConfigValueType.DECIMAL,
            NodeConfigValueType.COORDINATE -> DecimalValue(raw)
            NodeConfigValueType.BOOLEAN -> BooleanValue(raw.toBooleanStrict())
            NodeConfigValueType.ENUM -> EnumTokenValue(
                enumTypeId(legacyType, field.key),
                raw.trim().uppercase(),
            )
            NodeConfigValueType.TIME -> {
                val parts = raw.split(":")
                require(parts.size == 2) { "time must use HH:mm" }
                TimeOfDayValue(parts[0].toInt() * 60 + parts[1].toInt())
            }
            NodeConfigValueType.DATE -> DateValue(raw)
            NodeConfigValueType.DURATION_SECONDS -> DurationValue(
                Math.multiplyExact(raw.toLong(), 1000L),
            )
            NodeConfigValueType.PACKAGE -> PackageIdValue(raw)
            NodeConfigValueType.URL -> UriValue(raw)
            NodeConfigValueType.SECRET -> SecretReferenceValue(
                secretReferenceId(legacyType, field.key),
            )
            NodeConfigValueType.JSON -> JsonValue(Json.parseToJsonElement(raw))
        }
    }

    private fun canonicalFieldType(type: NodeConfigValueType): NodeFieldType = when (type) {
        NodeConfigValueType.STRING -> NodeFieldType.TEXT
        NodeConfigValueType.INTEGER -> NodeFieldType.INTEGER
        NodeConfigValueType.DECIMAL,
        NodeConfigValueType.COORDINATE -> NodeFieldType.DECIMAL
        NodeConfigValueType.BOOLEAN -> NodeFieldType.BOOLEAN
        NodeConfigValueType.ENUM -> NodeFieldType.ENUM_TOKEN
        NodeConfigValueType.TIME -> NodeFieldType.TIME_OF_DAY
        NodeConfigValueType.DATE -> NodeFieldType.DATE
        NodeConfigValueType.DURATION_SECONDS -> NodeFieldType.DURATION_MS
        NodeConfigValueType.PACKAGE -> NodeFieldType.PACKAGE_ID
        NodeConfigValueType.URL -> NodeFieldType.URI
        NodeConfigValueType.SECRET -> NodeFieldType.SECRET_REFERENCE
        NodeConfigValueType.JSON -> NodeFieldType.JSON
    }

    private fun canonicalValueKind(type: NodeConfigValueType): CanonicalValueKind = when (type) {
        NodeConfigValueType.STRING -> CanonicalValueKind.TEXT
        NodeConfigValueType.INTEGER -> CanonicalValueKind.INTEGER
        NodeConfigValueType.DECIMAL,
        NodeConfigValueType.COORDINATE -> CanonicalValueKind.DECIMAL
        NodeConfigValueType.BOOLEAN -> CanonicalValueKind.BOOLEAN
        NodeConfigValueType.ENUM -> CanonicalValueKind.ENUM_TOKEN
        NodeConfigValueType.TIME -> CanonicalValueKind.TIME_OF_DAY
        NodeConfigValueType.DATE -> CanonicalValueKind.DATE
        NodeConfigValueType.DURATION_SECONDS -> CanonicalValueKind.DURATION_MS
        NodeConfigValueType.PACKAGE -> CanonicalValueKind.PACKAGE_ID
        NodeConfigValueType.URL -> CanonicalValueKind.URI
        NodeConfigValueType.SECRET -> CanonicalValueKind.SECRET_REFERENCE
        NodeConfigValueType.JSON -> CanonicalValueKind.JSON
    }

    private fun nodeArguments(node: CanonicalNode): CanonicalArguments = when (node) {
        is CanonicalActionNode -> node.arguments
        is ObserveNode -> node.arguments
        else -> CanonicalArguments.EMPTY
    }

    private fun inferredField(value: NodeFieldValue): NodeSchemaField {
        val raw = value.value
        val type = when (raw.kind) {
            CanonicalValueKind.BOOLEAN -> NodeFieldType.BOOLEAN
            CanonicalValueKind.INTEGER -> NodeFieldType.INTEGER
            CanonicalValueKind.DECIMAL -> NodeFieldType.DECIMAL
            CanonicalValueKind.TEXT -> NodeFieldType.TEXT
            CanonicalValueKind.PERCENTAGE -> NodeFieldType.PERCENTAGE
            CanonicalValueKind.DURATION_MS -> NodeFieldType.DURATION_MS
            CanonicalValueKind.TIMESTAMP_MS -> NodeFieldType.TIMESTAMP_MS
            CanonicalValueKind.TIME_OF_DAY -> NodeFieldType.TIME_OF_DAY
            CanonicalValueKind.DATE -> NodeFieldType.DATE
            CanonicalValueKind.TIMEZONE_ID -> NodeFieldType.TIMEZONE_ID
            CanonicalValueKind.PACKAGE_ID -> NodeFieldType.PACKAGE_ID
            CanonicalValueKind.URI -> NodeFieldType.URI
            CanonicalValueKind.COORDINATE -> NodeFieldType.COORDINATE
            CanonicalValueKind.ENUM_TOKEN -> NodeFieldType.ENUM_TOKEN
            CanonicalValueKind.JSON -> NodeFieldType.JSON
            CanonicalValueKind.SECRET_REFERENCE -> NodeFieldType.SECRET_REFERENCE
            CanonicalValueKind.COLLECTION -> NodeFieldType.COLLECTION
            CanonicalValueKind.EXPRESSION -> {
                val expression = raw as ExpressionValue
                nodeFieldTypeForKind(expression.resultKind)
            }
        }
        val enum = raw as? EnumTokenValue
        return NodeSchemaField(
            id = value.field,
            type = type,
            alwaysRequired = true,
            enumType = enum?.enumType,
            allowedTokens = enum?.let { listOf(it.token) }.orEmpty(),
            collectionElementKind = (raw as? CollectionValue)?.elementKind,
            expressionCapable = raw is ExpressionValue,
        )
    }

    private fun nodeFieldTypeForKind(kind: CanonicalValueKind): NodeFieldType = when (kind) {
        CanonicalValueKind.BOOLEAN -> NodeFieldType.BOOLEAN
        CanonicalValueKind.INTEGER -> NodeFieldType.INTEGER
        CanonicalValueKind.DECIMAL -> NodeFieldType.DECIMAL
        CanonicalValueKind.TEXT -> NodeFieldType.TEXT
        CanonicalValueKind.PERCENTAGE -> NodeFieldType.PERCENTAGE
        CanonicalValueKind.DURATION_MS -> NodeFieldType.DURATION_MS
        CanonicalValueKind.TIMESTAMP_MS -> NodeFieldType.TIMESTAMP_MS
        CanonicalValueKind.TIME_OF_DAY -> NodeFieldType.TIME_OF_DAY
        CanonicalValueKind.DATE -> NodeFieldType.DATE
        CanonicalValueKind.TIMEZONE_ID -> NodeFieldType.TIMEZONE_ID
        CanonicalValueKind.PACKAGE_ID -> NodeFieldType.PACKAGE_ID
        CanonicalValueKind.URI -> NodeFieldType.URI
        CanonicalValueKind.COORDINATE -> NodeFieldType.COORDINATE
        CanonicalValueKind.ENUM_TOKEN -> NodeFieldType.ENUM_TOKEN
        CanonicalValueKind.JSON -> NodeFieldType.JSON
        CanonicalValueKind.SECRET_REFERENCE -> NodeFieldType.SECRET_REFERENCE
        CanonicalValueKind.COLLECTION -> NodeFieldType.COLLECTION
        CanonicalValueKind.EXPRESSION ->
            throw IllegalArgumentException("nested expression result kind is invalid")
    }

    private fun targetOf(node: CanonicalNode): TargetId = when (node) {
        is CanonicalActionNode -> node.target
        is ObserveNode -> node.target
        else -> throw IllegalArgumentException(
            "legacy node ${node.primitive} has no direct target contract",
        )
    }

    private fun operationOf(node: CanonicalNode): OperationId = when (node) {
        is SetStateNode -> OperationId("core.operation.set_state")
        is SetValueNode -> OperationId("core.operation.set_value")
        is InvokeNode -> node.operation
        is OpenNode -> node.operation
        is SendNode -> node.operation
        is TransformNode -> node.operation
        is InputNode -> node.operation
        is RestoreNode -> OperationId("core.operation.set_state")
        else -> throw IllegalArgumentException(
            "legacy action ${node.primitive} has no operation contract",
        )
    }

    private fun predicateOf(node: CanonicalNode): PredicateId = when (node) {
        is ObserveNode -> node.predicate
        else -> throw IllegalArgumentException(
            "legacy trigger ${node.primitive} has no predicate contract",
        )
    }

    private fun enumTypeId(legacyType: String, key: String): String =
        "compat." + legacyType.lowercase() + "." +
            key.lowercase().replace(NON_ID_CHARS, "_")

    private fun secretReferenceId(legacyType: String, key: String): String =
        ("legacy." + legacyType.lowercase() + "." +
            key.lowercase().replace(NON_ID_CHARS, "_")).take(128)

    private fun isWholeLong(value: Double): Boolean =
        value.isFinite() && value == value.toLong().toDouble()

    private val EXPRESSION_MARKER = Regex("%(?:CTX\\.|[A-Za-z_])")
    private val NON_ID_CHARS = Regex("[^a-z0-9_]")
    private val NON_BLANK_TYPES = setOf(
        NodeConfigValueType.INTEGER,
        NodeConfigValueType.DECIMAL,
        NodeConfigValueType.BOOLEAN,
        NodeConfigValueType.ENUM,
        NodeConfigValueType.TIME,
        NodeConfigValueType.DATE,
        NodeConfigValueType.DURATION_SECONDS,
        NodeConfigValueType.PACKAGE,
        NodeConfigValueType.URL,
        NodeConfigValueType.SECRET,
        NodeConfigValueType.JSON,
        NodeConfigValueType.COORDINATE,
    )
}
