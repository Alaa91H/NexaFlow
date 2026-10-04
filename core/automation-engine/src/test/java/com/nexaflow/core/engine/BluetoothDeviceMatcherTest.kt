package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDeviceMatcherTest {
    @Test
    fun `empty target matches every device`() {
        assertTrue(bluetoothDeviceMatches(emptyMap(), "AA:BB", "Headphones"))
        assertTrue(bluetoothDeviceMatches(mapOf("deviceName" to "__ANY__"), "AA:BB", "Headphones"))
    }

    @Test
    fun `address without name only matches that address`() {
        val config = mapOf("deviceName" to "", "deviceAddress" to "AA:BB")
        assertTrue(bluetoothDeviceMatches(config, "aa:bb", "Headphones"))
        assertFalse(bluetoothDeviceMatches(config, "CC:DD", "Headphones"))
    }

    @Test
    fun `name without address matches name ignoring case`() {
        val config = mapOf("deviceName" to "Headphones", "deviceAddress" to "")
        assertTrue(bluetoothDeviceMatches(config, "AA:BB", "headphones"))
        assertFalse(bluetoothDeviceMatches(config, "AA:BB", "Speaker"))
    }
}
