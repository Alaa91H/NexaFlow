package com.nexaflow.core.engine

import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

/**
 * Pure location-trigger evaluation used by [LocationMonitor].
 *
 * One location fix produces both event evidence (did this trigger transition
 * now?) and the durable target-state truth used to decide when an accepted
 * location lifecycle has ended.
 */
internal object LocationTriggerEvidenceEvaluator {

    data class Result(
        val inside: Boolean,
        val eventMatched: Boolean,
        val targetStateActive: Boolean,
    )

    fun evaluate(
        trigger: Trigger,
        deviceLatitude: Double,
        deviceLongitude: Double,
        previousInside: Boolean?,
    ): Result? {
        if (trigger.type != TriggerType.LOCATION) return null

        val latitude = trigger.config["lat"]?.toDoubleOrNull() ?: return null
        val longitude = trigger.config["lng"]?.toDoubleOrNull() ?: return null
        val radius = trigger.config["radius"]?.toDoubleOrNull() ?: return null
        val event = trigger.config["event"] ?: "ENTER"
        val source = trigger.config["source"] ?: "current"

        if (
            !FixedLocationEvaluator.isValidCoordinate(latitude, longitude) ||
            !FixedLocationEvaluator.isValidCoordinate(deviceLatitude, deviceLongitude) ||
            !FixedLocationEvaluator.isValidRadius(radius) ||
            event !in setOf("ENTER", "EXIT")
        ) {
            return null
        }

        val inside = FixedLocationEvaluator.isInside(
            device = FixedLocationEvaluator.Coordinates(deviceLatitude, deviceLongitude),
            fixed = FixedLocationEvaluator.Coordinates(latitude, longitude),
            radiusMeters = radius,
        )

        val eventMatched = if (source == "selected") {
            val previous = previousInside?.let {
                if (it) FixedLocationEvaluator.TransitionState.INSIDE
                else FixedLocationEvaluator.TransitionState.OUTSIDE
            } ?: FixedLocationEvaluator.TransitionState.UNKNOWN
            val requested = if (event == "ENTER") {
                FixedLocationEvaluator.EventType.ENTER
            } else {
                FixedLocationEvaluator.EventType.EXIT
            }
            FixedLocationEvaluator.transition(previous, inside, requested) != null
        } else {
            // Preserve the established current-location behavior: the first
            // known fix may emit ENTER/EXIT when it already satisfies the
            // configured side.
            when (event) {
                "ENTER" -> inside && previousInside != true
                "EXIT" -> !inside && previousInside != false
                else -> false
            }
        }

        return Result(
            inside = inside,
            eventMatched = eventMatched,
            targetStateActive = if (event == "ENTER") inside else !inside,
        )
    }
}
