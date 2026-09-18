package com.nexaflow.core.execution

import com.nexaflow.core.datastore.ActiveExecutionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointAdmissionReportThrottleTest {

    @Test
    fun repeatedCapacityDeferrals_areReportedOnlyOnceDuringCooldown() {
        val throttle = CheckpointAdmissionReportThrottle(cooldownMillis = 1_000L)

        assertTrue(throttle.shouldReport("wifi", capacity(), now = 1_000L))
        assertFalse(throttle.shouldReport("wifi", capacity(), now = 1_999L))
        assertTrue(throttle.shouldReport("wifi", capacity(), now = 2_000L))
    }

    @Test
    fun differentAdmissionReasons_keepSeparateDiagnosticWindows() {
        val throttle = CheckpointAdmissionReportThrottle(cooldownMillis = 1_000L)

        assertTrue(throttle.shouldReport("wifi", capacity(), now = 1_000L))
        assertTrue(
            throttle.shouldReport(
                "wifi",
                ActiveExecutionStore.CheckpointAdmission.DUPLICATE_RUN_ID,
                now = 1_001L
            )
        )
    }

    private fun capacity() = ActiveExecutionStore.CheckpointAdmission.CAPACITY_RESERVED_FOR_RECOVERY
}
