package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MaintenanceProfileTest {

    @Test
    fun `accepts an overnight maintenance window with resource requirements`() {
        val window = MaintenanceWindow(
            startTime = "02:00",
            endTime = "05:00",
            allowedDays = setOf(1, 3, 5),
            minimumBatteryPercent = 50,
            chargingRequired = true,
            unmeteredWifiRequired = true,
            deviceIdleRequired = true,
            maximumThermalStatus = 3,
            minimumFreeStorageBytes = 5_000_000_000L
        )

        assertEquals("02:00", window.startTime)
        assertEquals(50, window.minimumBatteryPercent)
    }

    @Test
    fun `rejects invalid maintenance day`() {
        try {
            MaintenanceWindow(allowedDays = setOf(0))
            fail("Expected invalid ISO day to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rejects duplicate maintenance dependencies`() {
        try {
            MaintenanceProfile(
                kind = MaintenanceKind.DAILY,
                dependencyAutomationIds = listOf("storage", "storage")
            )
            fail("Expected duplicate dependency to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
