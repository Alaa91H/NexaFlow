package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T05 — explicit selection, combination, and execution semantics for canonical
 * nodes, as decided in ADR-003.
 *
 * The concepts are deliberately kept separate. Overloading a single ANY/ALL
 * flag across targets, events, conditions, and execution is the ambiguity this
 * phase removes: each dimension declares its own meaning and the validator
 * fails closed when a declaration is missing or contradictory.
 */
@Serializable
enum class TargetSelectionMode {
    /** Exactly one target. */
    SINGLE,

    /** One or more targets; requires explicit execution semantics. */
    MULTI,
}

/**
 * Combination logic for events. Only [EventLogic.ANY_OF] exists by design:
 * several observed events are an "or" over event occurrences. "All events must
 * have happened" is a temporal/sequence question for the trigger compiler, not
 * a boolean flag here (ADR-003 invariant).
 */
@Serializable
enum class EventLogic {
    ANY_OF,
}

/** Combination logic for conditions. */
@Serializable
enum class ConditionLogic {
    ANY,
    ALL,
}

/** How a multi-target selection is executed. */
@Serializable
enum class ExecutionMode {
    /** Single-target execution; incompatible with [TargetSelectionMode.MULTI]. */
    SINGLE,

    /**
     * Targets execute as one atomic batch. Contradictory writes to the same
     * target are rejected by [validateSelectionSemantics].
     */
    BATCH,

    /** Targets execute in authored order; later writes intentionally win. */
    ORDERED,
}

/** What happens when part of a multi-target execution fails. */
@Serializable
enum class FailurePolicy {
    FAIL_FAST,
    CONTINUE_ON_ERROR,
    ROLLBACK_WHEN_SUPPORTED,
}

/**
 * Explicit, declared semantics of a canonical node. Absent optional fields
 * mean "not declared" — never a hidden runtime default; whether a declaration
 * is required is decided by [validateSelectionSemantics].
 */
@Serializable
data class NodeSelectionSemantics(
    val targetSelectionMode: TargetSelectionMode,
    val executionMode: ExecutionMode,
    val eventLogic: EventLogic? = null,
    val conditionLogic: ConditionLogic? = null,
    val failurePolicy: FailurePolicy? = null,
)

/**
 * How many targets an operation accepts. Cardinality is per operation, not per
 * family (ADR-003): e.g. open-app is single-target while force-stop is
 * multi-target.
 */
@Serializable
data class OperationCardinality(
    val minTargets: Int = 1,
    val maxTargets: Int? = null,
) {
    init {
        require(minTargets >= 1) { "minTargets must be >= 1" }
        require(maxTargets == null || maxTargets >= minTargets) {
            "maxTargets must be >= minTargets or unbounded"
        }
    }

    companion object {
        /** Operation accepts exactly one target (e.g. open app settings). */
        val SINGLE_TARGET = OperationCardinality(minTargets = 1, maxTargets = 1)

        /** Operation accepts any number of targets (e.g. force stop apps). */
        val UNBOUNDED = OperationCardinality()
    }
}

/** Deterministic, typed violations detected by [validateSelectionSemantics]. */
sealed interface SelectionSemanticsError {
    val message: String
}

data class MultiTargetWithoutExecutionSemantics(
    override val message: String =
        "MULTI target selection requires an explicit BATCH or ORDERED execution mode",
) : SelectionSemanticsError

data class ExecutionModeRequiresMultiSelection(
    val executionMode: ExecutionMode,
    override val message: String =
        "execution mode $executionMode requires MULTI target selection",
) : SelectionSemanticsError

data class MissingFailurePolicy(
    val executionMode: ExecutionMode,
    override val message: String =
        "multi-target $executionMode execution must declare an explicit failure policy",
) : SelectionSemanticsError

data class CardinalityViolation(
    val selectedTargets: Int,
    val cardinality: OperationCardinality,
    override val message: String =
        "selected target count $selectedTargets violates cardinality $cardinality",
) : SelectionSemanticsError

data class ContradictoryBatchWrite(
    val target: TargetId,
    override val message: String =
        "batch contains contradictory writes to target $target",
) : SelectionSemanticsError

data class UnverifiableBatchWrite(
    val target: TargetId,
    override val message: String =
        "batch consistency for target $target cannot be proven because a write uses an expression",
) : SelectionSemanticsError

/**
 * Pure, deterministic validation of declared semantics. Returns all violations
 * in a stable order; an empty list means the semantics are executable as
 * declared. Planned writes are only inspected for [ExecutionMode.BATCH].
 */
fun validateSelectionSemantics(
    semantics: NodeSelectionSemantics,
    selectedTargetCount: Int,
    cardinality: OperationCardinality? = null,
    plannedWrites: List<CanonicalActionNode> = emptyList(),
): List<SelectionSemanticsError> {
    val errors = mutableListOf<SelectionSemanticsError>()

    if (cardinality != null) {
        val aboveMax = cardinality.maxTargets != null && selectedTargetCount > cardinality.maxTargets
        if (selectedTargetCount < cardinality.minTargets || aboveMax) {
            errors += CardinalityViolation(selectedTargetCount, cardinality)
        }
    }

    when {
        semantics.targetSelectionMode == TargetSelectionMode.MULTI &&
            semantics.executionMode == ExecutionMode.SINGLE ->
            errors += MultiTargetWithoutExecutionSemantics()

        semantics.targetSelectionMode == TargetSelectionMode.SINGLE &&
            semantics.executionMode != ExecutionMode.SINGLE ->
            errors += ExecutionModeRequiresMultiSelection(semantics.executionMode)

        else -> Unit
    }

    if (
        semantics.targetSelectionMode == TargetSelectionMode.MULTI &&
        semantics.executionMode != ExecutionMode.SINGLE &&
        semantics.failurePolicy == null
    ) {
        errors += MissingFailurePolicy(semantics.executionMode)
    }

    if (semantics.executionMode == ExecutionMode.BATCH) {
        errors += batchWriteConflicts(plannedWrites)
    }

    return errors
}

/**
 * Fail-closed variant used by compiler/planner entry points: the first
 * violation (in the deterministic order of [validateSelectionSemantics])
 * aborts with an [IllegalArgumentException].
 */
fun requireValidSelectionSemantics(
    semantics: NodeSelectionSemantics,
    selectedTargetCount: Int,
    cardinality: OperationCardinality? = null,
    plannedWrites: List<CanonicalActionNode> = emptyList(),
) {
    val errors = validateSelectionSemantics(semantics, selectedTargetCount, cardinality, plannedWrites)
    require(errors.isEmpty()) { errors.first().message }
}

private fun batchWriteConflicts(
    plannedWrites: List<CanonicalActionNode>,
): List<SelectionSemanticsError> {
    val writesByTarget = plannedWrites
        .mapNotNull { node -> typedWriteValue(node)?.let { node.target to it } }
        .groupBy({ it.first }, { it.second })

    val errors = mutableListOf<SelectionSemanticsError>()
    for ((target, values) in writesByTarget) {
        // A single write can never contradict itself; only multiple writes to
        // the same target need proof, and an expression cannot provide it at
        // planning time (fail closed per the canonical fail-closed rule).
        if (values.size >= 2 && values.any { it is ExpressionValue }) {
            errors += UnverifiableBatchWrite(target)
            continue
        }
        if (values.distinct().size > 1) {
            errors += ContradictoryBatchWrite(target)
        }
    }
    return errors
}

private fun typedWriteValue(node: CanonicalActionNode): CanonicalValue? = when (node) {
    is SetStateNode -> node.state
    is SetValueNode -> node.value
    else -> null
}
