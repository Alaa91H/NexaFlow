package com.nexaflow.core.execution.compat

import com.nexaflow.domain.canonical.AtomicCommand
import com.nexaflow.domain.canonical.CanonicalNode
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.CanonicalRuntimePipeline
import com.nexaflow.domain.canonical.ExecutionMode
import com.nexaflow.domain.canonical.FailurePolicy
import com.nexaflow.domain.canonical.LegacyAdapterOutcome
import com.nexaflow.domain.canonical.LegacyCatalogCanonicalContractNormalizer
import com.nexaflow.domain.canonical.LegacyConfigEntry
import com.nexaflow.domain.canonical.LegacyNodeInput
import com.nexaflow.domain.canonical.LegacyNodeKind
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.NodeSelectionSemantics
import com.nexaflow.domain.canonical.TargetSelectionMode
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Trigger

/**
 * T26 production compatibility boundary.
 *
 * Legacy enums/maps stop here. The shared domain normalizer converts the
 * catalog contract into typed canonical values, then planLegacy performs the
 * real AST -> validation -> planner path before a compatibility provider can
 * receive the old Action object.
 */
class CanonicalRuntimeCutoverAdapter(
    private val pipeline: CanonicalRuntimePipeline = CanonicalRuntimePipeline(),
) {
    data class PreparedAction(
        val node: CanonicalNode,
        val command: AtomicCommand,
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
        val rawConfig = LegacyCatalogCanonicalContractNormalizer
            .legacyConfigEntries(action.config)
        val definition = AutomationNodeCatalog.definitionFor(action.type)
        val preview = canonicalized(
            legacyType = action.type.name,
            kind = LegacyNodeKind.ACTION,
            config = rawConfig,
        )
        val contract = LegacyCatalogCanonicalContractNormalizer.normalize(
            definition = definition,
            node = preview.node,
            config = rawConfig,
            kind = NodeSchemaKind.ACTION,
        )
        val planned = runCatching {
            pipeline.planLegacy(
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
        }.getOrElse { failure ->
            throw IllegalArgumentException(
                "canonical action ${action.type.name} failed during validation/planning: " +
                    failure.message.orEmpty(),
                failure,
            )
        }
        val commands = planned.plan.allCommands
        require(commands.size == 1) {
            "legacy action ${action.type.name} must plan to exactly one atomic command"
        }
        return PreparedAction(
            node = planned.node.withRuntimeId(CanonicalNodeId(instanceId)),
            command = commands.single().copy(commandId = instanceId),
            preservedConfig = LegacyCatalogCanonicalContractNormalizer
                .sanitizedPreservedConfig(definition, planned.preservedConfig),
        )
    }

    fun prepareTrigger(
        trigger: Trigger,
        runId: String,
        instanceId: String,
    ): PreparedTrigger {
        val rawConfig = LegacyCatalogCanonicalContractNormalizer
            .legacyConfigEntries(trigger.config)
        val definition = AutomationNodeCatalog.definitionFor(trigger.type)
        val preview = canonicalized(
            legacyType = trigger.type.name,
            kind = LegacyNodeKind.TRIGGER,
            config = rawConfig,
        )
        val contract = LegacyCatalogCanonicalContractNormalizer.normalize(
            definition = definition,
            node = preview.node,
            config = rawConfig,
            kind = NodeSchemaKind.TRIGGER,
        )
        val planned = runCatching {
            pipeline.planLegacy(
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
        }.getOrElse { failure ->
            throw IllegalArgumentException(
                "canonical trigger ${trigger.type.name} failed during validation/planning: " +
                    failure.message.orEmpty(),
                failure,
            )
        }
        return PreparedTrigger(
            node = planned.node.withRuntimeId(CanonicalNodeId(instanceId)),
            preservedConfig = LegacyCatalogCanonicalContractNormalizer
                .sanitizedPreservedConfig(definition, planned.preservedConfig),
        )
    }

    private fun canonicalized(
        legacyType: String,
        kind: LegacyNodeKind,
        config: List<LegacyConfigEntry>,
    ): LegacyAdapterOutcome.Canonicalized {
        val outcome = pipeline.canonicalize(
            LegacyNodeInput(legacyType, kind, config),
        )
        return outcome as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical adapter rejected $legacyType: " +
                    (outcome as LegacyAdapterOutcome.Rejected).reason,
            )
    }

    private companion object {
        val SINGLE_NODE_SEMANTICS = NodeSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.SINGLE,
            executionMode = ExecutionMode.SINGLE,
        )
    }
}

private fun CanonicalNode.withRuntimeId(id: CanonicalNodeId): CanonicalNode = when (this) {
    is com.nexaflow.domain.canonical.ObserveNode -> copy(id = id)
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
