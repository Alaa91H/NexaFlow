package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Pure matching logic for the SENSOR trigger — no device sensors needed. */
@RunWith(JUnit4::class)
class SensorTriggerMatcherTest {

    // ---- proximity --------------------------------------------------------

    @Test
    fun proximity_coveredWhenClose() {
        val config = mapOf("sensor" to "PROXIMITY", "event" to "COVERED")
        // maxRange 5cm: covered under 2.5cm, uncovered above.
        assertTrue(SensorTriggerMatcher.matches(config, "PROXIMITY", 1f, 0f, 0f, 0, 5f))
        assertFalse(SensorTriggerMatcher.matches(config, "PROXIMITY", 4f, 0f, 0f, 0, 5f))
    }

    @Test
    fun proximity_uncoveredWhenFar() {
        val config = mapOf("sensor" to "PROXIMITY", "event" to "UNCOVERED")
        assertTrue(SensorTriggerMatcher.matches(config, "PROXIMITY", 4f, 0f, 0f, 0, 5f))
        assertFalse(SensorTriggerMatcher.matches(config, "PROXIMITY", 1f, 0f, 0f, 0, 5f))
    }

    @Test
    fun proximity_digitalSensorMaxRangeZero() {
        // Digital proximity sensors report maximumRange = 0: any reading < 1cm
        // counts as covered.
        val config = mapOf("sensor" to "PROXIMITY", "event" to "COVERED")
        assertTrue(SensorTriggerMatcher.matches(config, "PROXIMITY", 0f, 0f, 0f, 0, 0f))
        assertFalse(SensorTriggerMatcher.matches(config, "PROXIMITY", 8f, 0f, 0f, 0, 0f))
    }

    // ---- light ------------------------------------------------------------

    @Test
    fun light_aboveThreshold() {
        val config = mapOf("sensor" to "LIGHT", "event" to "ABOVE", "threshold" to "200")
        assertTrue(SensorTriggerMatcher.matches(config, "LIGHT", 0f, 400f, 0f, 0, 0f))
        assertFalse(SensorTriggerMatcher.matches(config, "LIGHT", 0f, 100f, 0f, 0, 0f))
    }

    @Test
    fun light_belowThreshold() {
        val config = mapOf("sensor" to "LIGHT", "event" to "BELOW", "threshold" to "50")
        assertTrue(SensorTriggerMatcher.matches(config, "LIGHT", 0f, 10f, 0f, 0, 0f))
        assertFalse(SensorTriggerMatcher.matches(config, "LIGHT", 0f, 300f, 0f, 0, 0f))
    }

    // ---- shake / step -----------------------------------------------------

    @Test
    fun shake_overSensitivity() {
        val config = mapOf("sensor" to "SHAKE", "sensitivity" to "14")
        assertTrue(SensorTriggerMatcher.matches(config, "SHAKE", 0f, 0f, 18f, 0, 0f))
        assertFalse(SensorTriggerMatcher.matches(config, "SHAKE", 0f, 0f, 3f, 0, 0f))
    }

    @Test
    fun step_positiveDeltaFires() {
        val config = mapOf("sensor" to "STEP")
        assertTrue(SensorTriggerMatcher.matches(config, "STEP", 0f, 0f, 0f, 1, 0f))
        assertFalse(SensorTriggerMatcher.matches(config, "STEP", 0f, 0f, 0f, 0, 0f))
    }

    // ---- statefulness (drives exit behavior) -------------------------------

    @Test
    fun statefulSensors_endWhenConditionReverses() {
        val proximity = mapOf("sensor" to "PROXIMITY", "event" to "COVERED")
        assertTrue(SensorTriggerMatcher.ended(proximity, "PROXIMITY", 4f, 0f, 0f, 0, 5f))
        assertFalse(SensorTriggerMatcher.ended(proximity, "PROXIMITY", 1f, 0f, 0f, 0, 5f))

        val light = mapOf("sensor" to "LIGHT", "event" to "ABOVE", "threshold" to "200")
        assertTrue(SensorTriggerMatcher.ended(light, "LIGHT", 0f, 50f, 0f, 0, 0f))
        assertFalse(SensorTriggerMatcher.ended(light, "LIGHT", 0f, 500f, 0f, 0, 0f))
    }

    @Test
    fun transientSensorsNeverEnd() {
        assertFalse(SensorTriggerMatcher.isStateful("SHAKE"))
        assertFalse(SensorTriggerMatcher.isStateful("STEP"))
        // ended() is a no-op for transient sensors even when not matching.
        assertFalse(
            SensorTriggerMatcher.ended(mapOf("sensor" to "STEP"), "STEP", 0f, 0f, 0f, 0, 0f)
        )
    }

    // ---- automation filtering ----------------------------------------------

    @Test
    fun automationsFor_filtersBySensorKind() {
        val automation = Automation(
            id = "a1",
            name = "Cover test",
            description = "",
            icon = "bolt",
            iconColor = 0,
            backgroundColor = 0,
            category = "",
            priority = 0,
            enabled = true,
            triggers = listOf(
                Trigger(TriggerType.SENSOR, mapOf("sensor" to "PROXIMITY")),
                Trigger(TriggerType.SENSOR, mapOf("sensor" to "LIGHT"))
            ),
            actions = emptyList(),
            createdAt = 0,
            updatedAt = 0
        )
        val disabled = automation.copy(enabled = false)
        val lightOnly = automation.copy(
            id = "a2",
            triggers = listOf(Trigger(TriggerType.SENSOR, mapOf("sensor" to "LIGHT")))
        )

        val proximity = SensorTriggerMatcher.automationsFor(
            listOf(automation, disabled, lightOnly), "PROXIMITY"
        )
        assertEquals(listOf("a1"), proximity.map { it.id })

        val light = SensorTriggerMatcher.automationsFor(
            listOf(automation, disabled, lightOnly), "LIGHT"
        )
        assertEquals(listOf("a1", "a2"), light.map { it.id })

        // Unknown sensor kind matches nothing.
        assertTrue(SensorTriggerMatcher.automationsFor(listOf(automation), "MAGNET").isEmpty())
    }

    @Test
    fun sensorOf_normalizesCase() {
        assertEquals("PROXIMITY", SensorTriggerMatcher.sensorOf(mapOf("sensor" to "proximity")))
        assertEquals("", SensorTriggerMatcher.sensorOf(emptyMap()))
    }
}
