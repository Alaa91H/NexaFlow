package com.nexaflow.core.common

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularNetworkReaderTest {

    @Test
    fun `framework network types map to trigger generations`() {
        assertEquals(CellularNetworkReader.GENERATION_2G,
            CellularNetworkReader.generationOf(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals(CellularNetworkReader.GENERATION_3G,
            CellularNetworkReader.generationOf(TelephonyManager.NETWORK_TYPE_UMTS))
        assertEquals(CellularNetworkReader.GENERATION_4G,
            CellularNetworkReader.generationOf(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals(CellularNetworkReader.GENERATION_5G,
            CellularNetworkReader.generationOf(TelephonyManager.NETWORK_TYPE_NR))
    }

    @Test
    fun `unknown network types return null`() {
        assertEquals(null, CellularNetworkReader.generationOf(TelephonyManager.NETWORK_TYPE_UNKNOWN))
        assertEquals(null, CellularNetworkReader.generationOf(Int.MIN_VALUE))
    }

    @Test
    fun `AUTO matches only a known generation`() {
        assertTrue(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, "4G"))
        assertTrue(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, "5G"))
        assertFalse(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, null))
        assertFalse(CellularNetworkReader.matchesNetworkMode("5G", null))
        assertFalse(CellularNetworkReader.matchesNetworkMode("5G", "4G"))
    }
}
