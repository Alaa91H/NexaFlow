package com.nexaflow.domain.constraints

import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure gate evaluation for [Constraint]s against a [ConstraintSnapshot].
 * No Android dependencies — fully unit-testable. Semantics are AND: a task
 * runs only when every constraint is satisfied.
 */
object ConstraintEvaluator {

    /** True when a single constraint is satisfied by the given device state. */
    fun isSatisfied(
        constraint: Constraint,
        state: ConstraintSnapshot,
        nowTime: java.time.LocalTime = java.time.LocalTime.now(),
        today: java.time.LocalDate = java.time.LocalDate.now()
    ): Boolean = when (constraint.type) {
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
        ConstraintType.BLUETOOTH -> {
            val wantOn = (constraint.config["state"] ?: "ON") == "ON"
            if (wantOn) state.bluetoothEnabled else !state.bluetoothEnabled
        }
        ConstraintType.DND -> {
            val wantOn = (constraint.config["state"] ?: "ON") == "ON"
            if (wantOn) state.dndActive else !state.dndActive
        }
        ConstraintType.AIRPLANE -> {
            val wantOn = (constraint.config["state"] ?: "ON") == "ON"
            if (wantOn) state.airplaneModeOn else !state.airplaneModeOn
        }
        ConstraintType.CHARGING -> {
            val wantCharging = (constraint.config["state"] ?: "CHARGING") == "CHARGING"
            if (wantCharging) state.isCharging else !state.isCharging
        }
        ConstraintType.LOCATION -> {
            val wantOn = (constraint.config["state"] ?: "ON") == "ON"
            if (wantOn) state.locationEnabled else !state.locationEnabled
        }
        // External conditions are evaluated by AutomationConstraintGate through
        // CapabilityExecutionService. A legacy direct evaluator has no typed
        // adapter or availability information, so it safely rejects rather than
        // coercing an unknown external state to true.
        ConstraintType.PLUGIN -> false
        ConstraintType.SCHEDULE -> scheduleSatisfied(constraint.config, nowTime, today)
    }

    /** True when every constraint passes (empty constraint list → true). */
    fun allSatisfied(constraints: List<Constraint>, state: ConstraintSnapshot): Boolean =
        constraints.all { isSatisfied(it, state) }

    /**
     * SCHEDULE gate: the current day/time must fall inside the configured
     * window. `days` is a comma-separated list of ISO day numbers (1=Mon..
     * 7=Sun); empty or absent means every day. `start`/`end` are HH:mm; a
     * window whose end is not after its start spans midnight (e.g. 22:00 to
     * 06:00 is active overnight). Unparseable bounds fail closed so a corrupt
     * config can never widen the gate.
     */
    fun scheduleSatisfied(
        config: Map<String, String>,
        nowTime: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val days = config["days"].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
        if (days.isNotEmpty() && today.dayOfWeek.value !in days) return false
        val start = runCatching { LocalTime.parse(config["start"]) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(config["end"]) }.getOrNull() ?: return false
        return if (end.isAfter(start)) {
            !nowTime.isBefore(start) && nowTime.isBefore(end)
        } else {
            // Overnight window (e.g. 22:00 -> 06:00).
            !nowTime.isBefore(start) || nowTime.isBefore(end)
        }
    }
}
