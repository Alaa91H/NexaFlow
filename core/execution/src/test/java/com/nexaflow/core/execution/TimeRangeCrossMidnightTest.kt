package com.nexaflow.core.execution

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the cross-midnight time-range semantics the ALL trigger-match mode
 * relies on: 22:00 -> 07:00 must be true at 22:30, 01:00, 06:59 and false at
 * 12:00, 18:00, 07:01 (plus exact-boundary behavior and the charging+night
 * scenario decision table).
 */
class TimeRangeCrossMidnightTest {

    private val nightRange = mapOf(
        "timeMode" to "RANGE",
        "rangeStart" to "22:00",
        "rangeEnd" to "07:00"
    )

    private fun satisfiedAt(hour: Int, minute: Int): Boolean =
        TriggerStateEvaluator.timeTriggerSatisfied(
            config = nightRange,
            now = LocalTime.of(hour, minute),
            today = LocalDate.of(2026, 9, 21)
        )

    @Test
    fun overnightRange_trueInsideTheEveningSide() {
        assertTrue(satisfiedAt(22, 0))
        assertTrue(satisfiedAt(22, 30))
        assertTrue(satisfiedAt(23, 59))
    }

    @Test
    fun overnightRange_trueInsideTheMorningSide() {
        assertTrue(satisfiedAt(0, 0))
        assertTrue(satisfiedAt(1, 0))
        assertTrue(satisfiedAt(6, 59))
    }

    @Test
    fun overnightRange_falseOutsideTheWindow() {
        assertFalse(satisfiedAt(12, 0))
        assertFalse(satisfiedAt(18, 0))
        assertFalse(satisfiedAt(7, 1))
        assertFalse(satisfiedAt(21, 59))
    }

    @Test
    fun boundaryIsInclusiveAtStartAndExclusiveAtEnd() {
        // start inclusive: 22:00:00 belongs to the window...
        assertTrue(satisfiedAt(22, 0))
        // ...and 07:00:00 does not (window is [start, end)).
        assertFalse(satisfiedAt(7, 0))
    }

    @Test
    fun normalDayRangeStillWorks() {
        val day = nightRange + ("rangeStart" to "09:00") + ("rangeEnd" to "17:00")
        assertTrue(
            TriggerStateEvaluator.timeTriggerSatisfied(
                config = day, now = LocalTime.of(12, 0), today = LocalDate.of(2026, 9, 21)
            )
        )
        assertFalse(
            TriggerStateEvaluator.timeTriggerSatisfied(
                config = day, now = LocalTime.of(18, 0), today = LocalDate.of(2026, 9, 21)
            )
        )
    }

    /**
     * The feature scenario decision table: DND enabled only when the device
     * is charging AND the clock sits inside 22:00-07:00.
     */
    @Test
    fun chargingAndNightScenarioMatrix() {
        fun dndShouldEnable(charging: Boolean, hour: Int, minute: Int): Boolean =
            charging && satisfiedAt(hour, minute)

        // 14:00 + charging -> do NOT enable
        assertFalse(dndShouldEnable(charging = true, hour = 14, minute = 0))
        // 23:00 + not charging -> do NOT enable
        assertFalse(dndShouldEnable(charging = false, hour = 23, minute = 0))
        // 23:00 + charging -> enable
        assertTrue(dndShouldEnable(charging = true, hour = 23, minute = 0))
        // 01:00 (after midnight) + charging -> enable
        assertTrue(dndShouldEnable(charging = true, hour = 1, minute = 0))
        // 06:59 + charging -> enable; 07:01 + charging -> do NOT
        assertTrue(dndShouldEnable(charging = true, hour = 6, minute = 59))
        assertFalse(dndShouldEnable(charging = true, hour = 7, minute = 1))
    }
}
