package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ConditionResult
import kotlinx.serialization.Serializable

/** Explicit runtime declaration for a canonical operation or predicate. */
@Serializable
data class CanonicalNodeExecutionContract(
    val definitionId: String,
    val schema: NodeSchema,
    val semantics: NodeSelectionSemantics,
    val capabilityRequirement: CapabilityRequirement,
    val executionPolicy: PlanExecutionPolicy,
    val failurePolicy: FailurePolicy,
) {
    init {
        require(definitionId.matches(Regex("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+")))
    }
}

/** Registry refuses duplicate IDs and never falls back to a legacy type. */
class CanonicalNodeExecutionRegistry(contracts: List<CanonicalNodeExecutionContract>) {
    private val byId = contracts.associateBy(CanonicalNodeExecutionContract::definitionId)

    init {
        require(byId.size == contracts.size) { "canonical node execution contract registered twice" }
    }

    fun contractFor(definitionId: String): CanonicalNodeExecutionContract? = byId[definitionId]

    fun all(): List<CanonicalNodeExecutionContract> = byId.values.sortedBy { it.definitionId }
}

/** Validates contract, target, semantic ID and typed args before invoking the planner. */
class CanonicalNativeWorkflowPlanner(
    private val registry: CanonicalNodeExecutionRegistry,
    private val planner: CanonicalExecutionPlanner = CanonicalExecutionPlanner.of(
        CanonicalCommandSemanticsCatalog.all(),
    ),
) {
    fun plan(node: CanonicalWorkflowNode): ExecutionPlan {
        val contract = registry.contractFor(node.definitionId)
            ?: throw IllegalArgumentException("canonical node has no registered runtime contract")
        require(contract.schema == node.schema) { "canonical node schema does not match runtime registration" }
        val schemaSemantic = node.schema.operation?.value ?: requireNotNull(node.schema.predicate).value
        val nodeSemantic = when (val canonical = node.node) {
            is CanonicalActionNode -> operationOf(canonical).value
            is WaitNode -> OperationId("core.operation.wait").value
            is ObserveNode -> canonical.predicate.value
            else -> throw IllegalArgumentException("canonical node primitive is not executable as a registry item")
        }
        require(schemaSemantic == nodeSemantic) { "canonical node semantic does not match runtime registration" }
        val requirement = CanonicalWorkflowContract(
            schema = contract.schema,
            semantics = contract.semantics,
            capabilityRequirement = contract.capabilityRequirement,
        )
        val verdict = validate(
            CanonicalWorkflowAst(root = node.node),
            requirement,
            node.arguments,
        )
        require(verdict.isValid) {
            "canonical node validation failed: ${verdict.findings.first().rule}"
        }
        return planner.planValidated(
            ast = CanonicalWorkflowAst(root = node.node),
            contract = requirement,
            values = node.arguments,
            executionPolicy = contract.executionPolicy,
            failurePolicy = contract.failurePolicy,
        )
    }

    fun planAll(nodes: List<CanonicalWorkflowNode>): List<ExecutionPlan> = nodes.map(::plan)

    private fun operationOf(node: CanonicalActionNode): OperationId = when (node) {
        is SetStateNode -> OperationId("core.operation.set_state")
        is SetValueNode -> OperationId("core.operation.set_value")
        is InvokeNode -> node.operation
        is OpenNode -> node.operation
        is SendNode -> node.operation
        is TransformNode -> node.operation
        is InputNode -> node.operation
        is RestoreNode -> OperationId("core.operation.set_state")
    }
}

@Serializable
enum class CanonicalTriggerSourceKind {
    EVENT,
    STATE,
    THRESHOLD,
    SCHEDULE,
}

/** Typed event/state source declaration for canonical triggers independent of legacy enums. */
@Serializable
data class CanonicalTriggerSourceContract(
    val definitionId: String,
    val schema: NodeSchema,
    val sourceKind: CanonicalTriggerSourceKind,
    val capabilityRequirement: CapabilityRequirement,
    val duplicateWindowMs: Long = 30_000L,
    val maximumOccurrenceIdLength: Int = 256,
) {
    init {
        require(schema.kind == NodeSchemaKind.TRIGGER)
        require(definitionId.matches(Regex("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)+")))
        require(duplicateWindowMs in 0L..86_400_000L)
        require(maximumOccurrenceIdLength in 16..512)
    }
}

class CanonicalTriggerSourceRegistry(contracts: List<CanonicalTriggerSourceContract>) {
    private val byId = contracts.associateBy(CanonicalTriggerSourceContract::definitionId)
    init { require(byId.size == contracts.size) { "canonical trigger source registered twice" } }
    fun contractFor(definitionId: String): CanonicalTriggerSourceContract? = byId[definitionId]
    fun all(): List<CanonicalTriggerSourceContract> = byId.values.toList()
}

