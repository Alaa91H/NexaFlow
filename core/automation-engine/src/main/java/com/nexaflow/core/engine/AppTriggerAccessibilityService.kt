package com.nexaflow.core.engine

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
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
                    automation.enabled && automation.triggers.any { trigger ->
                        trigger.type == TriggerType.APPLICATION && trigger.config["package"] == packageName
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

    override fun onInterrupt() = Unit

    companion object {
        private const val COOLDOWN_MS = 10_000L
    }
}
