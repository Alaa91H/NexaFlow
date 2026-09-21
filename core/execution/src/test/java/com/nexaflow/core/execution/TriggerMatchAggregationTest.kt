package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.TriggerMatchMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TriggerMatchAggregationTest {

    @Test
    fun anyTruthTable() {
        assertEquals(ConditionResult.Unsatisfied, aggregate(TriggerMatchMode.ANY, false, false))
        assertEquals(ConditionResult.Satisfied, aggregate(TriggerMatchMode.ANY, true, false))
        assertEquals(ConditionResult.Satisfied, aggregate(TriggerMatchMode.ANY, false, true))
        assertEquals(ConditionResult.Satisfied, aggregate(TriggerMatchMode.ANY, true, true))
    }

    @Test
    fun allTruthTable() {
        assertEquals(ConditionResult.Unsatisfied, aggregate(TriggerMatchMode.ALL, false, false))
        assertEquals(ConditionResult.Unsatisfied, aggregate(TriggerMatchMode.ALL, true, false))
        assertEquals(ConditionResult.Unsatisfied, aggregate(TriggerMatchMode.ALL, false, true))
        assertEquals(ConditionResult.Satisfied, aggregate(TriggerMatchMode.ALL, true, true))
    }

    @Test
    fun supportsThreeOrMoreConditions() {
        assertEquals(
            ConditionResult.Satisfied,
            TriggerStateEvaluator.aggregate(
                listOf(
                    ConditionResult.Satisfied,
                    ConditionResult.Satisfied,
                    ConditionResult.Satisfied
                ),
                TriggerMatchMode.ALL
            )
        )
        assertEquals(
            ConditionResult.Satisfied,
            TriggerStateEvaluator.aggregate(
                listOf(
                    ConditionResult.Unsatisfied,
                    ConditionResult.Satisfied,
                    ConditionResult.Unsatisfied
                ),
                TriggerMatchMode.ANY
            )
        )
    }

    @Test
    fun unknownNeverCountsAsTrue() {
        assertEquals(
            ConditionResult.Unknown,
            TriggerStateEvaluator.aggregate(
                listOf(ConditionResult.Unsatisfied, ConditionResult.Unknown),
                TriggerMatchMode.ANY
            )
        )
        assertEquals(
            ConditionResult.Unknown,
            TriggerStateEvaluator.aggregate(
                listOf(ConditionResult.Satisfied, ConditionResult.Unknown),
                TriggerMatchMode.ALL
            )
        )
    }

    @Test
    fun chargingAndOvernightRangeTransitionsMatchAcceptanceScenario() {
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "22:00",
            "rangeEnd" to "07:00",
            "repeat" to "DAILY"
        )
        val date = LocalDate.of(2026, 9, 21)

        fun state(charging: Boolean, time: String): ConditionResult {
            val inRange = TriggerStateEvaluator.timeTriggerSatisfied(
                config = config,
                now = LocalTime.parse(time),
                today = date
            )
            return TriggerStateEvaluator.aggregate(
                listOf(charging.toResult(), inRange.toResult()),
                TriggerMatchMode.ALL
            )
        }

        assertEquals(ConditionResult.Unsatisfied, state(charging = true, time = "14:00"))
        assertEquals(ConditionResult.Unsatisfied, state(charging = false, time = "23:00"))
        assertEquals(ConditionResult.Satisfied, state(charging = true, time = "23:00"))

        // Starts charging before the night window, then 22:00 enters it.
        assertEquals(ConditionResult.Unsatisfied, state(charging = true, time = "21:59"))
        assertEquals(ConditionResult.Satisfied, state(charging = true, time = "22:00"))

        // Night is already active, then charging starts.
        assertEquals(ConditionResult.Unsatisfied, state(charging = false, time = "23:30"))
        assertEquals(ConditionResult.Satisfied, state(charging = true, time = "23:30"))
    }

    private fun aggregate(
        mode: TriggerMatchMode,
        first: Boolean,
        second: Boolean
    ): ConditionResult = TriggerStateEvaluator.aggregate(
        listOf(first.toResult(), second.toResult()),
        mode
    )

    private fun Boolean.toResult(): ConditionResult =
        if (this) ConditionResult.Satisfied else ConditionResult.Unsatisfied
}
