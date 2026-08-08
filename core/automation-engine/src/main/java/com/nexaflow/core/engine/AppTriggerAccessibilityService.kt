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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Detects when the user opens an app and drives the "active while the app
 * stays in the foreground" lifecycle of app-triggered tasks:
 *
 *  - a task fires when one of its apps comes to the foreground;
 *  - it stays active (its toggles remain applied) as long as that app keeps
 *    the foreground, through overlays like the notification shade, permission
 *    dialogs and the keyboard ([AppForegroundRules]);
 *  - when the app actually leaves the foreground, the task's end options run
 *    ([ExecutionEngine.runExit]) — restore original state, per-action end
 *    behavior or the configured exit actions.
 */
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

    private val tracker = AppForegroundTracker()

    // Accessibility events arrive on the service's main thread and each one
    // spawns a coroutine; serializing keeps two rapid foreground switches from
    // racing the tracker's maps (double Run / lost Exit).
    private val eventMutex = Mutex()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        // System chrome (shade, keyboards, dialogs) overlays the current app
        // without ending its session — never treat it as a foreground change.
        if (!AppForegroundRules.isForegroundPackage(packageName)) return
        if (packageName == lastPackage) return
        lastPackage = packageName

        scope.launch {
            eventMutex.withLock {
                val automations = repository.getAutomations().first()
                val byId = automations.associateBy { it.id }
                // All app-trigger automations are tracked (not just enabled ones)
                // so a task disabled mid-session still fires its end options on
                // the next foreground change. The enabled gate lives in `matches`.
                val tasks = automations
                    .filter { it.triggers.any { trigger -> trigger.type == TriggerType.APPLICATION } }
                    .map { AppForegroundTracker.Task(it.id, it.cooldownMillis) }
                tracker.onForegroundChange(packageName, tasks) { taskId, pkg ->
                    byId[taskId]?.let { it.enabled && it.triggers.any { t -> t.matchesPackage(pkg) } }
                        ?: false
                }.forEach { command ->
                    val taskId = when (command) {
                        is AppForegroundTracker.Command.Run -> command.taskId
                        is AppForegroundTracker.Command.Exit -> command.taskId
                    }
                    val automation = byId[taskId] ?: return@forEach
                    when (command) {
                        is AppForegroundTracker.Command.Run -> executionEngine.runAutomation(automation)
                        is AppForegroundTracker.Command.Exit -> executionEngine.runExit(automation)
                    }
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
