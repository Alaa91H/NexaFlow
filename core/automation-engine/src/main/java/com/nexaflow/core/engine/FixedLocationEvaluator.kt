package com.nexaflow.core.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Provider-independent fixed-location trigger primitives. */
object FixedLocationEvaluator {
    const val DEFAULT_RADIUS_METERS = 100.0
    const val MIN_RADIUS_METERS = 1.0
    const val MAX_RADIUS_METERS = 100_000.0

    enum class TransitionState { UNKNOWN, OUTSIDE, INSIDE }
    enum class EventType { ENTER, EXIT }

    data class Coordinates(val latitude: Double, val longitude: Double)

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun isValidRadius(radiusMeters: Double): Boolean =
        radiusMeters.isFinite() && radiusMeters in MIN_RADIUS_METERS..MAX_RADIUS_METERS

    fun distanceMeters(from: Coordinates, to: Coordinates): Double {
        require(isValidCoordinate(from.latitude, from.longitude))
        require(isValidCoordinate(to.latitude, to.longitude))
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun isInside(device: Coordinates, fixed: Coordinates, radiusMeters: Double): Boolean {
        require(isValidRadius(radiusMeters))
        return distanceMeters(device, fixed) <= radiusMeters
    }

    /** Returns an event only for a real OUTSIDE↔INSIDE transition. */
    fun transition(
        previous: TransitionState,
        currentInside: Boolean,
        eventType: EventType
    ): EventType? {
        val current = if (currentInside) TransitionState.INSIDE else TransitionState.OUTSIDE
        return when {
            previous == TransitionState.OUTSIDE && current == TransitionState.INSIDE && eventType == EventType.ENTER -> EventType.ENTER
            previous == TransitionState.INSIDE && current == TransitionState.OUTSIDE && eventType == EventType.EXIT -> EventType.EXIT
            else -> null
        }
    }
}
