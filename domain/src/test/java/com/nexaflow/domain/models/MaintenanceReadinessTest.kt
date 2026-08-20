package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MaintenanceReadinessTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `accepts a ready maintenance window when every resource gate passes`() {
        val readiness = MaintenanceReadinessEvaluator.evaluate(
            profile = profile(),
            snapshot = readySnapshot(),
            now = at(day = 2, hour = 3)
        )

        assertEquals(MaintenanceReadiness.Ready, readiness)
    }

    @Test
    fun `waits outside the configured maintenance time window`() {
        val readiness = MaintenanceReadinessEvaluator.evaluate(
            profile = profile(),
            snapshot = readySnapshot(),
            now = at(day = 2, hour = 8)
        )

        assertEquals(
            MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.OUTSIDE_TIME_WINDOW),
            readiness
        )
    }

    @Test
    fun `treats overnight window as belonging to its start day`() {
        val profile = MaintenanceProfile(
            kind = MaintenanceKind.NIGHT,
            window = MaintenanceWindow(
                startTime = "22:00",
                endTime = "05:00",
                allowedDays = setOf(7)
            )
        )

        val readiness = MaintenanceReadinessEvaluator.evaluate(
            profile = profile,
            snapshot = ConstraintSnapshot(),
            now = at(day = 3, hour = 2)
        )

        assertEquals(MaintenanceReadiness.Ready, readiness)
    }

    @Test
    fun `waits for every unavailable required resource`() {
        val cases = listOf(
            readySnapshot(batteryLevel = 49) to MaintenanceWaitReason.BATTERY_BELOW_MINIMUM,
            readySnapshot(isCharging = false) to MaintenanceWaitReason.CHARGING_REQUIRED,
            readySnapshot(unmeteredNetwork = false) to MaintenanceWaitReason.UNMETERED_WIFI_REQUIRED,
            readySnapshot(screenOff = false) to MaintenanceWaitReason.SCREEN_OFF_REQUIRED,
            readySnapshot(deviceIdle = false) to MaintenanceWaitReason.DEVICE_IDLE_REQUIRED,
            readySnapshot(thermalStatus = 4) to MaintenanceWaitReason.THERMAL_STATUS_TOO_HIGH,
            readySnapshot(availableStorageBytes = 4_999L) to MaintenanceWaitReason.STORAGE_BELOW_MINIMUM
        )

        cases.forEach { (snapshot, reason) ->
            assertEquals(
                MaintenanceReadiness.WaitingForWindow(reason),
                MaintenanceReadinessEvaluator.evaluate(profile(), snapshot, at(day = 2, hour = 3))
            )
        }
    }

    private fun profile() = MaintenanceProfile(
        kind = MaintenanceKind.NIGHT,
        window = MaintenanceWindow(
            startTime = "02:00",
            endTime = "05:00",
            allowedDays = setOf(1, 2, 3, 4, 5, 6, 7),
            minimumBatteryPercent = 50,
            chargingRequired = true,
            unmeteredWifiRequired = true,
            screenOffRequired = true,
            deviceIdleRequired = true,
            maximumThermalStatus = 3,
            minimumFreeStorageBytes = 5_000L
        )
    )

    private fun readySnapshot(
        batteryLevel: Int = 50,
        isCharging: Boolean = true,
        unmeteredNetwork: Boolean = true,
        screenOff: Boolean = true,
        deviceIdle: Boolean = true,
        thermalStatus: Int? = 3,
        availableStorageBytes: Long? = 5_000L
    ) = ConstraintSnapshot(
        batteryLevel = batteryLevel,
        isCharging = isCharging,
        unmeteredNetwork = unmeteredNetwork,
        screenOff = screenOff,
        deviceIdle = deviceIdle,
        thermalStatus = thermalStatus,
        availableStorageBytes = availableStorageBytes
    )

    private fun at(day: Int, hour: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, zone)
}
