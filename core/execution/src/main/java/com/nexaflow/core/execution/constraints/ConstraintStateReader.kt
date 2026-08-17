package com.nexaflow.core.execution.constraints

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.nexaflow.domain.models.ConstraintSnapshot

/**
 * Captures the device state a [Constraint] gate checks, right before a task
 * runs. Every probe is defensive: a failed probe yields the fail-closed
 * default (e.g. unknown battery level = -1) so constraints never let a task
 * run against state we could not verify.
 */
object ConstraintStateReader {

    fun capture(context: Context): ConstraintSnapshot {
        val app = context.applicationContext
        return ConstraintSnapshot(
            wifiConnected = isWifiConnected(app),
            batteryLevel = batteryLevel(app),
            screenLocked = isScreenLocked(app),
            headsetConnected = isHeadsetConnected(app),
            bluetoothEnabled = isBluetoothEnabled(app),
            dndActive = isDndActive(app),
            airplaneModeOn = isAirplaneModeOn(app),
            isCharging = isCharging(app),
            locationEnabled = isLocationEnabled(app)
        )
    }

    /** True when the active network is Wi-Fi (or the radio is connected to Wi-Fi). */
    @SuppressLint("MissingPermission") // guarded by the checkPermission probe below
    private fun isWifiConnected(context: Context): Boolean = runCatching {
        // ACCESS_NETWORK_STATE is a normal permission (auto-granted), but the
        // probe keeps lint honest and fail-closes on denial.
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    /** Battery percentage 0..100, or -1 when unreadable. */
    private fun batteryLevel(context: Context): Int = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (capacity != null && capacity in 0..100) capacity else {
            // Fallback: sticky ACTION_BATTERY_CHANGED broadcast.
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (scale > 0) (level * 100 / scale) else -1
        }
    }.getOrDefault(-1)

    /** True when the keyguard is showing (screen locked). */
    private fun isScreenLocked(context: Context): Boolean = runCatching {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        km.isKeyguardLocked
    }.getOrDefault(false)

    /** True when a wired headset is plugged in. */
    private fun isHeadsetConnected(context: Context): Boolean = runCatching {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return false
        audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrDefault(false)

    /** True when Bluetooth is enabled. */
    private fun isBluetoothEnabled(context: Context): Boolean = runCatching {
        context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            ?.adapter?.isEnabled ?: false
    }.getOrDefault(false)

    /** True when Do Not Disturb is active. */
    private fun isDndActive(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    /** True when airplane mode is on. */
    private fun isAirplaneModeOn(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }.getOrDefault(false)

    /** True when the device is charging. */
    private fun isCharging(context: Context): Boolean = runCatching {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(null, filter)
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)

    /** True when location services are enabled. */
    private fun isLocationEnabled(context: Context): Boolean = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)
}
