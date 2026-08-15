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
    private val activeStates = mutableMapOf<String, Boolean>()

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
            // Fire the missed exit NOW for any task whose triggered condition
            // already ended while the process was down.
            reconcileWithDeviceState()
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

    private suspend fun reconcileWithDeviceState() {
        val on = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.AIRPLANE_MODE } }
            .forEach { automation ->
                val wantOn = (automation.triggers.first { it.type == TriggerType.AIRPLANE_MODE }
                    .config["state"] ?: "ON") == "ON"
                val wasActive = activeStates[automation.id] ?: return@forEach
                if (wasActive != (on == wantOn)) {
                    activeStates.remove(automation.id)
                    activeStore.clearAutomation(SOURCE, automation.id)
                    executionEngine.runExit(automation)
                }
            }
    }

    private companion object {
        const val SOURCE = "airplane"
    }
}
