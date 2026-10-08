package com.nexaflow.core.engine

import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationAlarmReceiverTimeRangeExecutionTest {
    @Test
    fun `valid range start remains executable when delivery is after nominal end`() {
        assertTrue(
            AutomationAlarmReceiver.shouldExecuteRangeStart(
                isTimeRange = true,
                windowEndAt = 2_000L,
                deliveredAt = 1_999L,
            )
        )
    }

    @Test
    fun `range start delivered at or after its end is skipped`() {
        assertFalse(AutomationAlarmReceiver.shouldExecuteRangeStart(true, 2_000L, 2_000L))
        assertFalse(AutomationAlarmReceiver.shouldExecuteRangeStart(true, 2_000L, 2_001L))
    }

    @Test
    fun `malformed range without an end is rejected`() {
        assertFalse(
            AutomationAlarmReceiver.shouldExecuteRangeStart(
                isTimeRange = true,
                windowEndAt = null,
                deliveredAt = 1_000L,
            )
        )
    }

    @Test
    fun `one shot without a range remains executable`() {
        assertTrue(
            AutomationAlarmReceiver.shouldExecuteRangeStart(
                isTimeRange = false,
                windowEndAt = null,
                deliveredAt = 1_000L,
            )
        )
    }

    @Test
    fun `point occurrence is accepted only within its fifteen minute grace`() {
        val start = 10_000L
        assertTrue(AutomationAlarmReceiver.shouldExecutePointStart(start, start))
        assertTrue(AutomationAlarmReceiver.shouldExecutePointStart(start, start + 15 * 60_000L))
        assertFalse(AutomationAlarmReceiver.shouldExecutePointStart(start, start + 15 * 60_000L + 1L))
        assertFalse(AutomationAlarmReceiver.shouldExecutePointStart(start, start - 1L))
    }

    @Test
    fun `late occurrence diagnostic is classified as skipped`() {
        val record = AutomationAlarmReceiver.skippedOccurrenceRecord(
            automationId = "task",
            automationName = "Task",
            occurrenceId = "occurrence-1",
            occurredAt = 10_000L,
            deliveredAt = 20_000L,
            reason = "point occurrence was outside the 15-minute delivery window",
        )
        val retryRecord = AutomationAlarmReceiver.skippedOccurrenceRecord(
            automationId = "task",
            automationName = "Task",
            occurrenceId = "occurrence-1",
            occurredAt = 10_000L,
            deliveredAt = 20_000L,
            reason = "point occurrence was outside the 15-minute delivery window",
        )

        assertTrue(record.success)
        assertTrue(com.nexaflow.domain.models.ExecutionOutcomeClassifier.isSkipped(record))
        assertEquals(20_000L, record.executedAt)
        assertEquals("history upsert remains idempotent after a receiver crash", record.id, retryRecord.id)
    }

    @Test
    fun `boot trigger indices include every boot condition in saved order`() {
        val automation = testAutomation(
            id = "boot-task",
            triggers = listOf(
                Trigger(TriggerType.BOOT_COMPLETED, emptyMap()),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
                Trigger(TriggerType.BOOT_COMPLETED, emptyMap()),
            ),
        )

        assertEquals(
            setOf(0, 2),
            AutomationAlarmReceiver.bootTriggerIndices(automation),
        )
    }

    @Test
    fun `boot occurrence id stays stable across receiver redelivery in same boot`() {
        val first = AutomationAlarmReceiver.bootOccurrenceId(
            epochMillis = 1_700_000_050_000L,
            elapsedRealtimeMillis = 50_000L,
        )
        val redelivery = AutomationAlarmReceiver.bootOccurrenceId(
            epochMillis = 1_700_000_055_000L,
            elapsedRealtimeMillis = 55_000L,
        )

        assertEquals(first, redelivery)
    }


    @Test
    fun `identical scheduled time triggers share one occurrence evidence set`() {
        val config = mapOf("timeMode" to "AT", "time" to "08:00", "repeat" to "DAILY")
        val automation = testAutomation(
            id = "time-duplicates",
            triggers = listOf(
                Trigger(TriggerType.TIME, config),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
                Trigger(TriggerType.TIME, config),
                Trigger(TriggerType.TIME, config + ("time" to "09:00")),
            ),
        )

        assertEquals(
            setOf(0, 2),
            AutomationAlarmReceiver.matchingScheduledTimeTriggerIndices(automation),
        )
    }

}
