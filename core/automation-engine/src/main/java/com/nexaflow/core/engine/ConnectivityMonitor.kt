package com.nexaflow.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.nexaflow.core.common.CellularNetworkReader
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var initialized = false

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    /** Automations currently in their triggered state, mapped to the active state string. */
    private val activeStates = ConcurrentHashMap<String, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    // Telephony callbacks are delivered off the main thread. Their callbacks
    // only schedule a full condition read on the application scope.
    private var telephonyExecutor: ExecutorService = newTelephonyExecutor()
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var legacyTelephonyListener: PhoneStateListener? = null

    @Volatile
    private var latestDisplayInfo: TelephonyDisplayInfo? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        if (telephonyExecutor.isShutdown) {
            telephonyExecutor = newTelephonyExecutor()
        }
        scope.launch {
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the network callback is registered: registering delivers the
            // current network immediately, and that first handleChange must
            // see the restored active states so a condition that already ended
            // while the process was down fires its exit behavior right away.
            rearmFromLedger()
            if (!initialized) return@launch

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                initialized = false
                return@launch
            }
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleChange()
                }

                override fun onLost(network: Network) {
                    handleChange()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    // A 4G/5G transition can be reported as a capability change
                    // while the default mobile network remains available.
                    handleChange()
                }
            }
            callback = networkCallback
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            }
            registerTelephonyCallbacks()
            handleChange()
        }
    }

    /**
     * Restores the durable active keys into the in-memory map before the first
     * network callback. Keys carry the desired state (`id|CONNECTED`), so the
     * restored entry matches the monitor's exit check exactly. Stale keys for
     * deleted/disabled automations are pruned so they can never fire a stale
     * exit.
     */
    private suspend fun rearmFromLedger() {
        val enabledIds = repository.getAutomations().first()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeStates[id] = key.substringAfter('|', "CONNECTED")
            } else {
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    private companion object {
        const val SOURCE = "connectivity"
    }

    fun stop() {
        if (!initialized) return
        initialized = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        callback?.let { c ->
            runCatching { connectivityManager?.unregisterNetworkCallback(c) }
        }
        callback = null
        unregisterTelephonyCallbacks()
        activeStates.clear()
        lastRunAt.clear()
        telephonyExecutor.shutdownNow()
    }

    private fun newTelephonyExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NexaFlow-telephony").apply { isDaemon = true }
        }

    /**
     * Uses TelephonyCallback on Android 12+ and the public PhoneStateListener
     * callbacks on older releases. Registration failures are expected on
     * devices without phone permission or telephony hardware and are ignored;
     * the synchronous reader still degrades to null.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun registerTelephonyCallbacks() {
        if (!initialized) return
        unregisterTelephonyCallbacks()
        val manager = CellularNetworkReader.telephonyForDefaultDataSubscription(context)
            ?: return
        telephonyManager = manager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.ActiveDataSubscriptionIdListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    latestDisplayInfo = telephonyDisplayInfo
                    handleChange()
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    handleChange()
                }

                override fun onActiveDataSubscriptionIdChanged(subId: Int) {
                    latestDisplayInfo = null
                    registerTelephonyCallbacks()
                    handleChange()
                }
            }
            if (runCatching {
                    manager.registerTelephonyCallback(telephonyExecutor, callback)
                }.isSuccess
            ) {
                telephonyCallback = callback
                return
            }
        }

        val listener = object : PhoneStateListener() {
            override fun onServiceStateChanged(serviceState: ServiceState) {
                handleChange()
            }

            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                handleChange()
            }

            override fun onActiveDataSubscriptionIdChanged(subId: Int) {
                latestDisplayInfo = null
                registerTelephonyCallbacks()
                handleChange()
            }
        }
        var listenFlags = PhoneStateListener.LISTEN_SERVICE_STATE or
            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listenFlags = listenFlags or PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE
        }
        if (runCatching { manager.listen(listener, listenFlags) }.isSuccess) {
            legacyTelephonyListener = listener
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun unregisterTelephonyCallbacks() {
        val manager = telephonyManager
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { callback ->
                    runCatching { manager.unregisterTelephonyCallback(callback) }
                }
            }
            legacyTelephonyListener?.let { listener ->
                runCatching { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
            }
        }
        telephonyCallback = null
        legacyTelephonyListener = null
        telephonyManager = null
        latestDisplayInfo = null
    }

    private fun handleChange() {
        if (!initialized) return
        scope.launch {
            if (!initialized) return@launch
            // Every network condition is re-read from live state (capabilities,
            // tether setting, cellular generation) on each callback, so stale
            // callbacks self-correct.
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any {
                        it.type == TriggerType.CONNECTIVITY || it.type == TriggerType.NETWORK_MODE
                    }
                }
                .forEach { automation ->
                    // A task may use either the combined CONNECTIVITY trigger or the
                    // standalone NETWORK_MODE trigger (cellular generation).
                    val trigger = automation.triggers.firstOrNull {
                        it.type == TriggerType.CONNECTIVITY || it.type == TriggerType.NETWORK_MODE
                    } ?: return@forEach
                    val network = trigger.config["network"]
                        ?: if (trigger.type == TriggerType.NETWORK_MODE) "NETWORK_MODE" else "WIFI"
                    val desiredState = trigger.config["state"] ?: "CONNECTED"
                    val current = currentNetworkValue(network, capabilities)
                    val matched = if (network == "NETWORK_MODE") {
                        CellularNetworkReader.matchesNetworkMode(desiredState, current)
                    } else {
                        current == desiredState
                    }
                    if (matched) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = desiredState
                            activeStore.markActive(SOURCE, "${automation.id}|$desiredState")
                            executionEngine.runAutomation(automation)
                        }
                    } else if (current != null && activeStates[automation.id] == desiredState) {
                        // A known non-matching value ends the condition. An
                        // unreadable cellular generation is deliberately not an
                        // exit event, otherwise a transient permission/OEM read
                        // failure could run exit actions incorrectly.
                        activeStates.remove(automation.id)
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    /**
     * The current value of the monitored connectivity condition, in the same
     * vocabulary the trigger config uses: CONNECTED/DISCONNECTED for WIFI and
     * MOBILE, ON/OFF for the hotspot, and the cellular generation (2G/3G/4G/5G)
     * for NETWORK_MODE. Null means the condition is not applicable or could not
     * be read; it never becomes AUTO implicitly.
     */
    private fun currentNetworkValue(
        network: String,
        capabilities: NetworkCapabilities?
    ): String? = when (network) {
        "WIFI" -> when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "CONNECTED"
            else -> "DISCONNECTED"
        }
        "MOBILE" -> when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CONNECTED"
            else -> "DISCONNECTED"
        }
        "HOTSPOT" -> runCatching {
            Settings.Global.getInt(context.contentResolver, "tether_on", 0) == 1
        }.getOrDefault(false).let { if (it) "ON" else "OFF" }
        "NETWORK_MODE" -> {
            // The cellular registration can remain active while Wi-Fi is the
            // default route; do not gate the telephony read on the currently
            // active transport or a 4G/5G transition will be missed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CellularNetworkReader.read(context, latestDisplayInfo)
            } else {
                CellularNetworkReader.read(context)
            }
        }
        else -> null
    }
}

/**
 * Compatibility entry point for callers that only need a one-shot read. The
 * actual implementation lives in core:common so the monitor, manual gate and
 * editor share exactly the same semantics.
 */
fun currentCellularGeneration(context: Context): String? =
    CellularNetworkReader.read(context)

/** Maps a TelephonyManager network type to the trigger vocabulary. */
internal fun cellularGenerationOf(networkType: Int): String? =
    CellularNetworkReader.generationOf(networkType)
