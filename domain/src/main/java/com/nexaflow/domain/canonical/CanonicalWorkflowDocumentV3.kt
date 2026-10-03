package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
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
    /** Stable canonical identity; null only for pre-identity V3 payloads. */
    val targetId: String? = null,
    /** Operation for action nodes or predicate for trigger nodes. */
    val semanticId: String? = null,
    val preservedConfig: List<CanonicalLegacyEntryV3> = emptyList(),
    /**
     * Presence ledger for compatibility rehydration. Keys are non-secret
     * metadata only; raw values remain in the typed node or protected legacy
     * fallback. Null means an older V3 payload written before this ledger.
     */
    val suppliedConfigKeys: List<String>? = null,
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
            targetId = contract.schema.target.value,
            semanticId = (contract.schema.operation?.value ?: contract.schema.predicate?.value),
            preservedConfig = safePreserved.map {
                CanonicalLegacyEntryV3(it.key, it.rawValue)
            },
            suppliedConfigKeys = config.map { it.key }.distinct().sorted(),
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
