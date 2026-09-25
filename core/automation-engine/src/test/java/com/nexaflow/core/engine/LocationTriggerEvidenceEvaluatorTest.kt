package com.nexaflow.core.engine

import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTriggerEvidenceEvaluatorTest {

    private fun trigger(
        event: String = "ENTER",
        source: String = "current",
        latitude: Double = 52.5200,
        longitude: Double = 13.4050,
        radius: Double = 500.0,
    ) = Trigger(
        TriggerType.LOCATION,
        mapOf(
            "lat" to latitude.toString(),
            "lng" to longitude.toString(),
            "radius" to radius.toString(),
            "event" to event,
            "source" to source,
        ),
    )

    @Test
    fun currentLocationMayEmitEnterOnFirstKnownInsideFix() {
        val result = LocationTriggerEvidenceEvaluator.evaluate(
            trigger = trigger(event = "ENTER", source = "current"),
            deviceLatitude = 52.5200,
            deviceLongitude = 13.4050,
            previousInside = null,
        )

        assertNotNull(result)
        assertTrue(result!!.inside)
        assertTrue(result.eventMatched)
        assertTrue(result.targetStateActive)
    }

    @Test
    fun selectedLocationInitializesWithoutSyntheticEnter() {
        val result = LocationTriggerEvidenceEvaluator.evaluate(
            trigger = trigger(event = "ENTER", source = "selected"),
            deviceLatitude = 52.5200,
            deviceLongitude = 13.4050,
            previousInside = null,
        )

        assertNotNull(result)
        assertTrue(result!!.inside)
        assertFalse(result.eventMatched)
        assertTrue(result.targetStateActive)
    }

    @Test
    fun selectedEnterRequiresOutsideToInsideTransition() {
        val enter = trigger(event = "ENTER", source = "selected")

        val outside = LocationTriggerEvidenceEvaluator.evaluate(
            trigger = enter,
            deviceLatitude = 52.6000,
            deviceLongitude = 13.5000,
            previousInside = null,
        )!!
        val inside = LocationTriggerEvidenceEvaluator.evaluate(
            trigger = enter,
            deviceLatitude = 52.5200,
            deviceLongitude = 13.4050,
            previousInside = outside.inside,
        )!!

        assertFalse(outside.eventMatched)
        assertTrue(inside.eventMatched)
    }

    @Test
    fun exitTargetStateRemainsActiveWhileOutside() {
        val result = LocationTriggerEvidenceEvaluator.evaluate(
            trigger = trigger(event = "EXIT", source = "current"),
            deviceLatitude = 52.6000,
            deviceLongitude = 13.5000,
            previousInside = true,
        )!!

        assertFalse(result.inside)
        assertTrue(result.eventMatched)
        assertTrue(result.targetStateActive)
    }

    @Test
    fun malformedCoordinatesFailClosedInsteadOfProducingEvidence() {
        assertNull(
            LocationTriggerEvidenceEvaluator.evaluate(
                trigger = trigger(latitude = Double.NaN),
                deviceLatitude = 52.5200,
                deviceLongitude = 13.4050,
                previousInside = false,
            )
        )
    }
}
