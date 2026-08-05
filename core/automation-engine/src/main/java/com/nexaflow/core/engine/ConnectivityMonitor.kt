package com.nexaflow.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
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
                handleChange(connected = true)
            }

            override fun onLost(network: Network) {
                handleChange(connected = false)
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

    private fun handleChange(connected: Boolean) {
        scope.launch {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val type = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "MOBILE"
                else -> null
            }
            val state = if (connected) "CONNECTED" else "DISCONNECTED"
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.CONNECTIVITY }
                }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type == TriggerType.CONNECTIVITY }
                    val network = trigger.config["network"] ?: "WIFI"
                    val desiredState = trigger.config["state"] ?: "CONNECTED"
                    // When no network is active (type == null) the only valid
                    // transition is a disconnect, so gate on that.
                    val networkMatch = when {
                        type != null -> network == type
                        state == "DISCONNECTED" -> desiredState == "DISCONNECTED"
                        else -> false
                    }
                    if (networkMatch && state == desiredState) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > COOLDOWN_MS) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = state
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

    companion object {
        private const val COOLDOWN_MS = 5_000L
    }
}
