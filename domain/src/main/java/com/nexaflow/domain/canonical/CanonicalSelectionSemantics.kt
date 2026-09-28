package com.nexaflow.domain.canonical

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TargetSelectionMode {
    SINGLE,
    MULTI
}

@Serializable
enum class EventLogic {
    ANY_OF
}

@Serializable
enum class ConditionLogic {
    ANY,
    ALL
}

@Serializable
enum class ExecutionMode {
    SINGLE,
    BATCH,
    ORDERED
}

@Serializable
enum class FailurePolicy {
    FAIL_FAST,
    CONTINUE_ON_ERROR,
    ROLLBACK_WHEN_SUPPORTED
}

/**
 * Explicit semantics for one user-authored action selection.
 *
 * This is intentionally separate from UI presentation and from the later
 * execution planner. T05 records what the user selected; T10 decides how an
 * eligible batch may be scheduled physically.
 */
@Serializable
data class ActionSelectionSemantics(
    val targetSelectionMode: TargetSelectionMode,
    val executionMode: ExecutionMode,
    val failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST
) {
    init {
        when (targetSelectionMode) {
            TargetSelectionMode.SINGLE -> {
                require(executionMode == ExecutionMode.SINGLE) {
                    "SINGLE selection requires SINGLE execution"
                }
                require(failurePolicy == FailurePolicy.FAIL_FAST) {
                    "SINGLE selection does not accept batch failure policies"
                }
            }
            TargetSelectionMode.MULTI -> {
                require(executionMode != ExecutionMode.SINGLE) {
                    "MULTI selection requires BATCH or ORDERED execution"
                }
            }
        }
    }

    companion object {
        val SINGLE = ActionSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.SINGLE,
            executionMode = ExecutionMode.SINGLE
        )

        fun batch(
            failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST
        ): ActionSelectionSemantics = ActionSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.MULTI,
            executionMode = ExecutionMode.BATCH,
            failurePolicy = failurePolicy
        )

        fun ordered(
            failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST
        ): ActionSelectionSemantics = ActionSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.MULTI,
            executionMode = ExecutionMode.ORDERED,
            failurePolicy = failurePolicy
        )
    }
}

/**
 * Explicit event disjunction. Events never expose ALL because mutually
 * exclusive transitions cannot be made true simultaneously by declaration.
 */
@Serializable
@SerialName("event_selection")
data class EventSelectionNode(
    override val id: CanonicalNodeId,
    val events: List<ObserveNode>,
    val logic: EventLogic = EventLogic.ANY_OF
) : CanonicalConditionNode {
    init {
        require(events.size in 2..MAX_SELECTION_ITEMS) {
            "EventSelectionNode requires 2..$MAX_SELECTION_ITEMS events"
        }
        require(events.map { it.id }.distinct().size == events.size) {
            "EventSelectionNode event ids must be unique"
        }
    }

    override val primitive: CanonicalPrimitive = CanonicalPrimitive.EVENT_SELECTION
}

/**
 * Boolean condition composition. ANY/ALL apply only to conditions, never to
 * event alternatives or action execution.
 */
@Serializable
@SerialName("condition_group")
data class ConditionGroupNode(
    override val id: CanonicalNodeId,
    val conditions: List<CanonicalConditionNode>,
    val logic: ConditionLogic
) : CanonicalConditionNode {
    init {
        require(conditions.size in 2..MAX_SELECTION_ITEMS) {
            "ConditionGroupNode requires 2..$MAX_SELECTION_ITEMS conditions"
        }
        require(conditions.map { it.id }.distinct().size == conditions.size) {
            "ConditionGroupNode condition ids must be unique"
        }
    }

    override val primitive: CanonicalPrimitive = CanonicalPrimitive.CONDITION_GROUP
}

/**
 * A single or multi-selected set of atomic actions with explicit execution
 * semantics. Leaf actions remain atomic; grouping is represented here instead
 * of overloading each individual action with list-specific behavior.
 */
@Serializable
@SerialName("action_selection")
data class ActionSelectionNode(
    override val id: CanonicalNodeId,
    val actions: List<CanonicalActionNode>,
    val semantics: ActionSelectionSemantics
) : CanonicalNode {
    init {
        val expectedRange = when (semantics.targetSelectionMode) {
            TargetSelectionMode.SINGLE -> 1..1
            TargetSelectionMode.MULTI -> 2..MAX_SELECTION_ITEMS
        }
        require(actions.size in expectedRange) {
            "${semantics.targetSelectionMode} selection contains invalid action count=${actions.size}"
        }
        require(actions.map { it.id }.distinct().size == actions.size) {
            "ActionSelectionNode action ids must be unique"
        }
        require(actions.distinct().size == actions.size) {
            "ActionSelectionNode cannot contain duplicate actions"
        }
    }

    override val primitive: CanonicalPrimitive = CanonicalPrimitive.ACTION_SELECTION
}

const val MAX_SELECTION_ITEMS: Int = 128
