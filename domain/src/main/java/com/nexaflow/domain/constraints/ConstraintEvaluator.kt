package com.nexaflow.domain.constraints

import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType

/**
 * Pure gate evaluation for [Constraint]s against a [ConstraintSnapshot].
 * No Android dependencies — fully unit-testable. Semantics are AND: a task
 * runs only when every constraint is satisfied.
 */
object ConstraintEvaluator {

    /** True when a single constraint is satisfied by the given device state. */
    fun isSatisfied(constraint: Constraint, state: ConstraintSnapshot): Boolean = when (constraint.type) {
        ConstraintType.WIFI -> state.wifiConnected
        ConstraintType.SCREEN_LOCKED -> state.screenLocked
        ConstraintType.HEADSET -> state.headsetConnected
        ConstraintType.BATTERY -> {
            // Fail closed when the level could not be read: never run against
            // an unverifiable battery state.
            if (state.batteryLevel < 0) return false
            val level = constraint.config["level"]?.toIntOrNull() ?: 20
            val direction = constraint.config["direction"] ?: "BELOW"
            // Boundaries are inclusive in both directions: an exact match
            // satisfies ABOVE (>=) and BELOW (<=) alike. Documented so a user
            // stacking two battery constraints at the same level isn't surprised.
            if (direction == "ABOVE") state.batteryLevel >= level else state.batteryLevel <= level
        }
    }

    /** True when every constraint passes (empty constraint list → true). */
    fun allSatisfied(constraints: List<Constraint>, state: ConstraintSnapshot): Boolean =
        constraints.all { isSatisfied(it, state) }
}
