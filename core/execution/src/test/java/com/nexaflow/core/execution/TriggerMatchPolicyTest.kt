package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The complete ANY/ALL truth table required by the feature spec, plus the
 * empty-condition guard, the event-only advisory, and skip-message
 * classification. Pure JVM: no Android plumbing.
 */
class TriggerMatchPolicyTest {

    private val sat = ConditionResult.Satisfied
    private val unsat = ConditionResult.Unsatisfied
    private val unknown = ConditionResult.Unknown

    // ---- ANY truth table ----

    @Test
    fun any_falseFalse_isFalse() {
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(unsat, unsat)))
    }

    @Test
    fun any_trueFalse_isTrue() {
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(sat, unsat)))
    }

    @Test
    fun any_falseTrue_isTrue() {
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(unsat, sat)))
    }

    @Test
    fun any_trueTrue_isTrue() {
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(sat, sat)))
    }

    // ---- ALL truth table ----

    @Test
    fun all_falseFalse_isFalse() {
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(unsat, unsat)))
    }

    @Test
    fun all_trueFalse_isFalse() {
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(sat, unsat)))
    }

    @Test
    fun all_falseTrue_isFalse() {
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(unsat, sat)))
    }

    @Test
    fun all_trueTrue_isTrue() {
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(sat, sat)))
    }

    // ---- Beyond two conditions ----

    @Test
    fun all_scalesToFourConditions() {
        val results = listOf(sat, sat, sat, sat)
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, results))
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, results.take(3) + unsat))
    }

    @Test
    fun any_isTrueWhenAnyOfFourIsSatisfied() {
        assertTrue(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(unsat, unknown, unsat, sat)))
    }

    // ---- Unknown never fabricates success ----

    @Test
    fun all_unknownCondition_isNotSatisfied() {
        // An unverifiable condition (event-only source, unreadable service,
        // API failure) must not let ALL pass — never a fabricated success.
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(sat, unknown)))
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(unknown, unknown)))
        assertFalse(
            TriggerMatchPolicy.combine(TriggerMatchMode.ALL, listOf(sat, ConditionResult.Error("x")))
        )
    }

    @Test
    fun any_unknownOnly_isNotSatisfied() {
        // ANY requires at least one *satisfied* condition; Unknown alone is not one.
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, listOf(unknown)))
    }

    // ---- Empty condition list ----

    @Test
    fun emptyList_neverEvaluatesTrue_underAnyMode() {
        // Guards against `all(empty) == true` starting a task with no conditions.
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ALL, emptyList()))
        assertFalse(TriggerMatchPolicy.combine(TriggerMatchMode.ANY, emptyList()))
    }

    // ---- Event-only advisory ----

    @Test
    fun eventOnlyTriggerIsFlagged_forAllMode() {
        // SMS is a pure momentary event with no readable post-state.
        val sms = Trigger(TriggerType.SMS, mapOf("contains" to "alert"))
        val charger = Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED"))
        assertTrue(TriggerMatchPolicy.isEventOnly(sms))
        assertFalse(TriggerMatchPolicy.isEventOnly(charger))
        val advisory = TriggerMatchPolicy.allModeAdvisory(listOf(charger, sms))
        assertNotNull(advisory)
        assertTrue(advisory!!.contains("SMS"))
    }

    @Test
    fun allStateTriggersProduceNoAdvisory() {
        val charger = Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED"))
        val time = Trigger(
            TriggerType.TIME,
            mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "07:00")
        )
        assertNull(TriggerMatchPolicy.allModeAdvisory(listOf(charger, time)))
    }

    // ---- Skip message ----

    @Test
    fun skipMessage_listsConfirmedFalseLabels() {
        val charger = Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED"))
        val message = TriggerMatchPolicy.skipMessage(
            listOf(charger, charger.copy(type = TriggerType.TIME)),
            listOf(sat, unsat)
        )
        assertTrue(message.startsWith("Skipped: not all trigger conditions are true"))
        assertTrue(message.contains("TIME"))
        assertFalse(message.contains("unverifiable"))
    }

    @Test
    fun skipMessage_reportsUnverifiableWhenNothingConfirmedFalse() {
        val sms = Trigger(TriggerType.SMS, emptyMap())
        val message = TriggerMatchPolicy.skipMessage(listOf(sms), listOf(unknown))
        assertTrue(message.contains("condition state unverifiable"))
    }

    @Test
    fun oneShotTimeIsEventOnlyButTimeRangeIsStateReadable() {
        val instant = Trigger(
            TriggerType.TIME,
            mapOf("timeMode" to "AT", "time" to "22:00")
        )
        val range = Trigger(
            TriggerType.TIME,
            mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "07:00")
        )

        assertTrue(TriggerMatchPolicy.isEventOnly(instant))
        assertFalse(TriggerMatchPolicy.isEventOnly(range))
    }

}
