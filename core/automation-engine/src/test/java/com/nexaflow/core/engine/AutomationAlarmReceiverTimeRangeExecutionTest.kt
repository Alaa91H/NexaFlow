package com.nexaflow.core.engine

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
            )
        )
    }

    @Test
    fun `malformed range without an end is rejected`() {
        assertFalse(
            AutomationAlarmReceiver.shouldExecuteRangeStart(
                isTimeRange = true,
                windowEndAt = null,
            )
        )
    }

    @Test
    fun `one shot without a range remains executable`() {
        assertTrue(
            AutomationAlarmReceiver.shouldExecuteRangeStart(
                isTimeRange = false,
                windowEndAt = null,
            )
        )
    }
}
