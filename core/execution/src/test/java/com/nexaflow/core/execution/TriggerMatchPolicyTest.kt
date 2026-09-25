package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
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


    @Test
    fun aggregatePreservesTriStateSemantics() {
        assertEquals(
            ConditionResult.Satisfied,
            TriggerMatchPolicy.aggregate(
                TriggerMatchMode.ANY,
                listOf(ConditionResult.Unknown, ConditionResult.Satisfied),
            ),
        )
        assertEquals(
            ConditionResult.Unknown,
            TriggerMatchPolicy.aggregate(
                TriggerMatchMode.ANY,
                listOf(ConditionResult.Unsatisfied, ConditionResult.Unknown),
            ),
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            TriggerMatchPolicy.aggregate(
                TriggerMatchMode.ALL,
                listOf(ConditionResult.Satisfied, ConditionResult.Unsatisfied, ConditionResult.Unknown),
            ),
        )
        assertEquals(
            ConditionResult.Unknown,
            TriggerMatchPolicy.aggregate(
                TriggerMatchMode.ALL,
                listOf(ConditionResult.Satisfied, ConditionResult.Unavailable),
            ),
        )
    }

    @Test
    fun eventSemanticsDistinguishSingleEventFromSameOccurrenceRequirement() {
        val sms = Trigger(TriggerType.SMS, mapOf("contains" to "otp"))
        val dark = Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON"))
        val notification = Trigger(TriggerType.NOTIFICATION, mapOf("contains" to "otp"))

        assertEquals(
            TriggerMatchPolicy.AllModeEventSemantics.CURRENT_EVENT_WITH_LIVE_STATE,
            TriggerMatchPolicy.allModeEventSemantics(listOf(sms, dark)),
        )
        assertEquals(
            TriggerMatchPolicy.AllModeEventSemantics.SAME_OCCURRENCE_REQUIRED,
            TriggerMatchPolicy.allModeEventSemantics(listOf(sms, notification)),
        )
        assertEquals(
            TriggerMatchPolicy.AllModeEventSemantics.NONE,
            TriggerMatchPolicy.allModeEventSemantics(listOf(dark)),
        )
    }

    @Test
    fun multipleMomentaryConditionsExplainSameOccurrenceRequirement() {
        val smsOne = Trigger(TriggerType.SMS, mapOf("from" to "111"))
        val smsTwo = Trigger(TriggerType.SMS, mapOf("contains" to "otp"))

        val message = TriggerMatchPolicy.skipMessage(
            listOf(smsOne, smsTwo),
            listOf(ConditionResult.Satisfied, ConditionResult.Unknown),
        )

        assertTrue(message.contains("same current occurrence"))
    }


    @Test
    fun confirmedFalseTakesPrecedenceOverSameOccurrenceAdvisory() {
        val smsOne = Trigger(TriggerType.SMS, mapOf("from" to "111"))
        val smsTwo = Trigger(TriggerType.SMS, mapOf("contains" to "otp"))
        val dark = Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON"))

        val message = TriggerMatchPolicy.skipMessage(
            listOf(smsOne, smsTwo, dark),
            listOf(
                ConditionResult.Satisfied,
                ConditionResult.Unknown,
                ConditionResult.Unsatisfied,
            ),
        )

        assertTrue(message.contains("DARK_MODE"))
        assertFalse(message.contains("same current occurrence"))
    }


    @Test
    fun legacyReviewIsRequiredOnlyForAllWithMomentaryEvidence() {
        fun task(
            mode: TriggerMatchMode,
            triggers: List<Trigger>,
            version: Int,
        ) = Automation(
            id = "review",
            name = "Review",
            description = "",
            icon = "bolt",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "test",
            priority = 1,
            enabled = true,
            triggers = triggers,
            actions = emptyList(),
            triggerMatch = mode,
            createdAt = 0L,
            updatedAt = 0L,
            workflowVersion = version,
        )

        val eventAndState = listOf(
            Trigger(TriggerType.SMS, emptyMap()),
            Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
        )
        val statesOnly = listOf(
            Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
            Trigger(TriggerType.CHARGER, mapOf("state" to "CONNECTED")),
        )

        assertTrue(
            TriggerMatchPolicy.requiresOccurrenceSemanticsReview(
                task(
                    TriggerMatchMode.ALL,
                    eventAndState,
                    Automation.LEGACY_TRIGGER_SEMANTICS_VERSION,
                )
            )
        )
        assertFalse(
            TriggerMatchPolicy.requiresOccurrenceSemanticsReview(
                task(
                    TriggerMatchMode.ALL,
                    eventAndState,
                    Automation.OCCURRENCE_AWARE_TRIGGER_SEMANTICS_VERSION,
                )
            )
        )
        assertFalse(
            TriggerMatchPolicy.requiresOccurrenceSemanticsReview(
                task(
                    TriggerMatchMode.ALL,
                    statesOnly,
                    Automation.LEGACY_TRIGGER_SEMANTICS_VERSION,
                )
            )
        )
        assertFalse(
            TriggerMatchPolicy.requiresOccurrenceSemanticsReview(
                task(
                    TriggerMatchMode.ANY,
                    eventAndState,
                    Automation.LEGACY_TRIGGER_SEMANTICS_VERSION,
                )
            )
        )
    }

}
