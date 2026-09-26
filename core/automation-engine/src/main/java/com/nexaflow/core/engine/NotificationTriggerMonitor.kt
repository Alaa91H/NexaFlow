package com.nexaflow.core.engine

import android.net.Uri
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.NotificationAccess
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Notification trigger monitor with durable occurrence ownership.
 *
 * POSTED triggers stay active until the exact notification that activated them
 * disappears. REMOVED triggers stay active until a new matching notification
 * for the same package is posted. The runtime ledger survives process death;
 * [reconcileActiveNotifications] re-validates ownership when Android reconnects
 * the NotificationListenerService.
 */
@Singleton
class NotificationTriggerMonitor @Inject constructor(
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    data class ActiveNotification(
        val key: String,
        val packageName: String,
        val title: String?,
        val text: String?
    )

    private data class Occurrence(
        val event: String,
        val packageName: String,
        val notificationKey: String
    )

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    private val evaluationMutex = Mutex()

    fun onNotificationPosted(
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?
    ) {
        scope.launch {
            handleEdge(
                event = EVENT_POSTED,
                notificationKey = notificationKey,
                packageName = packageName,
                title = title,
                text = text
            )
        }
    }

    fun onNotificationRemoved(
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?
    ) {
        scope.launch {
            handleEdge(
                event = EVENT_REMOVED,
                notificationKey = notificationKey,
                packageName = packageName,
                title = title,
                text = text
            )
        }
    }

    /**
     * Reconciles durable notification lifecycles against Android's current
     * active-notification snapshot after listener/process restart.
     */
    fun reconcileActiveNotifications(active: List<ActiveNotification>) {
        scope.launch {
            evaluationMutex.withLock {
                val automations = repository.getAutomations().first()
                val byId = automations.associateBy { it.id }

                runtimeStore.activeStates()
                    .filter { it.source == SOURCE }
                    .forEach { state ->
                        val automation = byId[state.automationId]
                        if (automation == null) {
                            // Definition is gone; retain durable evidence for
                            // explicit recovery and do not invent an exit.
                            return@forEach
                        }
                        if (!automation.enabled ||
                            automation.triggers.none { it.type == TriggerType.NOTIFICATION }
                        ) {
                            requestExit(
                                automation = automation,
                                reason = ExitReason.AUTOMATION_DISABLED,
                                occurrenceId = state.occurrenceId
                            )
                            return@forEach
                        }

                        val occurrence = decodeSourceKey(state.sourceKey)
                            ?: return@forEach
                        val matchingActive = active.any { snapshot ->
                            snapshot.packageName == occurrence.packageName &&
                                notificationMatchesContent(
                                    automation = automation,
                                    packageName = snapshot.packageName,
                                    title = snapshot.title,
                                    text = snapshot.text
                                )
                        }
                        val exactStillActive = active.any { snapshot ->
                            snapshot.key == occurrence.notificationKey &&
                                snapshot.packageName == occurrence.packageName
                        }

                        when (occurrence.event) {
                            EVENT_POSTED -> {
                                if (!exactStillActive) {
                                    requestExit(
                                        automation = automation,
                                        reason = ExitReason.TRIGGER_FALSE,
                                        occurrenceId = state.occurrenceId
                                    )
                                }
                            }
                            EVENT_REMOVED -> {
                                if (matchingActive) {
                                    requestExit(
                                        automation = automation,
                                        reason = ExitReason.TRIGGER_FALSE,
                                        occurrenceId = state.occurrenceId
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }

    private suspend fun handleEdge(
        event: String,
        notificationKey: String,
        packageName: String,
        title: String?,
        text: String?
    ) = evaluationMutex.withLock {
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
                val matching = automation.triggers.withIndex().filter { (_, trigger) ->
                    trigger.type == TriggerType.NOTIFICATION &&
                        matches(trigger.config, packageName, title, text)
                }
                val edgeIndices = matching
                    .filter { (_, trigger) ->
                        (trigger.config["event"] ?: EVENT_POSTED) == event
                    }
                    .map { it.index }
                    .toSet()

                val current = runtimeStore.current(automation.id)
                val currentOccurrence = current
                    ?.takeIf { it.source == SOURCE }
                    ?.let { decodeSourceKey(it.sourceKey) }

                if (edgeIndices.isNotEmpty()) {
                    if (current == null) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            activate(
                                automation = automation,
                                event = event,
                                notificationKey = notificationKey,
                                packageName = packageName,
                                matchedTriggerIndices = edgeIndices,
                                now = now
                            )
                        }
                    }
                    return@forEach
                }

                // Exact opposite edge ends an already-owned notification state.
                if (currentOccurrence != null) {
                    val shouldExit = when (currentOccurrence.event) {
                        EVENT_POSTED ->
                            event == EVENT_REMOVED &&
                                currentOccurrence.notificationKey == notificationKey
                        EVENT_REMOVED ->
                            event == EVENT_POSTED &&
                                currentOccurrence.packageName == packageName &&
                                notificationMatchesContent(
                                    automation = automation,
                                    packageName = packageName,
                                    title = title,
                                    text = text
                                )
                        else -> false
                    }
                    if (shouldExit) {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.TRIGGER_FALSE,
                            occurrenceId = current.occurrenceId
                        )
                    }
                }
            }
    }

    private suspend fun activate(
        automation: Automation,
        event: String,
        notificationKey: String,
        packageName: String,
        matchedTriggerIndices: Set<Int>,
        now: Long
    ) {
        val occurrenceId = "notification:${automation.id}:${UUID.randomUUID()}"
        val sourceKey = encodeSourceKey(
            automationId = automation.id,
            occurrence = Occurrence(
                event = event,
                packageName = packageName,
                notificationKey = notificationKey
            )
        )
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            ),
            triggerOccurrence = TriggerOccurrence(
                matchedTriggerIndices = matchedTriggerIndices,
                occurredAtEpochMs = now,
                sourceId = SOURCE,
                eventId = "notification:$event:${Uri.encode(notificationKey)}"
            )
        )
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> Unit
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> Unit
        }
    }

    /**
     * Re-applies the blocking state from persisted automations. Called when the
     * notification listener reconnects because NotificationAccess is in-memory.
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

    /**
     * Matches the notification's package/content filters independently of the
     * configured edge. The opposite edge closes a durable occurrence, so a
     * REMOVED trigger must be able to recognize the later matching POSTED edge
     * even though its trigger config still says REMOVED.
     */
    private fun notificationMatchesContent(
        automation: Automation,
        packageName: String,
        title: String?,
        text: String?
    ): Boolean = automation.triggers.any { trigger ->
        trigger.type == TriggerType.NOTIFICATION &&
            matches(trigger.config, packageName, title, text)
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
        if (packages.isNotEmpty() && packageName !in packages) return false

        val contains = config["contains"].orEmpty().trim()
        if (contains.isEmpty()) return true
        val haystack = listOfNotNull(title, text).joinToString(" ")
        return haystack.contains(contains, ignoreCase = true)
    }

    private fun encodeSourceKey(automationId: String, occurrence: Occurrence): String =
        listOf(
            automationId,
            occurrence.event,
            Uri.encode(occurrence.packageName),
            Uri.encode(occurrence.notificationKey)
        ).joinToString(SEPARATOR)

    private fun decodeSourceKey(sourceKey: String): Occurrence? {
        val parts = sourceKey.split(SEPARATOR, limit = 4)
        if (parts.size != 4) return null
        val event = parts[1].takeIf { it == EVENT_POSTED || it == EVENT_REMOVED }
            ?: return null
        return Occurrence(
            event = event,
            packageName = Uri.decode(parts[2]),
            notificationKey = Uri.decode(parts[3])
        )
    }

    private companion object {
        const val SOURCE = "notification"
        const val EVENT_POSTED = "POSTED"
        const val EVENT_REMOVED = "REMOVED"
        const val SEPARATOR = "|"
    }
}
