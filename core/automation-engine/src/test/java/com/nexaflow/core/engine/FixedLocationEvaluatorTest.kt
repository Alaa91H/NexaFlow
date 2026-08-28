package com.nexaflow.core.engine

import com.nexaflow.core.engine.FixedLocationEvaluator.Coordinates
import com.nexaflow.core.engine.FixedLocationEvaluator.EventType
import com.nexaflow.core.engine.FixedLocationEvaluator.TransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedLocationEvaluatorTest {
    @Test fun validCoordinatesAreAccepted() {
        assertTrue(FixedLocationEvaluator.isValidCoordinate(52.5208, 13.4095))
        assertTrue(FixedLocationEvaluator.isValidCoordinate(-90.0, 180.0))
    }

    @Test fun invalidCoordinatesNanInfinityAndOutOfRangeAreRejected() {
        assertFalse(FixedLocationEvaluator.isValidCoordinate(90.001, 0.0))
        assertFalse(FixedLocationEvaluator.isValidCoordinate(0.0, -180.001))
        assertFalse(FixedLocationEvaluator.isValidCoordinate(Double.NaN, 0.0))
        assertFalse(FixedLocationEvaluator.isValidCoordinate(0.0, Double.POSITIVE_INFINITY))
    }

    @Test fun radiusValidationAcceptsPositiveValuesWithinMaximum() {
        assertTrue(FixedLocationEvaluator.isValidRadius(1.0))
        assertTrue(FixedLocationEvaluator.isValidRadius(FixedLocationEvaluator.MAX_RADIUS_METERS))
        assertFalse(FixedLocationEvaluator.isValidRadius(0.0))
        assertFalse(FixedLocationEvaluator.isValidRadius(-1.0))
        assertFalse(FixedLocationEvaluator.isValidRadius(Double.NaN))
        assertFalse(FixedLocationEvaluator.isValidRadius(Double.POSITIVE_INFINITY))
        assertFalse(FixedLocationEvaluator.isValidRadius(FixedLocationEvaluator.MAX_RADIUS_METERS + 1))
    }

    @Test fun distanceAndBoundaryUseRadiusInclusiveComparison() {
        val fixed = Coordinates(52.5208, 13.4095)
        val same = Coordinates(52.5208, 13.4095)
        val nearby = Coordinates(52.5217, 13.4095)
        assertEquals(0.0, FixedLocationEvaluator.distanceMeters(fixed, same), 0.01)
        assertTrue(FixedLocationEvaluator.isInside(same, fixed, 50.0))
        assertFalse(FixedLocationEvaluator.isInside(nearby, fixed, 50.0))
    }

    @Test fun enterAndExitOnlyFireOnRealTransitions() {
        assertEquals(EventType.ENTER, FixedLocationEvaluator.transition(TransitionState.OUTSIDE, true, EventType.ENTER))
        assertEquals(EventType.EXIT, FixedLocationEvaluator.transition(TransitionState.INSIDE, false, EventType.EXIT))
        assertNull(FixedLocationEvaluator.transition(TransitionState.INSIDE, true, EventType.ENTER))
        assertNull(FixedLocationEvaluator.transition(TransitionState.OUTSIDE, false, EventType.EXIT))
    }

    @Test fun unknownStateDoesNotEmitEventAfterRestart() {
        assertNull(FixedLocationEvaluator.transition(TransitionState.UNKNOWN, true, EventType.ENTER))
        assertNull(FixedLocationEvaluator.transition(TransitionState.UNKNOWN, false, EventType.EXIT))
    }
}
