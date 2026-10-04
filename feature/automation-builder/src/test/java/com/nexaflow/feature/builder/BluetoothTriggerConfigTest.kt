package com.nexaflow.feature.builder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothTriggerConfigTest {
    @Test
    fun `empty target means any bluetooth device`() {
        assertTrue(BluetoothTriggerConfig.isAnyDevice("", ""))
        assertTrue(BluetoothTriggerConfig.isAnyDevice("__ANY__", ""))
    }

    @Test
    fun `manual name or address targets a specific device`() {
        assertFalse(BluetoothTriggerConfig.isAnyDevice("Pixel Buds", ""))
        assertFalse(BluetoothTriggerConfig.isAnyDevice("", "AA:BB:CC:DD:EE:FF"))
    }
}
