package com.nexaflow.core.execution.canonical

import com.nexaflow.domain.canonical.CanonicalTriggerEvaluation
import com.nexaflow.domain.canonical.CanonicalTriggerEvaluator
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.ConditionLogic
import com.nexaflow.domain.canonical.CanonicalTriggerSourceRegistry
import com.nexaflow.domain.models.ConditionResult

/** Platform adapter for an event/state source registered by stable definition ID. */
interface CanonicalTriggerSourceHandler {
    val definitionId: String
    suspend fun evaluate(node: CanonicalWorkflowNode, occurrenceId: String?): ConditionResult
}

class CanonicalTriggerHandlerRegistry(handlers: List<CanonicalTriggerSourceHandler>) {
    private val byId = handlers.associateBy(CanonicalTriggerSourceHandler::definitionId)
    init { require(byId.size == handlers.size) { "canonical trigger handler registered twice" } }
    fun handlerFor(definitionId: String): CanonicalTriggerSourceHandler? = byId[definitionId]
}

/** Executes native source adapters through the typed ANY/ALL evaluator. */
class CanonicalTriggerDispatcher(
    private val evaluator: CanonicalTriggerEvaluator,
    private val handlers: CanonicalTriggerHandlerRegistry,
    private val sources: CanonicalTriggerSourceRegistry,
    private val capabilitySnapshot: () -> com.nexaflow.domain.capability.CapabilitySnapshot = { com.nexaflow.domain.capability.CapabilitySnapshot() },
) {
    fun executableContracts(): List<com.nexaflow.domain.canonical.CanonicalTriggerSourceContract> =
        sources.all().filter { handlers.handlerFor(it.definitionId) != null }.sortedBy { it.definitionId }

    suspend fun evaluate(
        nodes: List<CanonicalWorkflowNode>,
        logic: ConditionLogic,
        occurrenceId: String? = null,
        evaluatorOverrides: Map<String, suspend (CanonicalWorkflowNode) -> ConditionResult> = emptyMap(),
    ): CanonicalTriggerEvaluation {
        val triggers = nodes.filter { it.kind == com.nexaflow.domain.canonical.NodeSchemaKind.TRIGGER }
            .sortedBy { it.sequenceIndex }
        return evaluator.evaluate(
        nodes = triggers,
        logic = logic,
        occurrenceId = occurrenceId,
        providers = evaluatorOverrides + triggers
            .mapNotNull { node ->
                val handler = handlers.handlerFor(node.definitionId) ?: return@mapNotNull null
                val contract = sources.contractFor(node.definitionId) ?: return@mapNotNull null
                if (contract.schema != node.schema) return@mapNotNull null
                if (!com.nexaflow.domain.capability.CapabilityRequirementResolver
                        .resolve(contract.capabilityRequirement, capabilitySnapshot()).available
                ) return@mapNotNull null
                node.definitionId to suspend { currentNode: CanonicalWorkflowNode ->
                    handler.evaluate(currentNode, occurrenceId)
                }
            }.toMap(),
        capabilitySnapshot = capabilitySnapshot(),
        )
    }
}
