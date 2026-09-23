package com.nexaflow.core.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionSkipReportThrottleTest {

    @Test
    fun repeatedReasonWithinCooldownIsCoalesced() {
        val throttle = ExecutionSkipReportThrottle(cooldownMillis = 1_000L)

        assertTrue(throttle.shouldReport("wifi", "TRIGGER_GATE", 1_000L))
        assertFalse(throttle.shouldReport("wifi", "TRIGGER_GATE", 1_999L))
        assertTrue(throttle.shouldReport("wifi", "TRIGGER_GATE", 2_000L))
    }

    @Test
    fun differentReasonsAndTasksHaveIndependentWindows() {
        val throttle = ExecutionSkipReportThrottle(cooldownMillis = 1_000L)

        assertTrue(throttle.shouldReport("wifi", "TRIGGER_GATE", 1_000L))
        assertTrue(throttle.shouldReport("wifi", "CONSTRAINT_GATE", 1_001L))
        assertTrue(throttle.shouldReport("nfc", "TRIGGER_GATE", 1_002L))
    }

    @Test
    fun clockRollbackReportsFreshEvidence() {
        val throttle = ExecutionSkipReportThrottle(cooldownMillis = 1_000L)

        assertTrue(throttle.shouldReport("wifi", "TRIGGER_GATE", 2_000L))
        assertTrue(throttle.shouldReport("wifi", "TRIGGER_GATE", 1_000L))
    }
}
