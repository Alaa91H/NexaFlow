package com.nexaflow.core.engine

import android.location.Location
import com.nexaflow.domain.models.Trigger

/** Provider-independent fixed-location geofence logic. */
object FixedLocationEvaluator {
    const val MIN_RADIUS_METERS = 1.0
    const val MAX_RADIUS_METERS = 100_000.0

    enum class State { UNKNOWN, OUTSIDE, INSIDE }
    enum class Event { ENTER, EXIT }

    data class Configuration(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val event: Event,
        val locationName: String? = null
    )

    fun parse(trigger: Trigger): Configuration? {
        if (trigger.type.name != "FIXED_LOCATION") return null
        val c = trigger.config
        val latitude = c["latitude"]?.toDoubleOrNull() ?: c["lat"]?.toDoubleOrNull()
        val longitude = c["longitude"]?.toDoubleOrNull() ?: c["lng"]?.toDoubleOrNull()
        val radius = c["radiusMeters"]?.toDoubleOrNull() ?: c["radius"]?.toDoubleOrNull()
        val event = (c["eventType"] ?: c["event"] ?: return null).uppercase()
        val parsedEvent = runCatching { Event.valueOf(event) }.getOrNull()
        return if (latitude != null && longitude != null && radius != null && parsedEvent != null) {
            Configuration(latitude, longitude, radius, parsedEvent, c["locationName"])
                .takeIf { validate(it).isEmpty() }
        } else null
    }

    fun validate(configuration: Configuration): List<String> = buildList {
        if (!configuration.latitude.isFinite() || configuration.latitude !in -90.0..90.0) add("latitude")
        if (!configuration.longitude.isFinite() || configuration.longitude !in -180.0..180.0) add("longitude")
        if (!configuration.radiusMeters.isFinite() || configuration.radiusMeters !in MIN_RADIUS_METERS..MAX_RADIUS_METERS) add("radiusMeters")
    }

    fun distanceMeters(latitude: Double, longitude: Double, device: Location): Double {
        val result = FloatArray(1)
        Location.distanceBetween(latitude, longitude, device.latitude, device.longitude, result)
        return result[0].toDouble()
    }

    fun isInside(configuration: Configuration, device: Location): Boolean =
        distanceMeters(configuration.latitude, configuration.longitude, device) <= configuration.radiusMeters

    fun nextState(configuration: Configuration, device: Location): State =
        if (isInside(configuration, device)) State.INSIDE else State.OUTSIDE

    fun event(previous: State, next: State, configured: Event): Event? = when {
        configured == Event.ENTER && previous == State.OUTSIDE && next == State.INSIDE -> Event.ENTER
        configured == Event.EXIT && previous == State.INSIDE && next == State.OUTSIDE -> Event.EXIT
        else -> null
    }
}
