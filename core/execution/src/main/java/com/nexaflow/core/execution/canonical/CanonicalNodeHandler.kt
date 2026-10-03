package com.nexaflow.core.execution.canonical

import com.nexaflow.core.execution.workflow.WorkflowExecutionBudget
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.CanonicalNodeExecutionContract
import com.nexaflow.domain.canonical.CanonicalNativeWorkflowPlanner
import com.nexaflow.domain.canonical.CanonicalNodeExecutionRegistry
import com.nexaflow.domain.canonical.CanonicalExecutionPlanner
import com.nexaflow.domain.canonical.ExecutionPlan
import com.nexaflow.domain.capability.CapabilityStatus
import kotlinx.coroutines.CancellationException

data class CanonicalNodeExecutionContext(
    val workflowId: String,
    val runId: String,
    val budget: WorkflowExecutionBudget,
)

data class CanonicalNodeExecutionOutcome(
    val status: CapabilityStatus,
    val message: String,
) {
    val success: Boolean get() = status == CapabilityStatus.SUCCESS
}

/** Provider for exactly one registered canonical definition. */
interface CanonicalNodeHandler {
    val definitionId: String
    suspend fun execute(
        node: CanonicalWorkflowNode,
        plan: ExecutionPlan,
        context: CanonicalNodeExecutionContext,
    ): CanonicalNodeExecutionOutcome
}

/** Handler registry rejects collisions and has no legacy enum fallback. */
class CanonicalNodeHandlerRegistry(handlers: List<CanonicalNodeHandler>) {
    private val byId = handlers.associateBy(CanonicalNodeHandler::definitionId)

    init {
        require(byId.size == handlers.size) { "canonical node handler registered twice" }
    }

    fun handlerFor(definitionId: String): CanonicalNodeHandler? = byId[definitionId]
}

/** Plans every node against the runtime contract before any registered handler is invoked. */
class CanonicalNodeDispatcher(
    contracts: List<CanonicalNodeExecutionContract>,
    private val handlers: CanonicalNodeHandlerRegistry,
    planner: CanonicalExecutionPlanner = CanonicalExecutionPlanner.of(
        com.nexaflow.domain.canonical.CanonicalCommandSemanticsCatalog.all(),
    ),
    private val registeredDefinitionIds: Set<String> = contracts.mapTo(linkedSetOf()) { it.definitionId },
    private val capabilitySnapshot: () -> com.nexaflow.domain.capability.CapabilitySnapshot = { com.nexaflow.domain.capability.CapabilitySnapshot() },
) {
    private val contractRegistry = CanonicalNodeExecutionRegistry(contracts)
    private val planner = CanonicalNativeWorkflowPlanner(
        registry = contractRegistry,
        planner = planner,
    )

    suspend fun execute(
        node: CanonicalWorkflowNode,
        context: CanonicalNodeExecutionContext,
    ): CanonicalNodeExecutionOutcome {
        if (node.definitionId !in registeredDefinitionIds) {
            return CanonicalNodeExecutionOutcome(
                CapabilityStatus.UNSUPPORTED,
                "No canonical runtime contract is registered for this node",
            )
        }
        val handler = handlers.handlerFor(node.definitionId)
            ?: return CanonicalNodeExecutionOutcome(
                CapabilityStatus.UNSUPPORTED,
                "No canonical runtime handler is registered for this node",
            )
        val plan = try {
            val contract = contractRegistry.contractFor(node.definitionId)
                ?: error("canonical node contract is missing")
            require(
                com.nexaflow.domain.capability.CapabilityRequirementResolver.resolve(
                    contract.capabilityRequirement,
                    capabilitySnapshot(),
                ).available,
            ) { "canonical node capability is unavailable" }
            planner.plan(node)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IllegalArgumentException) {
            return CanonicalNodeExecutionOutcome(
                CapabilityStatus.FAILED,
                "Canonical node validation or planning failed",
            )
        }
        if (context.budget.isExpired() || !context.budget.tryConsumeNodeVisit()) {
            return CanonicalNodeExecutionOutcome(
                CapabilityStatus.FAILED,
                "Canonical workflow execution budget is exhausted",
            )
        }
        return try {
            handler.execute(node, plan, context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CanonicalNodeExecutionOutcome(CapabilityStatus.FAILED, "Canonical node handler failed")
        }
    }

    fun preflight(nodes: List<CanonicalWorkflowNode>) {
        nodes.forEach { node ->
            require(registeredDefinitionIds.contains(node.definitionId)) {
                "canonical node has no registered runtime contract"
            }
            require(handlers.handlerFor(node.definitionId) != null) {
                "canonical node has no registered runtime handler"
            }
            planner.plan(node)
        }
    }
}
