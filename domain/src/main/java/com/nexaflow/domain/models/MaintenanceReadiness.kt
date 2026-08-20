package com.nexaflow.domain.models

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** A typed admission decision for a maintenance profile before any action runs. */
sealed interface MaintenanceReadiness {
    data object Ready : MaintenanceReadiness
    data class WaitingForWindow(val reason: MaintenanceWaitReason) : MaintenanceReadiness
}

enum class MaintenanceWaitReason {
    OUTSIDE_TIME_WINDOW,
    BATTERY_BELOW_MINIMUM,
    CHARGING_REQUIRED,
    UNMETERED_WIFI_REQUIRED,
    SCREEN_OFF_REQUIRED,
    DEVICE_IDLE_REQUIRED,
    THERMAL_STATUS_UNAVAILABLE,
    THERMAL_STATUS_TOO_HIGH,
    STORAGE_STATUS_UNAVAILABLE,
    STORAGE_BELOW_MINIMUM
}

/**
 * Pure maintenance admission gate. It consumes the existing runtime
 * ConstraintSnapshot and never starts, schedules, or persists work itself.
 */
object MaintenanceReadinessEvaluator {

    fun evaluate(
        profile: MaintenanceProfile?,
        snapshot: ConstraintSnapshot,
        now: ZonedDateTime
    ): MaintenanceReadiness {
        val window = profile?.window ?: return MaintenanceReadiness.Ready
        if (!isInsideWindow(window, now)) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.OUTSIDE_TIME_WINDOW)
        }
        if (window.minimumBatteryPercent != null && snapshot.batteryLevel < window.minimumBatteryPercent) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.BATTERY_BELOW_MINIMUM)
        }
        if (window.chargingRequired && !snapshot.isCharging) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.CHARGING_REQUIRED)
        }
        if (window.unmeteredWifiRequired && !snapshot.unmeteredNetwork) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.UNMETERED_WIFI_REQUIRED)
        }
        if (window.screenOffRequired && !snapshot.screenOff) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.SCREEN_OFF_REQUIRED)
        }
        if (window.deviceIdleRequired && !snapshot.deviceIdle) {
            return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.DEVICE_IDLE_REQUIRED)
        }
        if (window.maximumThermalStatus != null) {
            val thermal = snapshot.thermalStatus
                ?: return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.THERMAL_STATUS_UNAVAILABLE)
            if (thermal > window.maximumThermalStatus) {
                return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.THERMAL_STATUS_TOO_HIGH)
            }
        }
        if (window.minimumFreeStorageBytes != null) {
            val storage = snapshot.availableStorageBytes
                ?: return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.STORAGE_STATUS_UNAVAILABLE)
            if (storage < window.minimumFreeStorageBytes) {
                return MaintenanceReadiness.WaitingForWindow(MaintenanceWaitReason.STORAGE_BELOW_MINIMUM)
            }
        }
        return MaintenanceReadiness.Ready
    }

    /**
     * Checks a local-day maintenance interval, including windows crossing
     * midnight. For an overnight interval, allowedDays apply to its start day.
     */
    private fun isInsideWindow(window: MaintenanceWindow, now: ZonedDateTime): Boolean {
        val startValue = window.startTime ?: return isAllowedDay(window, now.dayOfWeek)
        val start = LocalTime.parse(startValue)
        val endValue = window.endTime
        if (endValue == null) {
            return isAllowedDay(window, now.dayOfWeek) && now.toLocalTime() >= start
        }
        val end = LocalTime.parse(endValue)
        val time = now.toLocalTime()
        if (end > start) {
            return isAllowedDay(window, now.dayOfWeek) && time >= start && time < end
        }
        val startsToday = time >= start && isAllowedDay(window, now.dayOfWeek)
        val endsToday = time < end && isAllowedDay(window, now.minusDays(1).dayOfWeek)
        return startsToday || endsToday
    }

    private fun isAllowedDay(window: MaintenanceWindow, day: DayOfWeek): Boolean =
        window.allowedDays.isEmpty() || day.value in window.allowedDays
}
