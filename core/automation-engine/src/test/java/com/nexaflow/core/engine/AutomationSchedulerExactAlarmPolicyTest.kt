package com.nexaflow.core.engine

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
