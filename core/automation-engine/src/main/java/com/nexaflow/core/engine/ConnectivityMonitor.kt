package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.NetworkRegistrationInfo
import android.telephony.TelephonyManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Serializes state transitions so duplicate network callbacks cannot re-run one task. */
    private val transitionMutex = Mutex()
    /** Automations currently in their triggered state, mapped to the active state string. */
    private val activeStates = mutableMapOf<String, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    /** Last definitive tethering state reported by the Wi‑Fi AP state broadcast. */
    @Volatile private var hotspotEnabledHint: Boolean? = null
    private val hotspotStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == HOTSPOT_STATE_CHANGED_ACTION) {
                onHotspotStateChanged(intent.getIntExtra(HOTSPOT_STATE_EXTRA, HOTSPOT_STATE_UNAVAILABLE))
            }
        }
    }

    /** Receives the AP state carried by the system tethering broadcast. */
    internal fun onHotspotStateChanged(state: Int) {
        hotspotEnabledHint = when (state) {
            HOTSPOT_STATE_ENABLED -> true
            HOTSPOT_STATE_DISABLED,
            HOTSPOT_STATE_DISABLING,
            HOTSPOT_STATE_FAILED -> false
            else -> null
        }
        handleChange()
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        scope.launch {
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the network callback is registered: registering delivers the
            // current network immediately, and that first handleChange must
            // see the restored active states so a condition that already ended
            // while the process was down fires its exit behavior right away.
            rearmFromLedger()
            if (!initialized) return@launch
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleChange()
                }

                override fun onLost(network: Network) {
                    handleChange()
                }
            }
            callback = networkCallback
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            ContextCompat.registerReceiver(
                context,
                hotspotStateReceiver,
                IntentFilter(HOTSPOT_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
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
        // Public broadcast action delivered when local Wi‑Fi tethering changes.
        // Kept as a literal because SDK stubs do not expose the hidden constant.
        const val HOTSPOT_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val HOTSPOT_STATE_EXTRA = "wifi_state"
        const val HOTSPOT_STATE_UNAVAILABLE = -1
        const val HOTSPOT_STATE_DISABLED = 11
        const val HOTSPOT_STATE_DISABLING = 10
        const val HOTSPOT_STATE_ENABLED = 13
        const val HOTSPOT_STATE_FAILED = 14
    }

    fun stop() {
        if (!initialized) return
        initialized = false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback?.let { c ->
            try {
                connectivityManager.unregisterNetworkCallback(c)
            } catch (_: Throwable) {
                // ignore
            }
        }
        callback = null
        runCatching { context.unregisterReceiver(hotspotStateReceiver) }
    }

    private fun handleChange() {
        scope.launch {
            transitionMutex.withLock {
                // Every condition is re-read from live state (capabilities,
                // tether setting, cellular generation) on each callback, so
                // stale callbacks self-correct. A state task runs exactly once
                // when it enters the requested state, not on every callback.
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                val automations = repository.getAutomations().first()
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
                        // NETWORK_MODE with AUTO matches any active cellular
                        // generation; every other combination is an exact match.
                        val matched = if (network == "NETWORK_MODE" && desiredState == "AUTO") {
                            current != null
                        } else {
                            current == desiredState
                        }
                        if (matched && activeStates[automation.id] != desiredState) {
                            activeStates[automation.id] = desiredState
                            activeStore.markActive(SOURCE, "$automation.id|$desiredState")
                            executionEngine.runAutomation(automation)
                        } else if (!matched && activeStates[automation.id] == desiredState) {
                            // The condition ended (state flipped or network lost): fire exit once.
                            activeStates.remove(automation.id)
                            activeStore.clearAutomation(SOURCE, automation.id)
                            executionEngine.runExit(automation)
                        }
                    }
            }
        }
    }

    /**
     * The current value of the monitored connectivity condition, in the same
     * vocabulary the trigger config uses: CONNECTED/DISCONNECTED for WIFI and
     * MOBILE, ON/OFF for the hotspot, and the cellular generation (2G/3G/4G/5G,
     * or AUTO when the generation cannot be read) for NETWORK_MODE. Null means
     * the condition is not applicable right now (e.g. no cellular for modes).
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
        "HOTSPOT" -> (
            hotspotEnabledHint ?: runCatching {
                Settings.Global.getInt(context.contentResolver, "tether_on", 0) == 1
            }.getOrDefault(false)
        ).let { if (it) "ON" else "OFF" }
        "NETWORK_MODE" -> {
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) return null
            currentCellularGeneration(context) ?: "AUTO"
        }
        else -> null
    }
}

/**
 * The generation the device is genuinely on. The ServiceState packet-switched
 * registration is checked first — on 5G NSA (LTE-anchored) networks the legacy
 * [TelephonyManager.getNetworkType] still reports LTE even though data runs on
 * NR, and the PS/NR registration is the only signal that reflects real 5G on
 * modern devices. Falls back to [TelephonyManager.getNetworkType] (per the
 * requested source; more representative on dual-SIM and hybrid-voice setups)
 * for everything else. Returns null when telephony is unavailable.
 */
// Every privileged read below is wrapped in runCatching and degrades to the
// legacy network type (or null) when the READ_PHONE_STATE/READ_BASIC_PHONE_STATE
// grant is missing — lint cannot prove that, so the calls are suppressed here.
@Suppress("MissingPermission")
fun currentCellularGeneration(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Requires READ_PHONE_STATE on API 31+; degrade gracefully when the
        // permission is missing and rely on the network type instead.
        // (getNetworkRegistrationInfoList and the access-technology getters
        // require API 30, hence the R guard.)
        val serviceState = runCatching { telephony.serviceState }.getOrNull()
        val onReal5g = serviceState?.networkRegistrationInfoList.orEmpty().any { info ->
            info.domain == NetworkRegistrationInfo.DOMAIN_PS &&
                info.accessNetworkTechnology == ACCESS_NETWORK_TECHNOLOGY_NR
        }
        if (onReal5g) return "5G"
    }
    @Suppress("DEPRECATION")
    return cellularGenerationOf(telephony.networkType)
}

/** NetworkRegistrationInfo.ACCESS_NETWORK_TECHNOLOGY_NR (AOSP-stable value). */
internal const val ACCESS_NETWORK_TECHNOLOGY_NR = 20

/**
 * Maps a TelephonyManager network type to its generation (2G/3G/4G/5G).
 *
 * The deprecated constants below are the only public identifiers for legacy
 * radio technologies and are retained solely to classify old devices.
 */
@Suppress("DEPRECATION")
internal fun cellularGenerationOf(networkType: Int): String? = when (networkType) {
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
    TelephonyManager.NETWORK_TYPE_UMTS,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_HSPAP,
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
    TelephonyManager.NETWORK_TYPE_LTE,
    TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
    TelephonyManager.NETWORK_TYPE_NR -> "5G"
    // Unknown / not available: report AUTO so an AUTO trigger matches.
    else -> "AUTO"
}
