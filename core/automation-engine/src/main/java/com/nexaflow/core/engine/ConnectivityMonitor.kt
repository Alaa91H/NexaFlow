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

    fun initialize() {
        if (initialized) return
        initialized = true
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleChange(connected = true)
            }

            override fun onLost(network: Network) {
                handleChange(connected = false)
            }
        })
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
            if (type == null) return@launch
            val state = if (connected) "CONNECTED" else "DISCONNECTED"
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { trigger ->
                        trigger.type == TriggerType.CONNECTIVITY &&
                            trigger.config["network"] == type &&
                            trigger.config["state"] == state
                    }
                }
                .forEach { automation ->
                    val last = lastRunAt[automation.id] ?: 0L
                    if (now - last > COOLDOWN_MS) {
                        lastRunAt[automation.id] = now
                        executionEngine.runAutomation(automation)
                    }
                }
        }
    }

    companion object {
        private const val COOLDOWN_MS = 5_000L
    }
}
