package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persisted canonical payload written beside the historical Room columns.
 *
 * The canonical nodes are the validated executable intent. sourceType and
 * preservedConfig are compatibility/audit metadata only. Raw sensitive fields
 * are never copied into this JSON. Nodes that still depend on a legacy secret
 * store are explicitly marked so V3_ONLY retirement cannot be declared early.
 */
@Serializable
data class CanonicalWorkflowDocumentV3(
    val schemaVersion: Int = SCHEMA_VERSION,
    val workflowId: String,
    val conditionLogic: ConditionLogic,
    val triggers: List<CanonicalPersistedNodeV3>,
    val actions: List<CanonicalPersistedNodeV3>,
    val exitActions: List<CanonicalPersistedNodeV3>,
    val requiresLegacyFallback: Boolean = false,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported canonical workflow schemaVersion=$schemaVersion"
        }
        require(workflowId.isNotBlank()) { "workflowId must not be blank" }
        require(
            requiresLegacyFallback ==
                (triggers + actions + exitActions).any { it.legacyFallbackRequired },
        ) {
            "requiresLegacyFallback must match node-level fallback requirements"
        }
    }

    companion object {
        const val SCHEMA_VERSION = 3
    }
}

@Serializable
data class CanonicalPersistedNodeV3(
    val sourceType: String,
    val node: CanonicalNode,
    val preservedConfig: List<CanonicalLegacyEntryV3> = emptyList(),
    val endBehavior: CanonicalEndBehaviorV3? = null,
    val legacyFallbackRequired: Boolean = false,
)

@Serializable
data class CanonicalLegacyEntryV3(
    val key: String,
    val rawValue: String,
)

@Serializable
data class CanonicalEndBehaviorV3(
    val mode: String,
    val config: List<CanonicalLegacyEntryV3>,
)

@Serializable
enum class CanonicalV3WriteState {
    V3_READY,
    V3_WITH_LEGACY_FALLBACK,
    LEGACY_ONLY_DEGRADED,
}

data class CanonicalV3WriteResult(
    val state: CanonicalV3WriteState,
    val payload: String?,
    /** Stable non-sensitive code only; never exception text or user config. */
    val errorCode: String? = null,
) {
    init {
        require((state == CanonicalV3WriteState.LEGACY_ONLY_DEGRADED) == (payload == null)) {
            "degraded V3 writes must have no payload; successful writes must have one"
        }
        require((state == CanonicalV3WriteState.LEGACY_ONLY_DEGRADED) == (errorCode != null)) {
            "only degraded V3 writes carry an error code"
        }
    }
}

/**
 * T27 production codec.
 *
 * Runtime and persistence share LegacyCatalogCanonicalContractNormalizer and
 * CanonicalRuntimePipeline.planLegacy. V3 therefore serializes the same typed,
 * validated node that execution admits rather than an adapter-only skeleton.
 */
