package com.nexaflow.core.common

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import android.provider.Settings
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Authoritative reader for the internet hotspot (Wi-Fi tethering) state.
 *
 * Android 16/API 36 introduced the public [TetheringManager] callback that
 * reports the currently tethered interfaces immediately on registration and
 * thereafter on every change. Android 17/API 37 retains that contract. The
 * old `Settings.Global.tether_on` key is not a public API and is absent or
 * stale on several current OEM builds, so it is only a compatibility fallback
 * when the callback is not available.
 */
object HotspotStateReader {

    @Volatile
    private var callbackState: Boolean? = null

    /**
     * Returns ON/OFF only when the platform supplied a trustworthy value. A
     * null result means unknown; callers must never reinterpret it as OFF.
     */
    fun currentState(context: Context): Boolean? {
        callbackState?.let { return it }
        // On API 36+ never fall back to the undocumented tether_on setting:
        // a missing callback sample is UNKNOWN, not a trustworthy OFF/ON value.
        return if (Build.VERSION.SDK_INT < API_TETHERING_CALLBACK) legacyState(context) else null
    }

    /**
     * Performs an independent one-shot platform read. Unlike [currentState],
     * this does not depend on ConnectivityMonitor having already populated the
     * process cache, so post-action verification remains valid during cold
     * starts and immediately after process recovery.
     */
    @SuppressLint("MissingPermission")
    suspend fun freshState(context: Context, timeoutMs: Long = FRESH_READ_TIMEOUT_MS): Boolean? {
        if (Build.VERSION.SDK_INT < API_TETHERING_CALLBACK) return legacyState(context)
        return withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            freshStateApi36(context)
        }
    }

    @TargetApi(API_TETHERING_CALLBACK)
    private suspend fun freshStateApi36(context: Context): Boolean? =
        suspendCancellableCoroutine { continuation ->
            val manager = context.getSystemService(TetheringManager::class.java)
            if (manager == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val finished = AtomicBoolean(false)
            lateinit var callback: TetheringManager.TetheringEventCallback

            fun finish(value: Boolean?) {
                if (!finished.compareAndSet(false, true)) return
                runCatching { manager.unregisterTetheringEventCallback(callback) }
                if (continuation.isActive) continuation.resume(value)
            }

            callback = object : TetheringManager.TetheringEventCallback {
                override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                    val value = hasWifiTetheringInterface(interfaces.map { it.type })
                    callbackState = value
                    finish(value)
                }
            }

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    runCatching { manager.unregisterTetheringEventCallback(callback) }
                }
            }

            runCatching {
                manager.registerTetheringEventCallback(DIRECT_EXECUTOR, callback)
            }.onFailure {
                finish(null)
            }
        }

    /**
     * Begins Android 16+/17+ observation. The callback is invoked once with
     * the current tethered-interface set, then every time that set changes.
     *
     * ACCESS_NETWORK_STATE is a normal permission, declared by the app. The
     * caller owns the returned registration and must close it when monitoring
     * stops to avoid retaining a service instance.
     */
    @SuppressLint("MissingPermission")
    fun observe(
        context: Context,
        executor: Executor,
        onStateChanged: () -> Unit
    ): AutoCloseable? {
        if (Build.VERSION.SDK_INT < API_TETHERING_CALLBACK) return null
        return observeApi36(context, executor, onStateChanged)
    }

    @TargetApi(API_TETHERING_CALLBACK)
    private fun observeApi36(
        context: Context,
        executor: Executor,
        onStateChanged: () -> Unit
    ): AutoCloseable? {
        val manager = context.getSystemService(TetheringManager::class.java) ?: return null
        val callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                // Interface names are vendor-specific. Type is the stable
                // platform contract, so only an explicitly Wi-Fi tethered
                // interface means that the internet hotspot is ON.
                callbackState = hasWifiTetheringInterface(interfaces.map { it.type })
                onStateChanged()
            }
        }
        return runCatching {
            manager.registerTetheringEventCallback(executor, callback)
            AutoCloseable {
                runCatching { manager.unregisterTetheringEventCallback(callback) }
            }
        }.getOrNull()
    }

    /** Internal pure seam for unit tests and callback payload validation. */
    internal fun hasWifiTetheringInterface(interfaceTypes: Iterable<Int>): Boolean =
        interfaceTypes.any { it == TetheringManager.TETHERING_WIFI }

    /** Compatibility-only fallback for devices before API 36. */
    private fun legacyState(context: Context): Boolean? = runCatching {
        when (Settings.Global.getString(context.contentResolver, LEGACY_TETHER_KEY)) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }.getOrNull()

    fun clear() {
        callbackState = null
    }

    private val DIRECT_EXECUTOR = Executor { command -> command.run() }
    private const val FRESH_READ_TIMEOUT_MS = 2_000L
    private const val LEGACY_TETHER_KEY = "tether_on"
    private const val API_TETHERING_CALLBACK = 36
}
