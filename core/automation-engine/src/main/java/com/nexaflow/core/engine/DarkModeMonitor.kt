package com.nexaflow.core.engine

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
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
 * Standalone DARK_MODE trigger: fires a task when the system dark theme turns
 * on (or off, per the configured `state`), once per transition, and runs the
 * task's exit behavior when the configured side ends.
 */
@Singleton
class DarkModeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val exitCoordinator: ExitExecutionCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on the opposite event). */
    private val activeStates = mutableMapOf<String, Boolean>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                handleState(isDarkMode())
            }
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
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

    private fun isDarkMode(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES ||
            (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun handleState(dark: Boolean) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.DARK_MODE } }
                .forEach { automation ->
                    val wantDark = (automation.triggers.first { it.type == TriggerType.DARK_MODE }
                        .config["state"] ?: "ON") == "ON"
                    if (dark == wantDark) {
                        // Fire once per transition into the triggered state.
                        if (activeStates.put(automation.id, dark) == null) {
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
        val dark = isDarkMode()
        val automations = repository.getAutomations().first()
        automations
            .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.DARK_MODE } }
            .forEach { automation ->
                val wantDark = (automation.triggers.first { it.type == TriggerType.DARK_MODE }
                    .config["state"] ?: "ON") == "ON"
                val wasActive = activeStates[automation.id] ?: return@forEach
                if (wasActive != (dark == wantDark)) {
                    activeStates.remove(automation.id)
                    exitCoordinator.submit(SOURCE, automation)
                }
            }
    }

    private companion object {
        const val SOURCE = "dark-mode"
    }
}
