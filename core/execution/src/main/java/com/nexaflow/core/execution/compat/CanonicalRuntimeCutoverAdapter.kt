package com.nexaflow.core.execution.compat

import com.nexaflow.domain.canonical.BooleanValue
import com.nexaflow.domain.canonical.CanonicalActionNode
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalNode
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.CanonicalRuntimePipeline
import com.nexaflow.domain.canonical.CanonicalValue
import com.nexaflow.domain.canonical.CanonicalValueKind
import com.nexaflow.domain.canonical.DateValue
import com.nexaflow.domain.canonical.DecimalValue
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.ExecutionMode
import com.nexaflow.domain.canonical.ExpressionValue
import com.nexaflow.domain.canonical.FailurePolicy
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.JsonValue
import com.nexaflow.domain.canonical.LegacyAdapterOutcome
import com.nexaflow.domain.canonical.LegacyConfigEntry
import com.nexaflow.domain.canonical.LegacyNodeInput
import com.nexaflow.domain.canonical.LegacyNodeKind
import com.nexaflow.domain.canonical.NodeFieldDefault
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeFieldValue
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.NodeSecurityClass
import com.nexaflow.domain.canonical.NodeSelectionSemantics
import com.nexaflow.domain.canonical.ObserveNode
import com.nexaflow.domain.canonical.OperationId
import com.nexaflow.domain.canonical.PackageIdValue
import com.nexaflow.domain.canonical.PredicateId
import com.nexaflow.domain.canonical.SecretReferenceValue
import com.nexaflow.domain.canonical.TargetId
import com.nexaflow.domain.canonical.TargetSelectionMode
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.canonical.TimeOfDayValue
import com.nexaflow.domain.canonical.UriValue
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigField
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Trigger
import kotlinx.serialization.json.Json

/**
 * T26 production compatibility boundary.
 *
 * Legacy enums and raw string maps stop here. Catalog contracts materialize
 * typed canonical values (including declared defaults), then execution goes
 * through CanonicalRuntimePipeline.planLegacy:
 *
 * legacy -> adapter -> canonical AST -> validation -> planner -> AtomicCommand.
 */
