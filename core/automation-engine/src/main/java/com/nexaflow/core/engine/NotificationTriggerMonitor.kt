package com.nexaflow.core.engine

import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.NotificationAccess
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires automations with a NOTIFICATION trigger when a matching notification is
 * posted or removed. The trigger config supports:
 *  - "packages": comma-separated package names (blank = any app)
 *  - "contains": text the notification title/body must contain (blank = any)
 *  - "event": "POSTED" (default) or "REMOVED"
 *
 * Delegated to by [NotificationListener] (the system-bound service). Exit
 * behavior mirrors [BluetoothMonitor]: when the notification that activated a
 * task goes away, the task's exit actions run.
 */
@Singleton
class NotificationTriggerMonitor @Inject constructor(
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    /** automations currently in their triggered state, mapped to the package that triggered them. */
    private val activeStates = ConcurrentHashMap<String, String>()

    fun onNotificationPosted(packageName: String, title: String?, text: String?) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { trigger ->
                        trigger.type == TriggerType.NOTIFICATION &&
                            matches(trigger.config, packageName, title, text)
                    }
                }
                .forEach { automation ->
                    val notificationTriggers = automation.triggers.filter {
                        it.type == TriggerType.NOTIFICATION && matches(it.config, packageName, title, text)
                    }
                    val firesOnPosted = notificationTriggers.any { (it.config["event"] ?: "POSTED") == "POSTED" }
                    val firesOnRemoved = notificationTriggers.any { (it.config["event"] ?: "POSTED") == "REMOVED" }
                    if (firesOnPosted) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = packageName
                            executionEngine.runAutomation(automation)
                        }
                    } else if (firesOnRemoved && activeStates[automation.id] == packageName) {
                        // Only REMOVED triggers: a new notification arriving while the
                        // task is active on this package means the previous one was
                        // replaced, so end the active state.
                        activeStates.remove(automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    fun onNotificationRemoved(packageName: String, title: String?, text: String?) {
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any { trigger ->
                        trigger.type == TriggerType.NOTIFICATION && matches(trigger.config, packageName, title, text)
                    }
                }
                .forEach { automation ->
                    val notificationTriggers = automation.triggers.filter {
                        it.type == TriggerType.NOTIFICATION && matches(it.config, packageName, title, text)
                    }
                    val firesOnPosted = notificationTriggers.any { (it.config["event"] ?: "POSTED") == "POSTED" }
                    val firesOnRemoved = notificationTriggers.any { (it.config["event"] ?: "POSTED") == "REMOVED" }
                    if (firesOnRemoved) {
                        val last = lastRunAt[automation.id] ?: 0L
                        val now = System.currentTimeMillis()
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activeStates[automation.id] = packageName
                            executionEngine.runAutomation(automation)
                        }
                    } else if (firesOnPosted && activeStates[automation.id] == packageName) {
                        // The notification that activated the task was dismissed: run exit.
                        activeStates.remove(automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    /**
     * Re-applies the blocking state from persisted automations. Called when the
     * notification listener reconnects (e.g. after a process restart), because
     * the in-memory [NotificationAccess] set is empty after a fresh process.
     */
    fun restoreBlockedState() {
        scope.launch {
            val automations = repository.getAutomations().first()
            val toBlock = mutableSetOf<String>()
            automations
                .filter { it.enabled }
                .flatMap { it.actions }
                .filter { action ->
                    action.type == ActionType.SYSTEM_BLOCK_NOTIFICATION &&
                        (action.config["enabled"]?.toBoolean() ?: true)
                }
                .forEach { action ->
                    (action.config["packages"] ?: action.config["package"] ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { toBlock.add(it) }
                }
            toBlock.forEach { NotificationAccess.setBlocked(it, true) }
        }
    }

    private fun matches(
        config: Map<String, String>,
        packageName: String,
        title: String?,
        text: String?
    ): Boolean {
        val packages = config["packages"].orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val packageMatch = packages.isEmpty() || packageName in packages
        if (!packageMatch) return false
        val contains = config["contains"].orEmpty().trim()
        if (contains.isEmpty()) return true
        val haystack = listOfNotNull(title, text).joinToString(" ")
        return haystack.contains(contains, ignoreCase = true)
    }

}
