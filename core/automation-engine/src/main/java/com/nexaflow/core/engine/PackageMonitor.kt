package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone APP_INSTALLED trigger: fires a task when a package is INSTALLED,
 * REMOVED or UPDATED (per the configured `event` and optional `package`
 * filter). Transient events — the task's exit behavior runs when the package
 * is removed after an install-fire, or installed after a remove-fire.
 */
@Singleton
class PackageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val pluginDiscoveryRegistry: PluginDiscoveryRegistry,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire the opposite event). */
    private val activeStates = mutableMapOf<String, String>()

    private val oppositeEvent = mapOf(
        "INSTALLED" to "REMOVED",
        "REMOVED" to "INSTALLED"
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) "UPDATED" else "INSTALLED"
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) "UPDATED" else "REMOVED"
                }
                else -> return
            }
            val pkg = intent.data?.schemeSpecificPart ?: return
            // Reuse this existing package lifecycle source: registry discovery
            // remains lazy and performs no background scan until a consumer asks.
            pluginDiscoveryRegistry.invalidate()
            handleEvent(event, pkg)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
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

    private fun handleEvent(event: String, pkg: String) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.APP_INSTALLED } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type == TriggerType.APP_INSTALLED }
                    val want = trigger.config["event"] ?: "INSTALLED"
                    val filterPkg = trigger.config["package"]?.takeIf { it.isNotBlank() }
                    if (filterPkg != null && filterPkg != pkg) return@forEach
                    if (event == want) {
                        if (activeStates.put(automation.id, event) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (oppositeEvent[event] == want && activeStates.remove(automation.id) != null) {
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "package"
    }
}