class CanonicalRuntimeCutoverAdapter(
    private val pipeline: CanonicalRuntimePipeline = CanonicalRuntimePipeline(),
) {
    data class PreparedAction(
        val node: CanonicalNode,
        val command: com.nexaflow.domain.canonical.AtomicCommand,
        val preservedConfig: List<LegacyConfigEntry>,
    )

    data class PreparedTrigger(
        val node: CanonicalNode,
        val preservedConfig: List<LegacyConfigEntry>,
    )

    fun prepareAction(
        action: Action,
        runId: String,
        instanceId: String,
    ): PreparedAction {
        val definition = AutomationNodeCatalog.definitionFor(action.type)
        val rawConfig = action.config.toEntries()
        val preview = pipeline.canonicalize(
            LegacyNodeInput(action.type.name, LegacyNodeKind.ACTION, rawConfig),
        )
        val canonicalized = preview as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical adapter rejected ${action.type.name}: " +
                    (preview as LegacyAdapterOutcome.Rejected).reason,
            )
        val contract = contractFor(
            definition = definition,
            node = canonicalized.node,
            config = action.config,
            kind = NodeSchemaKind.ACTION,
        )
        val planned = pipeline.planLegacy(
            runId = runId,
            legacyType = action.type.name,
            kind = LegacyNodeKind.ACTION,
            schema = contract.schema,
            config = rawConfig,
            semantics = SINGLE_NODE_SEMANTICS,
            capabilityRequirement = CommandRequirementCatalog.requirementFor(action.type),
            failurePolicy = FailurePolicy.FAIL_FAST,
            validatedValues = contract.values,
        )
        val commands = planned.plan.allCommands
        require(commands.size == 1) {
            "legacy action ${action.type.name} must plan to exactly one atomic command"
        }
        return PreparedAction(
            node = planned.node.withRuntimeId(CanonicalNodeId(instanceId)),
            command = commands.single().copy(commandId = instanceId),
            preservedConfig = planned.preservedConfig,
        )
    }

    fun prepareTrigger(
        trigger: Trigger,
        runId: String,
        instanceId: String,
    ): PreparedTrigger {
        val definition = AutomationNodeCatalog.definitionFor(trigger.type)
        val rawConfig = trigger.config.toEntries()
        val preview = pipeline.canonicalize(
            LegacyNodeInput(trigger.type.name, LegacyNodeKind.TRIGGER, rawConfig),
        )
        val canonicalized = preview as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical adapter rejected ${trigger.type.name}: " +
                    (preview as LegacyAdapterOutcome.Rejected).reason,
            )
        val contract = contractFor(
            definition = definition,
            node = canonicalized.node,
            config = trigger.config,
            kind = NodeSchemaKind.TRIGGER,
        )
        val planned = pipeline.planLegacy(
            runId = runId,
            legacyType = trigger.type.name,
            kind = LegacyNodeKind.TRIGGER,
            schema = contract.schema,
            config = rawConfig,
            semantics = SINGLE_NODE_SEMANTICS,
            capabilityRequirement = CommandRequirementCatalog.requirementFor(trigger.type),
            failurePolicy = FailurePolicy.FAIL_FAST,
            validatedValues = contract.values,
        )
        return PreparedTrigger(
            node = planned.node.withRuntimeId(CanonicalNodeId(instanceId)),
            preservedConfig = planned.preservedConfig,
        )
    }

    private data class Contract(
        val schema: NodeSchema,
        val values: List<NodeFieldValue>,
    )

    private fun contractFor(
        definition: AutomationNodeDefinition,
        node: CanonicalNode,
        config: Map<String, String>,
        kind: NodeSchemaKind,
    ): Contract {
        val fields = definition.configuration.fields.map { field ->
            NodeSchemaField(
                id = CanonicalFieldId(field.key),
                type = canonicalFieldType(field.valueType),
                default = field.defaultValue?.let { raw ->
                    canonicalValue(field, definition.legacyTypeName, raw, allowExpression = false)
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

        val values = definition.configuration.fields.mapNotNull { field ->
            val raw = config[field.key] ?: field.defaultValue ?: return@mapNotNull null
            canonicalValue(
                field = field,
                legacyType = definition.legacyTypeName,
                raw = raw,
                allowExpression = field.expressionCapable,
            )?.let { NodeFieldValue(CanonicalFieldId(field.key), it) }
        }

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
        return Contract(schema, values)
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

    private fun targetOf(node: CanonicalNode): TargetId = when (node) {
        is CanonicalActionNode -> node.target
        is ObserveNode -> node.target
        else -> throw IllegalArgumentException(
            "legacy node ${node.primitive} has no direct target contract",
        )
    }

    private fun operationOf(node: CanonicalNode): OperationId = when (node) {
        is com.nexaflow.domain.canonical.SetStateNode -> OperationId("core.operation.set_state")
        is com.nexaflow.domain.canonical.SetValueNode -> OperationId("core.operation.set_value")
        is com.nexaflow.domain.canonical.InvokeNode -> node.operation
        is com.nexaflow.domain.canonical.OpenNode -> node.operation
        is com.nexaflow.domain.canonical.SendNode -> node.operation
        is com.nexaflow.domain.canonical.TransformNode -> node.operation
        is com.nexaflow.domain.canonical.InputNode -> node.operation
        is com.nexaflow.domain.canonical.RestoreNode -> OperationId("core.operation.set_state")
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

    private companion object {
        val SINGLE_NODE_SEMANTICS = NodeSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.SINGLE,
            executionMode = ExecutionMode.SINGLE,
        )
        val EXPRESSION_MARKER = Regex("%(?:CTX\\.|[A-Za-z_])")
        val NON_ID_CHARS = Regex("[^a-z0-9_]")
        val NON_BLANK_TYPES = setOf(
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
}

private fun Map<String, String>.toEntries(): List<LegacyConfigEntry> =
    entries.sortedBy { it.key }.map { LegacyConfigEntry(it.key, it.value) }

private fun CanonicalNode.withRuntimeId(id: CanonicalNodeId): CanonicalNode = when (this) {
    is ObserveNode -> copy(id = id)
    is com.nexaflow.domain.canonical.CompareNode -> copy(id = id)
    is com.nexaflow.domain.canonical.SetStateNode -> copy(id = id)
    is com.nexaflow.domain.canonical.SetValueNode -> copy(id = id)
    is com.nexaflow.domain.canonical.InvokeNode -> copy(id = id)
    is com.nexaflow.domain.canonical.OpenNode -> copy(id = id)
    is com.nexaflow.domain.canonical.SendNode -> copy(id = id)
    is com.nexaflow.domain.canonical.TransformNode -> copy(id = id)
    is com.nexaflow.domain.canonical.InputNode -> copy(id = id)
    is com.nexaflow.domain.canonical.WaitNode -> copy(id = id)
    is com.nexaflow.domain.canonical.RestoreNode -> copy(id = id)
    is com.nexaflow.domain.canonical.SequenceNode -> copy(id = id)
    is com.nexaflow.domain.canonical.BranchNode -> copy(id = id)
    is com.nexaflow.domain.canonical.ObservedConditionNode ->
        copy(id = id, observation = observation.copy(id = id))
    is com.nexaflow.domain.canonical.ComparisonConditionNode ->
        copy(id = id, comparison = comparison.copy(id = id))
}
