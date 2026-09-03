package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.models.TriggerType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone AIRPLANE_MODE trigger: fires a task when airplane mode turns on
 * (or off, per the configured `state`), once per transition, and runs the
 * task's exit behavior when the configured side ends.
 */
@Singleton
class AirplaneModeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on the opposite event). */
    private val activeStates = ConcurrentHashMap<String, Boolean>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                val on = intent.getBooleanExtra("state", false)
                handleState(on)
            }
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        scope.launch {
            // Evaluate every enabled task against the CURRENT airplane state:
            // a task whose condition already holds fires right away, a task
            // disabled while its condition still holds runs its exit behavior,
            // and a condition that ended while the process was down fires its
            // missed exit now instead of waiting for the next mode flip.
            reconcileAutomations()
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun handleState(on: Boolean) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.AIRPLANE_MODE } }
                .forEach { automation ->
                    val wantOn = (automation.triggers.first { it.type == TriggerType.AIRPLANE_MODE }
                        .config["state"] ?: "ON") == "ON"
                    if (on == wantOn) {
                        // Fire once per transition into the triggered state.
                        if (activeStates.put(automation.id, on) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeStates.remove(automation.id) != null) {
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    /**
     * Full re-evaluation of every AIRPLANE_MODE task against the current
     * device state. Invoked on initialize and whenever automations change
     * (enable/disable toggles, saves), so:
     *  - a task enabled while airplane mode already matches fires immediately
     *    instead of waiting for the next mode transition;
     *  - a task disabled while its condition still holds stops being tracked
     *    (its durable mark is pruned) instead of leaking until restart;
     *  - a condition that ended while the process was down fires its missed
     *    exit right away.
     */
    fun reconcileAutomations() {
        scope.launch {
            val on = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
            val automations = repository.getAutomations().first()
            val byId = automations.associateBy { it.id }
            // Restore durable active markers first so a task that fired before
            // a process restart is never fired again while its condition still
            // holds; run the end behavior of tasks disabled or deleted while
            // the process was down.
            // Disabling a task is an explicit abandonment of its lifecycle:
            // the durable mark is pruned without firing a stale exit (the exit
            // contract covers the condition ENDING while the task stays
            // enabled, never a deliberate disable).
            activeStore.activeKeys(SOURCE).forEach { id ->
                val automation = byId[id]
                when {
                    automation?.enabled == true -> activeStates[id] = true
                    else -> {
                        activeStates.remove(id)
                        activeStore.clearAutomation(SOURCE, id)
                    }
                }
            }
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.AIRPLANE_MODE } }
                .forEach { automation ->
                    val wantOn = (automation.triggers.first { it.type == TriggerType.AIRPLANE_MODE }
                        .config["state"] ?: "ON") == "ON"
                    if (on == wantOn) {
                        // Fire now when the condition holds and the task is not
                        // already in its triggered state (once per enablement).
                        if (activeStates.put(automation.id, on) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeStates.remove(automation.id) != null) {
                        // The condition already ended: run the exit behavior.
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "airplane"
    }
}
