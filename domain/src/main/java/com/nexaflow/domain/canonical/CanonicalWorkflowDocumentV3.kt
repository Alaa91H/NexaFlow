package com.nexaflow.domain.canonical

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persisted canonical payload written beside the historical Room columns.
 *
 * The canonical nodes are the executable intent. [sourceType] and
 * [preservedConfig] are compatibility/audit metadata only and are never used
 * as canonical execution commands. The existing Room columns remain the
 * rollback reader until the controlled-read phase is complete.
 */
@Serializable
data class CanonicalWorkflowDocumentV3(
    val schemaVersion: Int = SCHEMA_VERSION,
    val workflowId: String,
    val conditionLogic: ConditionLogic,
    val triggers: List<CanonicalPersistedNodeV3>,
    val actions: List<CanonicalPersistedNodeV3>,
    val exitActions: List<CanonicalPersistedNodeV3>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported canonical workflow schemaVersion=$schemaVersion"
        }
        require(workflowId.isNotBlank()) { "workflowId must not be blank" }
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

/**
 * T27 production codec. It uses the exact T14/T15/T17-T25 adapter stack used
 * by the canonical runtime pipeline; persistence can therefore never invent a
 * second mapping table.
 */
object CanonicalWorkflowV3Codec {

    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        classDiscriminator = "canonicalType"
    }

    private val adapter: LegacyCanonicalAdapter by lazy {
        CanonicalRuntimePipeline.defaultAdapter()
    }

    fun encode(automation: Automation): String =
        json.encodeToString(documentFor(automation))

    fun encodeOrNull(automation: Automation): String? =
        runCatching { encode(automation) }.getOrNull()

    fun decode(payload: String): CanonicalWorkflowDocumentV3 =
        json.decodeFromString(CanonicalWorkflowDocumentV3.serializer(), payload)

    fun documentFor(automation: Automation): CanonicalWorkflowDocumentV3 =
        CanonicalWorkflowDocumentV3(
            workflowId = automation.id,
            conditionLogic = when (automation.triggerMatch.name) {
                "ALL" -> ConditionLogic.ALL
                else -> ConditionLogic.ANY
            },
            triggers = automation.triggers.mapIndexed { index, trigger ->
                canonicalize(
                    legacyType = trigger.type.name,
                    kind = LegacyNodeKind.TRIGGER,
                    config = trigger.config.entries
                        .sortedBy { it.key }
                        .map { LegacyConfigEntry(it.key, it.value) },
                    instanceId = CanonicalNodeId("v3.trigger.$index"),
                    endBehavior = null,
                )
            },
            actions = automation.actions.mapIndexed { index, action ->
                canonicalize(
                    legacyType = action.type.name,
                    kind = LegacyNodeKind.ACTION,
                    config = action.config.entries
                        .sortedBy { it.key }
                        .map { LegacyConfigEntry(it.key, it.value) },
                    instanceId = CanonicalNodeId("v3.action.$index"),
                    endBehavior = action.endBehavior,
                )
            },
            exitActions = automation.exitActions.mapIndexed { index, action ->
                canonicalize(
                    legacyType = action.type.name,
                    kind = LegacyNodeKind.ACTION,
                    config = action.config.entries
                        .sortedBy { it.key }
                        .map { LegacyConfigEntry(it.key, it.value) },
                    instanceId = CanonicalNodeId("v3.exit.$index"),
                    endBehavior = action.endBehavior,
                )
            },
        )

    private fun canonicalize(
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
        val outcome = adapter.canonicalize(input)
        val canonicalized = outcome as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical V3 write refused for $kind/$legacyType: " +
                    (outcome as LegacyAdapterOutcome.Rejected).reason,
            )

        return CanonicalPersistedNodeV3(
            sourceType = legacyType,
            node = canonicalized.node.withCanonicalId(instanceId),
            preservedConfig = canonicalized.preservedConfig.map {
                CanonicalLegacyEntryV3(it.key, it.rawValue)
            },
            endBehavior = endBehavior?.toCanonicalCompatibility(),
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
