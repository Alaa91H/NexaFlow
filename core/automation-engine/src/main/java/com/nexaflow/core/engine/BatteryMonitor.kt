package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.compat.EventSource
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.schedule.BatteryTriggerMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    @ApplicationScope private val scope: CoroutineScope
) : EventSource {

    override val sourceId: String = TriggerSource.BATTERY.sourceId

    override val description: String = "Battery level & charger events"

    @Volatile
    private var registered = false

    private var alertedLow = false
    private var alertedChargeComplete = false
    /**
     * Active battery-trigger keys. Level-only triggers (chargerType = ANY) use
     * the plain automation id; charger-specific triggers use "automationId|plugType"
     * so switching chargers re-fires. Thread-safe: battery broadcasts are
     * handled concurrently on the application scope.
     */
    private val activeBatteryTriggers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val lastRunAt = mutableMapOf<String, Long>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            handleBatteryChange(level, status, plugged)
        }
    }

    override fun start() = initialize()

    fun initialize() {
        if (registered) return
        registered = true
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun handleBatteryChange(level: Int, status: Int, plugged: Int) {
        if (level <= 0) return
        scope.launch {
            val automations = repository.getAutomations().first()
            automations.filter { it.enabled }.forEach { automation ->
                // Battery trigger: fire when the level crosses the configured
                // threshold (ABOVE or BELOW) AND the charger type matches
                // (AC / USB / WIRELESS / ANY). The active key includes the plug
                // type so switching chargers (e.g. USB → wireless) re-fires.
                val batteryTrigger = automation.triggers.firstOrNull {
                    it.type == TriggerType.BATTERY
                }
                if (batteryTrigger != null) {
                    val config = batteryTrigger.config
                    val plugType = BatteryTriggerMatcher.plugTypeName(plugged)
                    val active = BatteryTriggerMatcher.isActive(config, level, plugged)
                    // Level-only triggers keep one key per automation so they fire
                    // once per crossing; charger-specific triggers key by plug type
                    // so switching chargers (e.g. USB → wireless) re-fires.
                    val key =
                        if (BatteryTriggerMatcher.configuredChargerType(config) == BatteryTriggerMatcher.CHARGER_ANY) {
                            automation.id
                        } else {
                            "${automation.id}|$plugType"
                        }
                    if (active) {
                        if (activeBatteryTriggers.add(key)) {
                            lastRunAt[automation.id] = System.currentTimeMillis()
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        val prefix = "${automation.id}|"
                        val hadActive = activeBatteryTriggers.any {
                            it == automation.id || it.startsWith(prefix)
                        }
                        if (hadActive) {
                            activeBatteryTriggers.removeAll {
                                it == automation.id || it.startsWith(prefix)
                            }
                            executionEngine.runExit(automation)
                        }
                    }
                }

                val alertAction = automation.actions.firstOrNull {
                    it.type == ActionType.BATTERY_ALERTS
                }
                val chargeAction = automation.actions.firstOrNull {
                    it.type == ActionType.BATTERY_CHARGING_NOTIFICATIONS
                }

                if (alertAction != null) {
                    val threshold = alertAction.config["below"]?.toIntOrNull() ?: 20
                    if (level <= threshold) {
                        if (!alertedLow) {
                            alertedLow = true
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedLow = false
                    }
                }

                if (chargeAction != null) {
                    if (status == BatteryManager.BATTERY_STATUS_FULL) {
                        if (!alertedChargeComplete) {
                            alertedChargeComplete = true
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedChargeComplete = false
                    }
                }
            }
        }
    }
}
