package com.nexaflow.core.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexaflow.core.rom.PrivilegedRunner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Location access helpers shared by the trigger editor (foreground "use my
 * current location" flow) and the background periodic checker ([LocationCheckWorker]).
 *
 * Android forbids an app from toggling the system location switch on its own.
 * The SILENT paths here go through the app's existing elevated runtimes:
 * WRITE_SECURE_SETTINGS (granted via adb) or the Shizuku/root shell
 * (`settings put secure location_mode`). When neither is available the caller
 * falls back to opening the system location settings screen for one tap.
 */
object LocationAccess {

    const val MODE_OFF = 0
    const val MODE_SENSORS_ONLY = 1
    const val MODE_BATTERY_SAVING = 2
    const val MODE_HIGH_ACCURACY = 3

    private const val DEFAULT_FIX_TIMEOUT_MS = 12_000L

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    MODE_OFF
                ) != MODE_OFF
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun currentLocationMode(context: Context): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, MODE_OFF)
        } catch (_: Throwable) {
            MODE_OFF
        }
    }

    /**
     * Turns location on without any user interaction, if an elevated runtime is
     * available. Returns true when location is on afterwards (it was already
     * on, or the silent switch succeeded).
     */
    fun enableLocationSilently(context: Context): Boolean {
        if (isLocationEnabled(context)) return true
        return setLocationModeSilently(context, MODE_HIGH_ACCURACY)
    }

    /**
     * Restores [previousMode] only when WE auto-enabled location and the user
     * has not changed the mode meanwhile. Never switches location off when the
     * user toggled it on themselves during the check window.
     */
    fun restoreLocationModeIfWeChanged(context: Context, previousMode: Int) {
        val current = currentLocationMode(context)
        if (previousMode != MODE_HIGH_ACCURACY && current == MODE_HIGH_ACCURACY) {
            setLocationModeSilently(context, previousMode)
        }
    }

    private fun setLocationModeSilently(context: Context, mode: Int): Boolean {
        // Fastest path: WRITE_SECURE_SETTINGS (adb-grantable) writes directly.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return try {
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.LOCATION_MODE, mode)
            } catch (_: Throwable) {
                false
            }
        }
        // Elevated runtime path (Shizuku or root): `settings put secure ...`.
        return PrivilegedRunner.runShell("settings put secure location_mode $mode").success
    }

    /** Opens the system location settings screen (user taps the switch once). */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            // No settings activity (unlikely) — ignore.
        }
    }

    /**
     * Waits for a fresh single-shot location fix (up to [timeoutMs]), preferring
     * enabled providers and falling back to the last known location. Returns
     * null when the permission is missing, providers are all off, or no fix
     * arrives in time.
     */
    // The permission is checked explicitly at the top (hasLocationPermission);
    // lint cannot trace the guard through the coroutine continuation below.
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, timeoutMs: Long = DEFAULT_FIX_TIMEOUT_MS): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val enabledProviders = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                fun cleanUp() {
                    enabledProviders.forEach { provider ->
                        runCatching { manager.removeUpdates(listener) }
                    }
                }

                if (enabledProviders.isEmpty()) {
                    // No live provider: return the most recent cached fix, if any.
                    val last = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
                        .getOrNull()
                        ?: runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }
                            .getOrNull()
                    if (last != null && continuation.isActive) continuation.resume(last) else continuation.cancel()
                    return@suspendCancellableCoroutine
                }

                enabledProviders.forEach { provider ->
                    runCatching {
                        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                }
                continuation.invokeOnCancellation { cleanUp() }
            }
        }
    }
}
