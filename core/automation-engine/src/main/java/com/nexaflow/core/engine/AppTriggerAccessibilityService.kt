package com.nexaflow.core.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppTriggerAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: AutomationRepository

    @Inject
    lateinit var executionEngine: ExecutionEngine

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    private var lastPackage: String? = null
    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently triggered by a foreground app (for exit behavior). */
    private val activeApps = mutableMapOf<String, String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == lastPackage || packageName == this.packageName) return
        lastPackage = packageName

        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.APPLICATION }
                }
                .forEach { automation ->
                    val matches = automation.triggers.any { it.matchesPackage(packageName) }
                    if (matches) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeApps[automation.id] = packageName
                            executionEngine.runAutomation(automation)
                        }
                    } else if (activeApps.remove(automation.id) != null) {
                        // The foreground app changed: the condition ended, fire exit.
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    override fun onInterrupt() = Unit
}

/** True when the app trigger's config lists [packageName] (single or multi-select). */
private fun com.nexaflow.domain.models.Trigger.matchesPackage(packageName: String): Boolean {
    val raw = config["packages"] ?: config["package"] ?: ""
    return raw.split(',').any { it.trim() == packageName }
}