data class CanonicalTriggerEvaluation(
    val decision: ConditionResult,
    val results: List<ConditionResult>,
    val duplicate: Boolean = false,
)

/** Runtime-independent ANY/ALL evaluator preserving UNKNOWN, UNAVAILABLE and ERROR. */
class CanonicalTriggerEvaluator(
    private val sources: CanonicalTriggerSourceRegistry,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val occurrenceLock = Any()
    private val occurrences = linkedMapOf<String, Long>()

    suspend fun evaluate(
        nodes: List<CanonicalWorkflowNode>,
        logic: ConditionLogic,
        occurrenceId: String? = null,
        providers: Map<String, suspend (CanonicalWorkflowNode) -> ConditionResult>,
        capabilitySnapshot: com.nexaflow.domain.capability.CapabilitySnapshot =
            com.nexaflow.domain.capability.CapabilitySnapshot(),
    ): CanonicalTriggerEvaluation {
        val triggers = nodes.filter { it.kind == NodeSchemaKind.TRIGGER }
        if (triggers.isEmpty()) return CanonicalTriggerEvaluation(ConditionResult.Unavailable, emptyList())
        val results = mutableListOf<ConditionResult>()
        for (node in triggers) {
            val source = sources.contractFor(node.definitionId)
                ?: return CanonicalTriggerEvaluation(
                    ConditionResult.Unavailable,
                    List(triggers.size) { ConditionResult.Unavailable },
                )
            if (source.schema != node.schema) return CanonicalTriggerEvaluation(
                ConditionResult.Unavailable,
                List(triggers.size) { ConditionResult.Unavailable },
            )
            if (!com.nexaflow.domain.capability.CapabilityRequirementResolver.resolve(
                    source.capabilityRequirement,
                    capabilitySnapshot,
                ).available
            ) {
                return CanonicalTriggerEvaluation(ConditionResult.Unavailable, List(triggers.size) { ConditionResult.Unavailable })
            }
            val provider = providers[node.definitionId]
                ?: return CanonicalTriggerEvaluation(
                    ConditionResult.Unavailable,
                    List(triggers.size) { ConditionResult.Unavailable },
                )
            val result = provider(node)
            if (result == ConditionResult.Satisfied && source.sourceKind == CanonicalTriggerSourceKind.EVENT) {
                if (occurrenceId.isNullOrBlank() || occurrenceId.length > source.maximumOccurrenceIdLength) {
                    return CanonicalTriggerEvaluation(ConditionResult.Unknown, List(triggers.size) { ConditionResult.Unknown })
                }
                if (!admitOccurrence(node.definitionId, occurrenceId, source.duplicateWindowMs)) {
                    return CanonicalTriggerEvaluation(
                        ConditionResult.Unsatisfied,
                        List(triggers.size) { ConditionResult.Unsatisfied },
                        duplicate = true,
                    )
                }
            }
            results += result
        }
        val decision = when (logic) {
            ConditionLogic.ANY -> when {
                results.any { it == ConditionResult.Satisfied } -> ConditionResult.Satisfied
                results.any { it is ConditionResult.Error } -> results.first { it is ConditionResult.Error }
                results.any { it == ConditionResult.Unknown } -> ConditionResult.Unknown
                results.any { it == ConditionResult.Unavailable } -> ConditionResult.Unavailable
                else -> ConditionResult.Unsatisfied
            }
            ConditionLogic.ALL -> when {
                results.any { it == ConditionResult.Unsatisfied } -> ConditionResult.Unsatisfied
                results.any { it is ConditionResult.Error } -> results.first { it is ConditionResult.Error }
                results.any { it == ConditionResult.Unknown } -> ConditionResult.Unknown
                results.any { it == ConditionResult.Unavailable } -> ConditionResult.Unavailable
                else -> ConditionResult.Satisfied
            }
        }
        return CanonicalTriggerEvaluation(decision, results)
    }

    private fun admitOccurrence(sourceId: String, occurrenceId: String, windowMs: Long): Boolean {
        synchronized(occurrenceLock) {
            val now = nowMs()
            val key = "$sourceId:$occurrenceId"
            val previous = occurrences[key]
            if (previous != null && now - previous in 0L..windowMs) return false
            occurrences[key] = now
            occurrences.entries.removeAll { now - it.value > MAX_OCCURRENCE_RETENTION_MS }
            while (occurrences.size > MAX_OCCURRENCES) occurrences.remove(occurrences.keys.first())
            return true
        }
    }

    private companion object {
        const val MAX_OCCURRENCES = 4_096
        const val MAX_OCCURRENCE_RETENTION_MS = 86_400_000L
    }
}
