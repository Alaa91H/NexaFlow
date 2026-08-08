package com.nexaflow.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringTimeoutPolicyTest {

    // Mirror the ServiceInfo bit values without needing the Android SDK.
    private val DATA_SYNC = 1 shl 3
    private val MEDIA_PROCESSING = 1 shl 10
    private val SPECIAL_USE = 1 shl 11
    private val CONNECTED_DEVICE = 1 shl 7

    @Test
    fun dataSync_isTimeLimited() {
        assertTrue(MonitoringTimeoutPolicy.isTimeLimitedType(DATA_SYNC))
    }

    @Test
    fun mediaProcessing_isTimeLimited() {
        assertTrue(MonitoringTimeoutPolicy.isTimeLimitedType(MEDIA_PROCESSING))
    }

    @Test
    fun specialUse_isNotTimeLimited() {
        assertFalse(MonitoringTimeoutPolicy.isTimeLimitedType(SPECIAL_USE))
    }

    @Test
    fun connectedDevice_isNotTimeLimited() {
        assertFalse(MonitoringTimeoutPolicy.isTimeLimitedType(CONNECTED_DEVICE))
    }

    @Test
    fun combinedDataSyncPlusSpecialUse_isTimeLimited() {
        // The mask checks any time-limited bit, regardless of other types set.
        assertTrue(MonitoringTimeoutPolicy.isTimeLimitedType(DATA_SYNC or SPECIAL_USE))
    }

    @Test
    fun zero_isNotTimeLimited() {
        assertFalse(MonitoringTimeoutPolicy.isTimeLimitedType(0))
    }

    @Test
    fun constants_areSane() {
        assertEquals(6L * 60 * 60 * 1000, MonitoringTimeoutPolicy.TIME_LIMIT_MS)
        assertTrue(MonitoringTimeoutPolicy.RESUME_RETRY_MS > 0)
        assertTrue(MonitoringTimeoutPolicy.RESUME_RETRY_MS < MonitoringTimeoutPolicy.TIME_LIMIT_MS)
        assertTrue(MonitoringTimeoutPolicy.START_DELAY_MS > 0)
    }
}
