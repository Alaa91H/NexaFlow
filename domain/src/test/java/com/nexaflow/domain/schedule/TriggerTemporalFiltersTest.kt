package com.nexaflow.domain.schedule

import com.nexaflow.domain.models.ConditionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerTemporalFiltersTest {

    @Test
    fun absentFilterConfigurationPreservesImmediateLegacyBehavior() {
        assertEquals(
            TriggerFilterConfigParse.Unconfigured,
            TriggerTemporalFilterConfigParser.parse(emptyMap()),
        )
    }

    @Test
    fun eventCooldownAllowsFirstAndExactBoundaryButBlocksInsideWindow() {
        val config = validConfig(cooldownMs = 1_000L)
        val state = TriggerTemporalFilterState()

        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:0", config, 100L))
        assertEquals(
            TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN),
            state.evaluateEvent("workflow:0", config, 1_099L),
        )
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:0", config, 1_100L))
    }

    @Test
    fun cooldownAloneAllowsOccurrencesAfterItsWindowExpires() {
        val state = TriggerTemporalFilterState()
        val config = validConfig(cooldownMs = 10L)
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:cooldown-only", config, 100L))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN), state.evaluateEvent("workflow:cooldown-only", config, 105L))
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:cooldown-only", config, 110L))
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:cooldown-only", config, 5_000L))
    }

    @Test
    fun debounceWaitsForTrailingEdgeInsteadOfAdmittingImmediately() {
        val config = validConfig(debounceMs = 500L)
        val state = TriggerTemporalFilterState()

        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), state.evaluateEvent("workflow:2", config, 0L))
        assertEquals(
            TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING),
            state.evaluateEvent("workflow:2", config, 499L),
        )
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), state.evaluateEvent("workflow:2", config, 500L))
        assertEquals(TriggerFilterDecision.Allowed, state.admitDebounced("workflow:2", config, 1_000L))
    }

    @Test
    fun debounceWindowRestartsForEveryOccurrence() {
        val config = validConfig(debounceMs = 500L)
        val state = TriggerTemporalFilterState()
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), state.evaluateEvent("workflow:quiet", config, 0L))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), state.evaluateEvent("workflow:quiet", config, 400L))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), state.admitDebounced("workflow:quiet", config, 899L))
        assertEquals(TriggerFilterDecision.Allowed, state.admitDebounced("workflow:quiet", config, 900L))
    }

    @Test
    fun slidingRateLimitExpiresAtExactWindowBoundary() {
        val config = validConfig(rateLimitCount = 2, rateLimitWindowMs = 1_000L)
        val state = TriggerTemporalFilterState()

        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:3", config, 0L))
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:3", config, 100L))
        assertEquals(
            TriggerFilterDecision.Blocked(TriggerFilterReason.RATE_LIMITED),
            state.evaluateEvent("workflow:3", config, 200L),
        )
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:3", config, 1_000L))
    }

    @Test
    fun numericHysteresisPreventsThresholdOscillation() {
        val config = validConfig(hysteresis = 5.0)
        val state = TriggerTemporalFilterState()

        assertEquals(ConditionResult.Unsatisfied, state.evaluateThreshold("workflow:4", 79.0, 80.0, true, config, 0L))
        assertEquals(ConditionResult.Satisfied, state.evaluateThreshold("workflow:4", 80.0, 80.0, true, config, 1L))
        assertEquals(ConditionResult.Satisfied, state.evaluateThreshold("workflow:4", 76.0, 80.0, true, config, 2L))
        assertEquals(ConditionResult.Unsatisfied, state.evaluateThreshold("workflow:4", 74.0, 80.0, true, config, 3L))
    }

    @Test
    fun stableForReportsUnknownUntilTheKnownConditionPersistsForTheDuration() {
        val config = validConfig(stableForMs = 1_000L)
        val state = TriggerTemporalFilterState()

        assertEquals(TriggerFilterReason.STABILITY_PENDING, state.evaluateStability("workflow:5", ConditionResult.Satisfied, config, 100L).reason)
        assertEquals(ConditionResult.Unknown, state.evaluateStability("workflow:5", ConditionResult.Satisfied, config, 1_099L).result)
        assertEquals(ConditionResult.Satisfied, state.evaluateStability("workflow:5", ConditionResult.Satisfied, config, 1_100L).result)
        assertEquals(ConditionResult.Unsatisfied, state.evaluateStability("workflow:5", ConditionResult.Unsatisfied, config, 1_101L).result)
        assertEquals(ConditionResult.Unknown, state.evaluateStability("workflow:5", ConditionResult.Satisfied, config, 1_102L).result)
    }

    @Test
    fun unknownObservationBreaksStabilityContinuityWithoutBecomingFalse() {
        val config = validConfig(stableForMs = 1_000L)
        val state = TriggerTemporalFilterState()

        assertEquals(ConditionResult.Unknown, state.evaluateStability("workflow:6", ConditionResult.Satisfied, config, 10L).result)
        assertEquals(ConditionResult.Unknown, state.evaluateStability("workflow:6", ConditionResult.Unknown, config, 500L).result)
        assertEquals(TriggerFilterReason.STABILITY_PENDING, state.evaluateStability("workflow:6", ConditionResult.Satisfied, config, 1_010L).reason)
        assertEquals(ConditionResult.Satisfied, state.evaluateStability("workflow:6", ConditionResult.Satisfied, config, 2_010L).result)
    }

    @Test
    fun unavailableAndErrorObservationsResetStableForWithoutBecomingFalse() {
        val config = validConfig(stableForMs = 1_000L)
        listOf(ConditionResult.Unavailable, ConditionResult.Error("permission denied")).forEachIndexed { index, unknown ->
            val state = TriggerTemporalFilterState()
            val key = "workflow:unknown:$index"
            assertEquals(ConditionResult.Unknown, state.evaluateStability(key, ConditionResult.Satisfied, config, 0L).result)
            assertEquals(unknown, state.evaluateStability(key, unknown, config, 500L).result)
            assertEquals(TriggerFilterReason.STABILITY_PENDING, state.evaluateStability(key, ConditionResult.Satisfied, config, 600L).reason)
            assertEquals(ConditionResult.Satisfied, state.evaluateStability(key, ConditionResult.Satisfied, config, 1_600L).result)
        }
    }

    @Test
    fun monotonicClockRollbackClearsHistoryAndReturnsUnknownOnce() {
        val config = validConfig(cooldownMs = 100L)
        val state = TriggerTemporalFilterState()

        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:7", config, 500L))
        assertEquals(
            TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET),
            state.evaluateEvent("workflow:7", config, 400L),
        )
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("workflow:7", config, 401L))
    }

    @Test
    fun malformedNegativeAndOverflowValuesAreExplicitlyInvalid() {
        assertTrue(TriggerTemporalFilterConfigParser.parse(mapOf("cooldownMs" to "-1")) is TriggerFilterConfigParse.Invalid)
        assertTrue(TriggerTemporalFilterConfigParser.parse(mapOf("stableForMs" to "9223372036854775808")) is TriggerFilterConfigParse.Invalid)
        assertTrue(TriggerTemporalFilterConfigParser.parse(mapOf("hysteresis" to "NaN")) is TriggerFilterConfigParse.Invalid)
    }

    @Test
    fun filterStateIsBoundedAndOldestEntryIsEvicted() {
        val config = validConfig(cooldownMs = 10L)
        val state = TriggerTemporalFilterState(maxEntries = 2)

        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("a", config, 0L))
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("b", config, 0L))
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("c", config, 0L))
        assertEquals(2, state.entryCountForTest())
        assertEquals(TriggerFilterDecision.Allowed, state.evaluateEvent("a", config, 1L))
    }

    private fun validConfig(
        debounceMs: Long? = null,
        cooldownMs: Long? = null,
        rateLimitCount: Int? = null,
        rateLimitWindowMs: Long? = null,
        minIntervalMs: Long? = null,
        stableForMs: Long? = null,
        hysteresis: Double? = null,
    ): TriggerTemporalFilterConfig = TriggerTemporalFilterConfig(
        debounceMs = debounceMs,
        minIntervalMs = minIntervalMs,
        cooldownMs = cooldownMs,
        rateLimitCount = rateLimitCount,
        rateLimitWindowMs = rateLimitWindowMs,
        stableForMs = stableForMs,
        hysteresis = hysteresis,
    )
}



