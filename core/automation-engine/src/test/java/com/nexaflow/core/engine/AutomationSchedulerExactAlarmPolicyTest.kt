package com.nexaflow.core.engine

import android.os.Build
import org.junit.Assert.assertFalse
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
