package com.nexaflow.core.rom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the two failure modes reported from a customized OEM
 * device (NFA-23):
 *
 * 1. A saved "5G" profile was the NR-only bitmask, which removed the LTE
 *    anchor and dropped the SIM out of service.
 * 2. The pre-write capability re-read failed with "network capabilities …
 *    no longer readable" while the radio re-registered, blocking the same
 *    write that would have restored a working mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkModeControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `5G-only requests are rejected before the radio is touched`() {
        val result = NetworkModeController(context).setNetworkMode(
            mode = "DYNAMIC",
            requestedMask = NetworkModePolicy.BITMASK_5G,
            subscriptionId = 1
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("5G alone"))
    }

    @Test
    fun `a 5G preference that keeps LTE stays applicable`() {
        val result = NetworkModeController(context).setNetworkMode(
            mode = "DYNAMIC",
            requestedMask = NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G,
            subscriptionId = 1
        )

        // The write itself still needs telephony privileges; what matters here
        // is that the profile is not refused by the policy guard.
        assertFalse(result.success)
        assertFalse(result.message.contains("5G alone"))
    }

    @Test
    fun `an unreadable capability snapshot no longer blocks a dynamic write`() {
        // The removed gate returned "Network capabilities for the selected SIM
        // are no longer readable" without ever attempting the write.
        val result = NetworkModeController(context).setNetworkMode(
            mode = "DYNAMIC",
            requestedMask = NetworkModePolicy.BITMASK_4G,
            subscriptionId = 1
        )

        assertFalse(result.success)
        assertFalse(result.message.contains("no longer readable"))
        assertFalse(result.message.contains("no longer supported"))
    }
}
