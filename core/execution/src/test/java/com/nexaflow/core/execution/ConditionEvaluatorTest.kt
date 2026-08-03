package com.nexaflow.core.execution

import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.ConditionType
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionEvaluatorTest {

    private fun evaluator(
        battery: Int = 100,
        now: LocalTime = LocalTime.NOON
    ): ConditionEvaluator = ConditionEvaluator(
        batteryLevelProvider = { battery },
        clock = { now }
    )

    private fun batteryCondition(above: String): Condition =
        Condition(ConditionType.BATTERY_PERCENTAGE, config = mapOf("above" to above))

    private fun timeCondition(start: String, end: String): Condition =
        Condition(ConditionType.TIME_RANGE, config = mapOf("start" to start, "end" to end))

    @Test
    fun emptyConditionsAreAlwaysMet() {
        assertTrue(evaluator().isMet(emptyList()))
    }

    @Test
    fun batteryAboveThresholdIsMet() {
        assertTrue(evaluator(battery = 80).isMet(listOf(batteryCondition("50"))))
    }

    @Test
    fun batteryBelowThresholdIsNotMet() {
        assertFalse(evaluator(battery = 30).isMet(listOf(batteryCondition("50"))))
    }

    @Test
    fun missingAboveValueDefaultsToTwenty() {
        assertFalse(evaluator(battery = 10).isMet(listOf(Condition(ConditionType.BATTERY_PERCENTAGE, config = emptyMap()))))
        assertTrue(evaluator(battery = 25).isMet(listOf(Condition(ConditionType.BATTERY_PERCENTAGE, config = emptyMap()))))
    }

    @Test
    fun timeRangeWithoutOvernightWrapIsMet() {
        assertTrue(evaluator(now = LocalTime.of(10, 0)).isMet(listOf(timeCondition("09:00", "11:00"))))
    }

    @Test
    fun timeRangeWithoutOvernightWrapIsNotMet() {
        assertFalse(evaluator(now = LocalTime.of(12, 0)).isMet(listOf(timeCondition("09:00", "11:00"))))
    }

    @Test
    fun overnightTimeRangeIsMetAfterMidnight() {
        assertTrue(evaluator(now = LocalTime.of(23, 30)).isMet(listOf(timeCondition("22:00", "06:00"))))
        assertTrue(evaluator(now = LocalTime.of(2, 0)).isMet(listOf(timeCondition("22:00", "06:00"))))
    }

    @Test
    fun overnightTimeRangeNotMetAtNoon() {
        assertFalse(evaluator(now = LocalTime.NOON).isMet(listOf(timeCondition("22:00", "06:00"))))
    }

    @Test
    fun blankTimeRangeConfigIsAlwaysMet() {
        assertTrue(evaluator().isMet(listOf(timeCondition("", ""))))
    }

    @Test
    fun malformedTimeRangeFallsBackToMet() {
        assertTrue(evaluator().isMet(listOf(timeCondition("not-a-time", "also-bad"))))
    }

    @Test
    fun allConditionsMustBeMet() {
        val conditions = listOf(batteryCondition("30"), timeCondition("08:00", "20:00"))
        assertTrue(evaluator(battery = 50, now = LocalTime.of(12, 0)).isMet(conditions))
        assertFalse(evaluator(battery = 20, now = LocalTime.of(12, 0)).isMet(conditions))
    }

    @Test
    fun orConditionSucceedsWhenAnyNestedIsMet() {
        val nested = listOf(batteryCondition("90"), batteryCondition("10"))
        val or = Condition(ConditionType.OR, config = emptyMap(), nestedConditions = nested)
        assertTrue(evaluator(battery = 15).isMet(listOf(or)))
    }

    @Test
    fun notConditionInvertsNested() {
        val not = Condition(
            ConditionType.NOT,
            config = emptyMap(),
            nestedConditions = listOf(batteryCondition("50"))
        )
        assertTrue(evaluator(battery = 20).isMet(listOf(not)))
        assertFalse(evaluator(battery = 80).isMet(listOf(not)))
    }
}
