package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
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
 * filter). These are momentary events with no reliable lifecycle-end callback,
 * so each matching execution closes its own end behavior immediately.
 */
@Singleton
class PackageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val pluginDiscoveryRegistry: PluginDiscoveryRegistry,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Per-task de-duplication for package broadcasts emitted in a short burst. */
    private val lastRunAt = mutableMapOf<String, Long>()

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
            val now = System.currentTimeMillis()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.APP_INSTALLED } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type == TriggerType.APP_INSTALLED }
                    val want = trigger.config["event"] ?: "INSTALLED"
                    val filterPkg = trigger.config["package"]?.takeIf { it.isNotBlank() }
                    if (filterPkg != null && filterPkg != pkg) return@forEach
                    if (event == want) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            executionEngine.runAutomation(
                                automation = automation,
                                completeExitOnFinish = true
                            )
                        }
                    }
                }
        }
    }

}
