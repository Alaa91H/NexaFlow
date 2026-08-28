package com.nexaflow.feature.builder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPickerFallbackTest {
    @Test
    fun validCoordinatesAreAccepted() {
        assertTrue(validCoordinate(52.5208, 13.4095))
        assertTrue(validCoordinate(-90.0, 180.0))
    }

    @Test
    fun malformedAndOutOfRangeCoordinatesAreRejected() {
        assertFalse(validCoordinate(null, 13.4095))
        assertFalse(validCoordinate(91.0, 13.4095))
        assertFalse(validCoordinate(52.5208, 181.0))
        assertFalse(validCoordinate(Double.NaN, 13.4095))
        assertFalse(validCoordinate(52.5208, Double.POSITIVE_INFINITY))
    }

    @Test
    fun radiusMustBeWithinTheSupportedRange() {
        assertTrue(validRadius(50))
        assertTrue(validRadius(2000))
        assertFalse(validRadius(0))
        assertFalse(validRadius(-10))
        assertFalse(validRadius(2001))
    }
}
