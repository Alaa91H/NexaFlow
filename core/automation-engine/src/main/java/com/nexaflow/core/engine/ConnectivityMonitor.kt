package com.nexaflow.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.NetworkRegistrationInfo
import android.telephony.TelephonyManager
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var initialized = false

    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered state, mapped to the active state string. */
    private val activeStates = mutableMapOf<String, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun initialize() {
        if (initialized) return
        initialized = true
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
    }

    private fun handleChange() {
        scope.launch {
            // Every network condition is re-read from live state (capabilities,
            // tether setting, cellular generation) on each callback, so stale
            // callbacks self-correct.
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
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
                    // NETWORK_MODE with AUTO matches any active cellular
                    // generation; every other combination is an exact match.
                    val matched = if (network == "NETWORK_MODE" && desiredState == "AUTO") {
                        current != null
                    } else {
                        current == desiredState
                    }
                    if (matched) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = desiredState
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeStates[automation.id] == desiredState) {
                        // The condition ended (state flipped or network lost): fire exit.
                        activeStates.remove(automation.id)
                        executionEngine.runExit(automation)
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
        "HOTSPOT" -> runCatching {
            Settings.Global.getInt(context.contentResolver, "tether_on", 0) == 1
        }.getOrDefault(false).let { if (it) "ON" else "OFF" }
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
fun currentCellularGeneration(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Requires READ_PHONE_STATE on API 31+; degrade gracefully when the
        // permission is missing and rely on the network type instead.
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

/** Maps a TelephonyManager network type to its generation (2G/3G/4G/5G). */
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
