package com.nexaflow.core.engine

import android.location.Location
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.*
import org.junit.Test

class FixedLocationEvaluatorTest {
    private fun location(lat: Double, lng: Double): Location = Location("test").apply {
        latitude = lat
        longitude = lng
    }

    @Test fun validatesCoordinatesAndRadius() {
        val valid = FixedLocationEvaluator.Configuration(52.5208, 13.4095, 150.0, FixedLocationEvaluator.Event.ENTER)
        assertTrue(FixedLocationEvaluator.validate(valid).isEmpty())
        assertFalse(FixedLocationEvaluator.validate(valid.copy(latitude = Double.NaN)).isEmpty())
        assertFalse(FixedLocationEvaluator.validate(valid.copy(longitude = Double.POSITIVE_INFINITY)).isEmpty())
        assertFalse(FixedLocationEvaluator.validate(valid.copy(radiusMeters = 0.0)).isEmpty())
        assertFalse(FixedLocationEvaluator.validate(valid.copy(radiusMeters = -1.0)).isEmpty())
        assertFalse(FixedLocationEvaluator.validate(valid.copy(radiusMeters = 100_001.0)).isEmpty())
    }

    @Test fun evaluatesDistanceAndTransitions() {
        val config = FixedLocationEvaluator.Configuration(52.5208, 13.4095, 150.0, FixedLocationEvaluator.Event.ENTER)
        val inside = location(52.5208, 13.4095)
        val outside = location(52.5300, 13.4095)
        assertTrue(FixedLocationEvaluator.isInside(config, inside))
        assertFalse(FixedLocationEvaluator.isInside(config, outside))
        assertEquals(FixedLocationEvaluator.Event.ENTER,
            FixedLocationEvaluator.event(FixedLocationEvaluator.State.OUTSIDE, FixedLocationEvaluator.State.INSIDE, FixedLocationEvaluator.Event.ENTER))
        assertEquals(FixedLocationEvaluator.Event.EXIT,
            FixedLocationEvaluator.event(FixedLocationEvaluator.State.INSIDE, FixedLocationEvaluator.State.OUTSIDE, FixedLocationEvaluator.Event.EXIT))
        assertNull(FixedLocationEvaluator.event(FixedLocationEvaluator.State.INSIDE, FixedLocationEvaluator.State.INSIDE, FixedLocationEvaluator.Event.ENTER))
        assertNull(FixedLocationEvaluator.event(FixedLocationEvaluator.State.OUTSIDE, FixedLocationEvaluator.State.OUTSIDE, FixedLocationEvaluator.Event.EXIT))
    }

    @Test fun parsesProviderIndependentConfig() {
        val trigger = Trigger(TriggerType.FIXED_LOCATION, mapOf(
            "latitude" to "52.5208", "longitude" to "13.4095",
            "radiusMeters" to "150", "eventType" to "ENTER", "locationName" to "Work"
        ))
        assertEquals("Work", FixedLocationEvaluator.parse(trigger)?.locationName)
    }
}
