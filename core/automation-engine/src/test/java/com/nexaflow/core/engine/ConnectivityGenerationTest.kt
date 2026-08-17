package com.nexaflow.core.engine

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the cellular generation mapping behind the NETWORK_MODE trigger.
 * The matcher must answer the same vocabulary the trigger config uses
 * (2G/3G/4G/5G), with 5G reported only for a genuine NR network type and
 * unknown types falling back to AUTO so an AUTO trigger matches.
 */
class ConnectivityGenerationTest {

    @Test
    fun `5G is reported only for genuine NR`() {
        assertEquals("5G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_NR))
        // LTE-anchored (NSA) setups report LTE through the legacy networkType;
        // the ServiceState NR check in ConnectivityMonitor catches those, and
        // the mapping itself must not fabricate 5G from a 4G type.
        assertEquals("4G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_LTE))
    }

    /** Legacy radio constants have no replacement and are the contract under test. */
    @Suppress("DEPRECATION")
    @Test
    fun `2G generations map to 2G`() {
        assertEquals("2G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_GPRS))
        assertEquals("2G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals("2G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_CDMA))
        assertEquals("2G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_1xRTT))
        assertEquals("2G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_IDEN))
    }

    @Test
    fun `3G generations map to 3G`() {
        assertEquals("3G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_UMTS))
        assertEquals("3G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_HSDPA))
        assertEquals("3G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_HSUPA))
        assertEquals("3G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals("3G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_TD_SCDMA))
    }

    @Test
    fun `4G generations map to 4G`() {
        assertEquals("4G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals("4G", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_IWLAN))
    }

    @Test
    fun `unknown types map to AUTO so an AUTO trigger matches`() {
        assertEquals("AUTO", cellularGenerationOf(TelephonyManager.NETWORK_TYPE_UNKNOWN))
        assertEquals("AUTO", cellularGenerationOf(Int.MIN_VALUE))
    }
}
