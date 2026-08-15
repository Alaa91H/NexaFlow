package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nexaflow.core.datastore.ActiveTriggerStore
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) : EventSource {

    override val sourceId: String = TriggerSource.BATTERY.sourceId

    override val description: String = "Battery level & charger events"

    /** Safety-net cadence: re-check the sticky battery state every minute. */
    private val safetyNetIntervalMs: Long = 60_000L

    @Volatile
    private var registered = false

    /**
     * Periodic safety-net job that re-reads the sticky battery state once a
     * minute so a crossed threshold is caught even if the ACTION_BATTERY_CHANGED
     * broadcast was missed (brief suspension, Doze transition).
     */
    private var safetyNetJob: Job? = null

    /** Last battery state we actually evaluated, to skip no-op ticks. */
    @Volatile
    private var lastHandledLevel = -1
    @Volatile
    private var lastHandledStatus = -1
    @Volatile
    private var lastHandledPlugged = -1

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
        scope.launch {
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the sticky battery broadcast is delivered. A task that was
            // triggered before a process/service restart must still fire its
            // exit behavior when the condition ends; if the condition already
            // ended while the process was down, the immediate sticky delivery
            // below catches it (handleBatteryChange sees `hadActive`).
            rearmFromLedger()
            if (!registered) return@launch
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            // ACTION_BATTERY_CHANGED is sticky: registering delivers the current
            // level immediately, so an already-crossed threshold fires right away
            // (e.g. the app starts while the battery is below the target).
            startSafetyNet()
        }
    }

    /**
     * Safety-net tick: re-reads the sticky ACTION_BATTERY_CHANGED state every
     * minute and re-evaluates every battery trigger against it.
     *
     * ACTION_BATTERY_CHANGED only fires when the level/status actually changes,
     * and a broadcast can be missed (the app briefly suspended, a Doze
     * transition, a dropped pending intent), so the pure broadcast path could
     * silently skip a crossed threshold. This loop guarantees the threshold is
     * caught within one minute of being crossed.
     *
     * Battery-conscious by design (per Android background-work guidance):
     *  - The loop piggybacks on the always-running monitoring foreground
     *    service, so it costs no extra wakeups — the device is already awake.
     *  - Reading the sticky broadcast is a single binder call; no IPC to
     *    another process, no battery-service query.
     *  - When level/status/plugged are identical to the last evaluated state
     *    (the overwhelming common case minute-to-minute), the tick skips the
     *    re-evaluation entirely — no database read, no CPU work.
     *  - Newly saved triggers are handled by [refresh] from the builder, so an
     *    unchanged battery can never miss a fresh threshold.
     */
    private fun startSafetyNet() {
        if (safetyNetJob?.isActive == true) return
        safetyNetJob = scope.launch {
            while (isActive) {
                delay(safetyNetIntervalMs)
                val sticky = runCatching {
                    context.registerReceiver(
                        null,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    )
                }.getOrNull() ?: continue
                val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val status = sticky.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                if (level == lastHandledLevel &&
                    status == lastHandledStatus &&
                    plugged == lastHandledPlugged
                ) {
                    // Nothing changed since the last broadcast — nothing to catch.
                    continue
                }
                handleBatteryChange(level, status, plugged)
            }
        }
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
        safetyNetJob?.cancel()
        safetyNetJob = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Restores the durable active keys into the in-memory set before the sticky
     * broadcast is delivered. Keys for automations that were deleted or disabled
     * while the process was down are pruned so a stale mark can never fire a
     * stale exit. Composite keys (`id|plugType`) collapse to the plain id — the
     * exit check matches either form.
     */
    private suspend fun rearmFromLedger() {
        val enabledIds = repository.getAutomations().first()
            .filter { it.enabled }
            .map { it.id }
            .toSet()
        activeStore.activeKeys(sourceId).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeBatteryTriggers.add(id)
            } else {
                activeStore.clearAutomation(sourceId, id)
            }
        }
    }

    private fun handleBatteryChange(level: Int, status: Int, plugged: Int) {
        if (level <= 0) return
        // Record the state we evaluated so the safety-net loop can skip no-op
        // ticks (identical state = no broadcast missed = nothing to catch).
        lastHandledLevel = level
        lastHandledStatus = status
        lastHandledPlugged = plugged
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
                            activeStore.markActive(sourceId, key)
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
                            activeStore.clearAutomation(sourceId, automation.id)
                            executionEngine.runExit(automation)
                        }
                    }
                }

                // Standalone charger trigger: fires when charging starts or
                // ends (any plug type), once per transition, and runs the exit
                // behavior when the configured side ends.
                val chargerTrigger = automation.triggers.firstOrNull {
                    it.type == TriggerType.CHARGER
                }
                if (chargerTrigger != null) {
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    val wantConnected = (chargerTrigger.config["event"] ?: "CONNECTED") == "CONNECTED"
                    val chargerKey = "${automation.id}|charger"
                    val now = System.currentTimeMillis()
                    if (charging == wantConnected) {
                        if (activeBatteryTriggers.add(chargerKey)) {
                            val last = lastRunAt[automation.id] ?: 0L
                            if (now - last > automation.cooldownMillis) {
                                lastRunAt[automation.id] = now
                                activeStore.markActive(sourceId, chargerKey)
                                executionEngine.runAutomation(automation)
                            }
                        }
                    } else if (activeBatteryTriggers.remove(chargerKey)) {
                        activeStore.clearAutomation(sourceId, automation.id)
                        executionEngine.runExit(automation)
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
