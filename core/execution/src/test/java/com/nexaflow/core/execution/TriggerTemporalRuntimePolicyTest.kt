package com.nexaflow.core.execution

import com.nexaflow.domain.models.*
import com.nexaflow.domain.schedule.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerTemporalRuntimePolicyTest {
    private fun workflow(vararg triggers: Trigger) = Automation(
        id = "temporal-runtime", name = "Temporal runtime", description = "", icon = "bolt",
        iconColor = 0L, backgroundColor = 0L, category = "test", priority = 1, enabled = true,
        triggers = triggers.toList(), actions = emptyList(), createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun filtersOnlyTheMatchedTriggerAndPreservesLegacySibling() {
        val task = workflow(
            Trigger(TriggerType.SMS, mapOf("cooldownMs" to "1000")),
            Trigger(TriggerType.CHARGER, emptyMap()),
        )
        val policy = TriggerTemporalRuntimePolicy()
        assertEquals(TriggerFilterDecision.Allowed, policy.applyEventFilters(task, TriggerOccurrence.single(0, 1), 10))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN), policy.applyEventFilters(task, TriggerOccurrence.single(0, 2), 11))
        assertEquals(TriggerFilterDecision.Allowed, policy.applyEventFilters(task, TriggerOccurrence.single(1, 2), 11))
    }

    @Test
    fun malformedConfigIsUnknownAndUnknownObservationStaysUnknown() {
        val task = workflow(Trigger(TriggerType.SMS, mapOf("cooldownMs" to "-2")))
        val policy = TriggerTemporalRuntimePolicy()
        assertEquals(TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE), policy.applyEventFilters(task, TriggerOccurrence.single(0, 1), 10))
        assertEquals(ConditionResult.Unknown, policy.applyStableFor(task, 0, ConditionResult.Unknown, 10).result)
    }

    @Test
    fun onlyCurrentOccurrenceTriggerReceivesCooldown() {
        val task = workflow(
            Trigger(TriggerType.SMS, mapOf("cooldownMs" to "1000")),
            Trigger(TriggerType.CHARGER, emptyMap()),
        )
        val policy = TriggerTemporalRuntimePolicy()
        assertEquals(TriggerFilterDecision.Allowed, policy.applyEventFilters(task, TriggerOccurrence.single(0, 1), 10))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN), policy.applyEventFilters(task, TriggerOccurrence.single(0, 2), 11))
        assertEquals(TriggerFilterDecision.Allowed, policy.applyEventFilters(task, TriggerOccurrence.single(1, 2), 11))
    }

    @Test
    fun debounceOccurrenceMustReachTrailingEdgeAdmission() {
        val task = workflow(Trigger(TriggerType.VOLUME_CHANGED, mapOf("debounceMs" to "100")))
        val policy = TriggerTemporalRuntimePolicy()
        assertEquals(TriggerFilterDecision.Allowed, policy.observeDebouncedOccurrence(task, 0, 10))
        assertEquals(TriggerFilterDecision.Allowed, policy.observeDebouncedOccurrence(task, 0, 50))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), policy.applyEventFilters(task, TriggerOccurrence.single(0, 2), 50))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), policy.admitDebouncedOccurrence(task, 0, 149))
        assertEquals(TriggerFilterDecision.Allowed, policy.admitDebouncedOccurrence(task, 0, 150))
        val admitted = TriggerOccurrence.single(0, 3, debounceAdmitted = true)
        assertEquals(TriggerFilterDecision.Allowed, policy.applyEventFilters(task, admitted, 151))
        assertEquals(TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING), policy.applyEventFilters(task, admitted, 152))
    }
}
