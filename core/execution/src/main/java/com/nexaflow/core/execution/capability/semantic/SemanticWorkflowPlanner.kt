package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndMode

data class SemanticWorkflowNodePlan(
    val owner: String,
    val actionType: ActionType,
    val plan: OperationExecutionPlan
)

data class SemanticWorkflowExecutionPlan(
    val nodes: List<SemanticWorkflowNodePlan>
) {
    val executable: Boolean
        get() = nodes.all { it.plan.executable }

    val pendingUserActionOwners: Set<String>
        get() = nodes
            .filter { it.plan.status == OperationPlanStatus.PENDING_USER_ACTION }
            .mapTo(linkedSetOf()) { it.owner }

    val unavailableOwners: Set<String>
        get() = nodes
            .filter {
                it.plan.status == OperationPlanStatus.UNAVAILABLE ||
                    it.plan.status == OperationPlanStatus.INVALID_CONFIGURATION
            }
            .mapTo(linkedSetOf()) { it.owner }
}

/**
 * Plans every semantic action in a workflow before the first side effect.
 *
 * Non-semantic actions remain owned by the existing capability/legacy
 * preflight. Semantic nodes, however, use the exact live strategy ranking that
 * execution will use, so a Settings-only hand-off can no longer masquerade as
 * an automatically executable workflow.
 */
class SemanticWorkflowPlanner(
    private val actionRouter: SemanticActionRouter
) {
    suspend fun plan(
        automation: Automation,
        executionId: String? = null
    ): SemanticWorkflowExecutionPlan {
        val nodes = buildList {
            automation.actions.forEachIndexed { index, action ->
                addPlan(
                    owner = "action:$index:${action.type.name}",
                    action = action,
                    workflowId = automation.id,
                    executionId = executionId
                )
                action.endBehavior
                    ?.takeIf { it.mode == EndMode.SET_VALUE }
                    ?.let { endBehavior ->
                        addPlan(
                            owner = "endBehavior:$index:${action.type.name}",
                            action = action.withConfig(endBehavior.config),
                            workflowId = automation.id,
                            executionId = executionId
                        )
                    }
            }
            automation.exitActions.forEachIndexed { index, action ->
                addPlan(
                    owner = "exitAction:$index:${action.type.name}",
                    action = action,
                    workflowId = automation.id,
                    executionId = executionId
                )
            }
        }
        return SemanticWorkflowExecutionPlan(nodes)
    }

    private suspend fun MutableList<SemanticWorkflowNodePlan>.addPlan(
        owner: String,
        action: Action,
        workflowId: String,
        executionId: String?
    ) {
        val plan = actionRouter.planIfSupported(action, workflowId, executionId) ?: return
        add(
            SemanticWorkflowNodePlan(
                owner = owner,
                actionType = action.type,
                plan = plan
            )
        )
    }
}
