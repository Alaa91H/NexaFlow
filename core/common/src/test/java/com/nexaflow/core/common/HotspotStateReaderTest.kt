package com.nexaflow.core.common

import android.net.TetheringManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotStateReaderTest {

    @Test
    fun `wifi tethering interface means internet hotspot is on`() {
        assertTrue(
            HotspotStateReader.hasWifiTetheringInterface(
                listOf(TetheringManager.TETHERING_WIFI)
            )
        )
    }

    @Test
    fun `non wifi tethering interfaces do not mean internet hotspot is on`() {
        assertFalse(
            HotspotStateReader.hasWifiTetheringInterface(
                // قيمة غير Wi‑Fi تمثل أي واجهة تقييد أخرى أو قيمة مستقبلية.
                listOf(Int.MIN_VALUE)
            )
        )
    }

    @Test
    fun `empty tethered interfaces mean internet hotspot is off`() {
        assertFalse(HotspotStateReader.hasWifiTetheringInterface(emptyList()))
    }
}