object CanonicalWorkflowV3Codec {

    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        classDiscriminator = "canonicalType"
    }

    private val pipeline: CanonicalRuntimePipeline by lazy {
        CanonicalRuntimePipeline()
    }

    private val singleNodeSemantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    fun encode(automation: Automation): String =
        json.encodeToString(documentFor(automation))

    fun prepareWrite(automation: Automation): CanonicalV3WriteResult =
        try {
            val document = documentFor(automation)
            CanonicalV3WriteResult(
                state = if (document.requiresLegacyFallback) {
                    CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK
                } else {
                    CanonicalV3WriteState.V3_READY
                },
                payload = json.encodeToString(document),
            )
        } catch (_: IllegalArgumentException) {
            CanonicalV3WriteResult(
                state = CanonicalV3WriteState.LEGACY_ONLY_DEGRADED,
                payload = null,
                errorCode = "CANONICAL_VALIDATION_REJECTED",
            )
        } catch (_: Exception) {
            CanonicalV3WriteResult(
                state = CanonicalV3WriteState.LEGACY_ONLY_DEGRADED,
                payload = null,
                errorCode = "CANONICAL_PREPARATION_FAILED",
            )
        }

    @Deprecated("Use prepareWrite so degradation is explicit and observable")
    fun encodeOrNull(automation: Automation): String? =
        prepareWrite(automation).payload

    fun decode(payload: String): CanonicalWorkflowDocumentV3 =
        json.decodeFromString(CanonicalWorkflowDocumentV3.serializer(), payload)

    /**
     * Dual-read product rehydration. Canonical V3 is authoritative for the
     * workflow graph it models; metadata/constraints that are not in V3 stay
     * sourced from the legacy Room row. Secret references deliberately resolve
     * only from the matching legacy config so plaintext never enters V3.
     */
    fun decodeToAutomation(
        payload: String,
        legacy: Automation,
    ): Automation {
        val document = decode(payload)
        require(document.workflowId == legacy.id) {
            "canonical workflow id does not match legacy row"
        }

        val triggers = document.triggers.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<TriggerType>(persisted.sourceType, "trigger")
            val fallback = legacy.triggers.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Trigger(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
            )
        }

        val actions = document.actions.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<ActionType>(persisted.sourceType, "action")
            val fallback = legacy.actions.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Action(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
                endBehavior = persisted.endBehavior?.toDomainEndBehavior(),
            )
        }

        val exitActions = document.exitActions.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<ActionType>(persisted.sourceType, "exit action")
            val fallback = legacy.exitActions.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Action(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
                endBehavior = persisted.endBehavior?.toDomainEndBehavior(),
            )
        }

        return legacy.copy(
            triggers = triggers,
            actions = actions,
            exitActions = exitActions,
            triggerMatch = when (document.conditionLogic) {
                ConditionLogic.ALL -> TriggerMatchMode.ALL
                ConditionLogic.ANY -> TriggerMatchMode.ANY
            },
        )
    }

    fun documentFor(automation: Automation): CanonicalWorkflowDocumentV3 {
        val triggers = automation.triggers.mapIndexed { index, trigger ->
            canonicalize(
                definition = AutomationNodeCatalog.definitionFor(trigger.type),
                legacyType = trigger.type.name,
                kind = LegacyNodeKind.TRIGGER,
                config = LegacyCatalogCanonicalContractNormalizer
                    .legacyConfigEntries(trigger.config),
                instanceId = CanonicalNodeId("v3.trigger.$index"),
                endBehavior = null,
            )
        }
        val actions = automation.actions.mapIndexed { index, action ->
            canonicalize(
                definition = AutomationNodeCatalog.definitionFor(action.type),
                legacyType = action.type.name,
                kind = LegacyNodeKind.ACTION,
                config = LegacyCatalogCanonicalContractNormalizer
                    .legacyConfigEntries(action.config),
                instanceId = CanonicalNodeId("v3.action.$index"),
                endBehavior = action.endBehavior,
            )
        }
        val exitActions = automation.exitActions.mapIndexed { index, action ->
            canonicalize(
                definition = AutomationNodeCatalog.definitionFor(action.type),
                legacyType = action.type.name,
                kind = LegacyNodeKind.ACTION,
                config = LegacyCatalogCanonicalContractNormalizer
                    .legacyConfigEntries(action.config),
                instanceId = CanonicalNodeId("v3.exit.$index"),
                endBehavior = action.endBehavior,
            )
        }
        val requiresLegacyFallback =
            (triggers + actions + exitActions).any { it.legacyFallbackRequired }

        return CanonicalWorkflowDocumentV3(
            workflowId = automation.id,
            conditionLogic = when (automation.triggerMatch.name) {
                "ALL" -> ConditionLogic.ALL
                else -> ConditionLogic.ANY
            },
            triggers = triggers,
            actions = actions,
            exitActions = exitActions,
            requiresLegacyFallback = requiresLegacyFallback,
        )
    }

    private fun canonicalize(
        definition: AutomationNodeDefinition,
        legacyType: String,
        kind: LegacyNodeKind,
        config: List<LegacyConfigEntry>,
        instanceId: CanonicalNodeId,
        endBehavior: EndBehavior?,
    ): CanonicalPersistedNodeV3 {
        val input = LegacyNodeInput(
            legacyType = legacyType,
            kind = kind,
            config = config,
        )
        val preview = pipeline.canonicalize(input)
        val canonicalized = preview as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical V3 write refused for $kind/$legacyType: " +
                    (preview as LegacyAdapterOutcome.Rejected).reason,
            )
        val contract = LegacyCatalogCanonicalContractNormalizer.normalize(
            definition = definition,
            node = canonicalized.node,
            config = config,
            kind = if (kind == LegacyNodeKind.ACTION) {
                NodeSchemaKind.ACTION
            } else {
                NodeSchemaKind.TRIGGER
            },
        )
        val planned = pipeline.planLegacy(
            runId = "persist:$legacyType:${instanceId.value}",
            legacyType = legacyType,
            kind = kind,
            schema = contract.schema,
            config = config,
            semantics = singleNodeSemantics,
            capabilityRequirement = CapabilityRequirement.None,
            failurePolicy = FailurePolicy.FAIL_FAST,
            validatedValues = contract.values,
        )
        val safePreserved = LegacyCatalogCanonicalContractNormalizer
            .sanitizedPreservedConfig(definition, planned.preservedConfig)

        return CanonicalPersistedNodeV3(
            sourceType = legacyType,
            node = planned.node.withCanonicalId(instanceId),
            preservedConfig = safePreserved.map {
                CanonicalLegacyEntryV3(it.key, it.rawValue)
            },
            endBehavior = endBehavior?.toCanonicalCompatibility(),
            legacyFallbackRequired = contract.containsLegacySecretMaterial,
        )
    }

    private fun EndBehavior.toCanonicalCompatibility(): CanonicalEndBehaviorV3 =
        CanonicalEndBehaviorV3(
            mode = mode.name,
            config = config.entries
                .sortedBy { it.key }
                .map { CanonicalLegacyEntryV3(it.key, it.value) },
        )

    private fun CanonicalEndBehaviorV3.toDomainEndBehavior(): EndBehavior =
        EndBehavior(
            mode = runCatching { EndMode.valueOf(mode) }.getOrElse {
                throw IllegalArgumentException("unknown end behavior mode")
            },
            config = config.associate { it.key to it.rawValue },
        )

    private fun rehydrateConfig(
        definition: AutomationNodeDefinition,
        persisted: CanonicalPersistedNodeV3,
        legacyConfig: Map<String, String>,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        persisted.preservedConfig.forEach { entry ->
            result[entry.key] = entry.rawValue
        }
        val arguments = nodeArguments(persisted.node)
            .entries
            .associateBy { it.id.value }

        definition.configuration.fields.forEach { field ->
            val value = arguments[field.key]?.value ?: return@forEach
            if (field.sensitive || field.valueType == NodeConfigValueType.SECRET) {
                if (value !is SecretReferenceValue) {
                    throw IllegalArgumentException(
                        "sensitive field ${field.key} lost its secret reference",
                    )
                }
                val rawSecret = legacyConfig[field.key]
                if (!rawSecret.isNullOrEmpty()) {
                    result[field.key] = rawSecret
                } else if (persisted.legacyFallbackRequired) {
                    throw IllegalArgumentException(
                        "legacy secret fallback missing for ${definition.legacyTypeName}/${field.key}",
                    )
                }
                return@forEach
            }
            canonicalValueToLegacy(field, value)?.let { raw ->
                result[field.key] = raw
            }
        }
        return result
    }

    private fun canonicalValueToLegacy(
        field: com.nexaflow.domain.catalog.NodeConfigField,
        value: CanonicalValue,
    ): String? {
        if (value is ExpressionValue) return value.source
        return when (field.valueType) {
            NodeConfigValueType.STRING -> (value as? TextValue)?.value
            NodeConfigValueType.INTEGER -> (value as? IntegerValue)?.value?.toString()
            NodeConfigValueType.DECIMAL -> (value as? DecimalValue)?.value
            NodeConfigValueType.BOOLEAN -> (value as? BooleanValue)?.value?.toString()
            NodeConfigValueType.ENUM -> (value as? EnumTokenValue)?.token
            NodeConfigValueType.TIME -> (value as? TimeOfDayValue)?.let {
                "%02d:%02d".format(it.minuteOfDay / 60, it.minuteOfDay % 60)
            }
            NodeConfigValueType.DATE -> (value as? DateValue)?.isoDate
            NodeConfigValueType.DURATION_SECONDS -> (value as? DurationValue)?.let {
                require(it.milliseconds % 1000L == 0L) {
                    "legacy seconds field cannot represent fractional milliseconds"
                }
                (it.milliseconds / 1000L).toString()
            }
            NodeConfigValueType.PACKAGE -> (value as? PackageIdValue)?.packageName
            NodeConfigValueType.URL -> (value as? UriValue)?.value
            NodeConfigValueType.SECRET -> null
            NodeConfigValueType.JSON -> (value as? JsonValue)?.value?.toString()
            NodeConfigValueType.COORDINATE -> when (value) {
                is DecimalValue -> value.value
                is CoordinateValue -> "${value.latitude},${value.longitude}"
                else -> null
            }
        }
    }

    private fun nodeArguments(node: CanonicalNode): CanonicalArguments = when (node) {
        is CanonicalActionNode -> node.arguments
        is ObserveNode -> node.arguments
        else -> CanonicalArguments.EMPTY
    }

    private inline fun <reified T : Enum<T>> enumValueOrThrow(
        raw: String,
        label: String,
    ): T = enumValues<T>().firstOrNull { it.name == raw }
        ?: throw IllegalArgumentException("unknown canonical source $label: $raw")
}

private fun CanonicalNode.withCanonicalId(id: CanonicalNodeId): CanonicalNode = when (this) {
    is ObserveNode -> copy(id = id)
    is CompareNode -> copy(id = id)
    is SetStateNode -> copy(id = id)
    is SetValueNode -> copy(id = id)
    is InvokeNode -> copy(id = id)
    is OpenNode -> copy(id = id)
    is SendNode -> copy(id = id)
    is TransformNode -> copy(id = id)
    is InputNode -> copy(id = id)
    is WaitNode -> copy(id = id)
    is RestoreNode -> copy(id = id)
    is SequenceNode -> copy(id = id)
    is BranchNode -> copy(id = id)
    is ObservedConditionNode -> copy(
        id = id,
        observation = observation.copy(id = id),
    )
    is ComparisonConditionNode -> copy(
        id = id,
        comparison = comparison.copy(id = id),
    )
}
