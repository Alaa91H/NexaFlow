package com.nexaflow.core.engine

import android.os.Build
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.ScheduledAutomationOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSchedulerExactAlarmPolicyTest {
    @Test
    fun preAndroid12DoesNotRequireExactAlarmSpecialAccess() {
        assertTrue(
            AutomationScheduler.exactAlarmAllowed(
                sdkInt = Build.VERSION_CODES.R,
                canScheduleExactAlarms = false
            )
        )
    }

    @Test
    fun occurrenceIdentityAndGenerationAreDeterministicAndConfigBound() {
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "08:00",
            "rangeEnd" to "17:00",
            "repeat" to "DAILY"
        )
        val occurrence = AutomationScheduler.occurrenceId(1_000L, 2_000L)
        val first = AutomationScheduler.generationOf("automation-a", config, 1_000L, 2_000L)

        assertEquals(occurrence, AutomationScheduler.occurrenceId(1_000L, 2_000L))
        assertEquals(first, AutomationScheduler.generationOf("automation-a", config, 1_000L, 2_000L))
        assertNotEquals(
            first,
            AutomationScheduler.generationOf(
                "automation-a",
                config + ("rangeEnd" to "18:00"),
                1_000L,
                2_000L
            )
        )
        assertNotEquals(
            first,
            AutomationScheduler.generationOf("automation-a", config, 2_000L, 3_000L)
        )
    }

    @Test
    fun `reconciliation selects the retained future end for an active overnight range`() {
        // Represents a 22:00 start with the same occurrence's 06:00 next-day END.
        // The scheduler must reuse this persisted identity after reboot rather than
        // generating a new 22:00 occurrence, which could not validate the ACTIVE
        // lifecycle at 06:00.
        val retained = ScheduledAutomationOccurrence(
            automationId = "night-silence",
            occurrenceId = "time:22:00-to-next-06:00",
            generation = "generation-22-06",
            windowStartAt = 22_000L,
            windowEndAt = 30_000L
        )
        val active = AutomationRuntimeState(
            automationId = retained.automationId,
            occurrenceId = retained.occurrenceId,
            source = "time-range",
            sourceKey = retained.occurrenceId,
            lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
            activatedAt = retained.windowStartAt,
            expectedEndAt = retained.windowEndAt,
            scheduleGeneration = retained.generation
        )
        val staleNextStart = retained.copy(
            occurrenceId = "time:next-22:00-to-next-06:00",
            generation = "generation-next",
            windowStartAt = 46_000L,
            windowEndAt = 54_000L
        )

        assertEquals(
            retained,
            AutomationScheduler.retainedActiveEndForRearm(
                activeState = active,
                occurrences = listOf(staleNextStart, retained),
                now = 25_000L
            )
        )
    }

    @Test
    fun `reconciliation never re-arms a stale elapsed or exiting time range`() {
        val retained = ScheduledAutomationOccurrence(
            automationId = "night-silence",
            occurrenceId = "time:22:00-to-next-06:00",
            generation = "generation-22-06",
            windowStartAt = 22_000L,
            windowEndAt = 30_000L
        )
        val active = AutomationRuntimeState(
            automationId = retained.automationId,
            occurrenceId = retained.occurrenceId,
            source = "time-range",
            sourceKey = retained.occurrenceId,
            lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
            activatedAt = retained.windowStartAt,
            expectedEndAt = retained.windowEndAt,
            scheduleGeneration = retained.generation
        )

        assertNull(
            AutomationScheduler.retainedActiveEndForRearm(
                activeState = active,
                occurrences = listOf(retained),
                now = retained.windowEndAt!!
            )
        )
        assertNull(
            AutomationScheduler.retainedActiveEndForRearm(
                activeState = active.copy(lifecycleState = AutomationRuntimeLifecycleState.EXITING),
                occurrences = listOf(retained),
                now = 25_000L
            )
        )
        assertNull(
            AutomationScheduler.retainedActiveEndForRearm(
                activeState = active.copy(scheduleGeneration = "edited-generation"),
                occurrences = listOf(retained),
                now = 25_000L
            )
        )
    }

    @Test
    fun receiverRetryPolicyIsBoundedAndRejectsInvalidAttempts() {
        assertFalse(AutomationScheduler.receiverRetryAllowed(-1))
        assertTrue(AutomationScheduler.receiverRetryAllowed(0))
        assertTrue(AutomationScheduler.receiverRetryAllowed(1))
        assertFalse(AutomationScheduler.receiverRetryAllowed(2))
    }

    @Test
    fun android12PlusRequiresExactAlarmSpecialAccessForPunctualExecution() {
        assertFalse(
            AutomationScheduler.exactAlarmAllowed(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = false
            )
        )
        assertTrue(
            AutomationScheduler.exactAlarmAllowed(
                sdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = true
            )
        )
    }
}
