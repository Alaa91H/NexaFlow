package com.nexaflow.core.execution.capability.semantic.strategies

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidApiStrategyAvailabilityPolicyTest {

    @Test
    fun `normal app Wi-Fi write is unavailable from Android 10 onward`() {
        assertTrue(publicWifiToggleAllowed(sdk = 28, frameworkPrivileged = false))
        assertFalse(publicWifiToggleAllowed(sdk = 29, frameworkPrivileged = false))
        assertFalse(publicWifiToggleAllowed(sdk = 37, frameworkPrivileged = false))
        assertTrue(publicWifiToggleAllowed(sdk = 37, frameworkPrivileged = true))
    }

    @Test
    fun `normal app Bluetooth write is unavailable from Android 13 onward`() {
        assertTrue(publicBluetoothToggleAllowed(sdk = 32, frameworkPrivileged = false))
        assertFalse(publicBluetoothToggleAllowed(sdk = 33, frameworkPrivileged = false))
        assertFalse(publicBluetoothToggleAllowed(sdk = 37, frameworkPrivileged = false))
        assertTrue(publicBluetoothToggleAllowed(sdk = 37, frameworkPrivileged = true))
    }
}
