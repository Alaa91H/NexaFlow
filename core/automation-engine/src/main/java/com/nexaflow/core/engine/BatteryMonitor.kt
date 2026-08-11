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
import com.nexaflow.domain.models.cooldownMillis
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

    /**
     * Automation ids that already fired their low-battery / charge-complete
     * alert, so each automation fires once per crossing instead of on every
     * broadcast while the condition still holds. Per-automation (not shared
     * booleans) so one task's alert never suppresses another's.
     */
    private val alertedLowIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val alertedChargeCompleteIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
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
        // ACTION_BATTERY_CHANGED is sticky: registering delivers the current
        // level immediately, so an already-crossed threshold fires right away
        // (e.g. the app starts while the battery is below the target).
    }

    /**
     * Re-evaluates every battery trigger against the CURRENT battery state.
     *
     * ACTION_BATTERY_CHANGED only fires when the level/status actually changes,
     * so a freshly saved task whose threshold is already crossed (battery is
     * steady below the target, charger already at the right type) would never
     * run until the battery moved again. The builder calls this right after
     * saving so the condition is checked immediately.
     */
    fun refresh() {
        scope.launch {
            // Read the latest sticky state (no new broadcast needed).
            val sticky = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            if (sticky == null) return@launch
            handleBatteryChange(
                sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1),
                sticky.getIntExtra(
                    android.os.BatteryManager.EXTRA_STATUS,
                    android.os.BatteryManager.BATTERY_STATUS_UNKNOWN
                ),
                sticky.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
            )
        }
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
                    // Charging state is derived from the battery status (charging
                    // or full = charging), independent of the plug mask — a
                    // full battery still counts as "charging" so CHARGING-state
                    // triggers stay satisfied while on the charger.
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    val active = BatteryTriggerMatcher.isActive(config, level, plugged, charging)
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
                        val last = lastRunAt[automation.id] ?: 0L
                        val now = System.currentTimeMillis()
                        if (activeBatteryTriggers.add(key) && now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
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
                        // add() returns true only the first time: fires once per
                        // automation per low-battery crossing.
                        if (alertedLowIds.add(automation.id)) {
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedLowIds.remove(automation.id)
                    }
                }

                if (chargeAction != null) {
                    if (status == BatteryManager.BATTERY_STATUS_FULL) {
                        if (alertedChargeCompleteIds.add(automation.id)) {
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedChargeCompleteIds.remove(automation.id)
                    }
                }
            }
        }
    }
}
