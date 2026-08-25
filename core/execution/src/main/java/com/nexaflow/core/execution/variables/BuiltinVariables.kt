package com.nexaflow.core.execution.variables

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.nexaflow.core.common.DefaultNetworkSnapshot
import com.nexaflow.core.common.DefaultNetworkStateReader
import com.nexaflow.core.common.NetworkTransportState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Device-context "local" variables resolved fresh at every task run, so texts
 * can reference live state: `%DATE`, `%TIME`, `%DATETIME`, `%BATTERY`,
 * `%CHARGING`, `%WIFI`, `%BLUETOOTH`, `%RINGER`, `%SCREEN`, `%AIRPLANE`,
 * `%NETWORK`, `%BRIGHTNESS`, `%BRAND`, `%MODEL`, `%SDK`.
 *
 * Every probe is best-effort: a failure yields an empty map entry rather than
 * a crash, and the whole provider degrades gracefully on unusual ROMs.
 */
object BuiltinVariables {

    /**
     * Probes optional device state (network, bluetooth, battery...). Every read
     * is best-effort and wrapped in try/catch — a missing permission or an
     * unusual ROM simply yields a default value rather than a crash, so the
     * lint guard documents the deliberate, already-guarded probes.
     */
    @SuppressLint("MissingPermission") // guarded probes, see above
    fun provide(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val now = Calendar.getInstance()
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val time = SimpleDateFormat("HH:mm", Locale.US)
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            out["DATE"] = date.format(now.time)
            out["TIME"] = time.format(now.time)
            out["DATETIME"] = dateTime.format(now.time)
        } catch (_: Throwable) {
            // ignore
        }
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager != null) {
                out["BATTERY"] =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
                out["CHARGING"] =
                    if (batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ==
                        BatteryManager.BATTERY_STATUS_CHARGING
                    ) "charging" else "not charging"
            }
        } catch (_: Throwable) {
            // ignore
        }
        try {
            val snapshot = DefaultNetworkStateReader.read(context)
            out["WIFI"] = when (
                DefaultNetworkStateReader.transportState(
                    snapshot,
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            ) {
                NetworkTransportState.CONNECTED -> "on"
                NetworkTransportState.DISCONNECTED -> "off"
                NetworkTransportState.UNKNOWN -> "unknown"
            }
            out["NETWORK"] = when (snapshot) {
                is DefaultNetworkSnapshot.Available -> when {
                    snapshot.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    snapshot.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                    else -> "other"
                }
                DefaultNetworkSnapshot.NoActiveNetwork -> "none"
                DefaultNetworkSnapshot.Unavailable -> "unknown"
            }
        } catch (_: Throwable) {
            // ignore
        }
        try {
            // BluetoothManager exists since API 18 (minSdk 26) and is the
            // canonical lookup on every version — no deprecated service cast.
            val bluetoothAdapter =
                context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            out["BLUETOOTH"] = if (bluetoothAdapter?.isEnabled == true) "on" else "off"
        } catch (_: Throwable) {
            // ignore
        }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            out["RINGER"] = when (audioManager?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
        } catch (_: Throwable) {
            // ignore
        }
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            out["SCREEN"] = if (powerManager?.isInteractive == true) "on" else "off"
        } catch (_: Throwable) {
            // ignore
        }
        try {
            out["AIRPLANE"] = if (Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) == 1
            ) "on" else "off"
            out["BRIGHTNESS"] = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            ).toString()
        } catch (_: Throwable) {
            // ignore
        }
        out["BRAND"] = Build.BRAND
        out["MODEL"] = Build.MODEL
        out["SDK"] = Build.VERSION.SDK_INT.toString()
        return out
    }
}
