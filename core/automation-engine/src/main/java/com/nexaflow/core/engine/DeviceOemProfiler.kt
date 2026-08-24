package com.nexaflow.core.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.ThermalState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device OEM and power management profiler.
 *
 * Detects:
 *  - Doze mode (active, idle, light)
 *  - Battery optimization exemption
 *  - OEM-specific background restrictions
 *  - Wakelock / alarm readiness
 *  - Thermal state
 *
 * Results feed into [CapabilityDeviceState] which the [CapabilityResolver]
 * uses for backend ranking. No raw Android objects cross this boundary.
 */
@Singleton
class DeviceOemProfiler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Summary of the device's current execution-relevant power profile.
     */
    data class OemPowerProfile(
        val isInDoze: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val isBackgroundRestricted: Boolean,
        val thermalState: ThermalState,
        val canScheduleExactAlarms: Boolean,
        val isInteractive: Boolean
    )

    /**
     * Snapshots current power/OEM profile. This is a best-effort read —
     * some fields may not be available on all OEM variants; they default to false.
     */
    fun snapshot(): OemPowerProfile {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isInDoze = if (Build.VERSION.SDK_INT >= 23) {
            powerManager?.isDeviceIdleMode == true
        } else false

        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= 23) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true

        val isBackgroundRestricted = if (Build.VERSION.SDK_INT >= 28) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isBackgroundRestricted == true
        } else false

        val thermalState = if (Build.VERSION.SDK_INT >= 29) {
            powerManager?.let { mapThermalStatus(it.currentThermalStatus) } ?: ThermalState.UNKNOWN
        } else ThermalState.UNKNOWN

        val canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.canScheduleExactAlarms() == true
        } else true

        val isInteractive = powerManager?.isInteractive == true

        return OemPowerProfile(
            isInDoze = isInDoze,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            isBackgroundRestricted = isBackgroundRestricted,
            thermalState = thermalState,
            canScheduleExactAlarms = canScheduleExactAlarms,
            isInteractive = isInteractive
        )
    }

    /**
     * Returns a [CapabilityDeviceState] annotated with power-aware fields.
     * Callers should merge this with wifi/battery fields from [DeviceStateSnapshot].
     */
    fun toCapabilityDeviceState(
        base: CapabilityDeviceState = CapabilityDeviceState(capturedAt = System.currentTimeMillis())
    ): CapabilityDeviceState {
        val profile = snapshot()
        return base.copy(
            thermalState = profile.thermalState,
            screenInteractive = profile.isInteractive,
            capturedAt = System.currentTimeMillis()
        )
    }

    /**
     * Diagnostic report for the Capability Center and Execution Inspector UI.
     */
    fun diagnostics(): Map<String, String> {
        val profile = snapshot()
        return mapOf(
            "isInDoze" to profile.isInDoze.toString(),
            "isIgnoringBatteryOptimizations" to profile.isIgnoringBatteryOptimizations.toString(),
            "isBackgroundRestricted" to profile.isBackgroundRestricted.toString(),
            "thermalState" to profile.thermalState.name,
            "canScheduleExactAlarms" to profile.canScheduleExactAlarms.toString(),
            "isInteractive" to profile.isInteractive.toString(),
            "androidSdk" to Build.VERSION.SDK_INT.toString(),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL
        )
    }

    private fun mapThermalStatus(status: Int): ThermalState {
        return if (Build.VERSION.SDK_INT >= 29) {
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                else -> ThermalState.UNKNOWN
            }
        } else ThermalState.UNKNOWN
    }
}
