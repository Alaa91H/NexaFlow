package com.nexaflow.domain.models

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NumericSensorsTest {
    @Test fun physicalSensorTypesAreDistinctAndMatchAndroidIdentifiers() {
        assertEquals(listOf(6, 13, 12, 2, 10, 4, 9, 36), NumericSensors.specs.values.map { it.type })
    }

    @Test fun switchingFromProximityMakesEveryNumericModeRunnable() {
        val previous = mapOf("sensor" to "PROXIMITY", "event" to "COVERED", "upperThreshold" to "5", "custom" to "keep")
        NumericSensors.specs.keys.forEach { kind ->
            val config = NumericSensors.configurationFor(kind, previous)
            assertEquals("ABOVE", config["event"])
            assertEquals("keep", config["custom"])
            assertFalse(config.containsKey("upperThreshold"))
            assertTrue(NumericSensors.matches(config, config.getValue("threshold").toFloat() + 1f))
        }
    }

    @Test fun switchingPhysicalQuantitiesResetsThresholdsButReSelectingPreservesEdits() {
        val pressure = mapOf("sensor" to "PRESSURE", "event" to "BETWEEN", "threshold" to "900", "upperThreshold" to "1100")
        assertEquals(pressure, NumericSensors.configurationFor("PRESSURE", pressure))
        val temperature = NumericSensors.configurationFor("TEMPERATURE", pressure)
        assertEquals("25", temperature["threshold"])
        assertEquals("ABOVE", temperature["event"])
        assertFalse(temperature.containsKey("upperThreshold"))
        assertEquals("COVERED", NumericSensors.configurationFor("PROXIMITY", temperature)["event"])
    }

    @Test fun calibrationIsAppliedWithinUnitSpecificBoundsAndClearedOnSensorChange() {
        val calibrated = mapOf("sensor" to "TEMPERATURE", "threshold" to "25", "calibrationOffset" to "2.5")
        assertEquals(27.5f, NumericSensors.calibratedValue("TEMPERATURE", calibrated, 25f)!!, 0f)
        assertTrue(NumericSensors.matches(calibrated, 27f))
        assertTrue(NumericSensors.matches(calibrated, 28f))
        assertFalse(NumericSensors.matches(calibrated + ("calibrationOffset" to "51"), 25f))
        assertFalse(NumericSensors.matches(calibrated + ("calibrationOffset" to "NaN"), 25f))
        assertFalse(NumericSensors.matches(calibrated + ("sensor" to "UNKNOWN"), 25f))
        assertFalse(NumericSensors.configurationFor("PRESSURE", calibrated).containsKey("calibrationOffset"))
        assertEquals(25f, NumericSensors.calibratedValue("TEMPERATURE", mapOf("sensor" to "TEMPERATURE"), 25f)!!, 0f)
    }

    @Test fun samplingPeriodUsesOnlySupportedPowerAndResponsivenessChoices() {
        assertEquals(200_000, NumericSensors.samplePeriodUs(emptyMap()))
        NumericSensors.samplePeriodsUs.forEach { period ->
            assertEquals(period, NumericSensors.samplePeriodUs(mapOf("samplePeriodUs" to period.toString())))
        }
        assertEquals(200_000, NumericSensors.samplePeriodUs(mapOf("samplePeriodUs" to "1000")))
        assertEquals(200_000, NumericSensors.samplePeriodUs(mapOf("samplePeriodUs" to "invalid")))
    }

    @Test fun calibrationAdjustsThresholdsAndRangeBounds() {
        val calibrated = mapOf(
            "sensor" to "TEMPERATURE", "event" to "BETWEEN", "threshold" to "20",
            "upperThreshold" to "22", "calibrationOffset" to "2"
        )
        assertTrue(NumericSensors.matches(calibrated, 18f))
        assertTrue(NumericSensors.matches(calibrated, 20f))
        assertFalse(NumericSensors.matches(calibrated, 17f))
        assertFalse(NumericSensors.matches(calibrated, 21f))
    }

    @Test fun rangeBoundariesAreInclusiveWhileAboveAndBelowAreStrict() {
        val config = mapOf("threshold" to "10", "upperThreshold" to "20")
        assertFalse(NumericSensors.matches(config + ("event" to "ABOVE"), 10f))
        assertFalse(NumericSensors.matches(config + ("event" to "BELOW"), 10f))
        assertTrue(NumericSensors.matches(config + ("event" to "AT_LEAST"), 10f))
        assertTrue(NumericSensors.matches(config + ("event" to "AT_MOST"), 10f))
        assertTrue(NumericSensors.matches(config + ("event" to "BETWEEN"), 10f))
        assertTrue(NumericSensors.matches(config + ("event" to "BETWEEN"), 20f))
        assertFalse(NumericSensors.matches(config + ("event" to "BETWEEN"), 21f))
    }

    @Test fun malformedOrNonFiniteThresholdsAndSamplesNeverMatch() {
        listOf("", "NaN", "Infinity", "invalid").forEach { threshold ->
            assertFalse(NumericSensors.matches(mapOf("threshold" to threshold), 10f))
        }
        assertFalse(NumericSensors.matches(mapOf("threshold" to "10"), Float.POSITIVE_INFINITY))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "10", "event" to "COVERED"), 100f))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "20", "event" to "BETWEEN", "upperThreshold" to "10"), 15f))
        assertFalse(NumericSensors.matches(mapOf("threshold" to "10", "event" to "BETWEEN", "upperThreshold" to "NaN"), 15f))
    }

    @Test fun vectorAndScalarExtractionRejectsInvalidHardwareSamples() {
        assertEquals(13f, NumericSensors.reading("MAGNETIC", floatArrayOf(3f, 4f, 12f))!!, 0.0001f)
        assertEquals(1013.25f, NumericSensors.reading("PRESSURE", floatArrayOf(1013.25f))!!, 0f)
        assertNull(NumericSensors.reading("GYROSCOPE", floatArrayOf(3f, 4f)))
        assertNull(NumericSensors.reading("GRAVITY", floatArrayOf(0f, Float.NaN, 1f)))
        assertNull(NumericSensors.reading("HINGE", floatArrayOf(Float.POSITIVE_INFINITY)))
        assertNull(NumericSensors.reading("UNKNOWN", floatArrayOf(1f)))
    }
}
