package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.compat.ExecutionChannelSelector
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Executes tasks. Action dispatch is delegated to an [ActionRegistry] so new
 * actions (built-in or plugin-provided) plug in without changing the engine.
 * Every run is recorded to the [LogStore] execution timeline.
 */
class ExecutionEngine(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val notificationPreferences: NotificationPreferences,
    private val actionRegistry: ActionRegistry = ActionRegistry.default(),
    private val logStore: LogStore = InMemoryLogStore(),
    private val epochMillis: EpochMillis = EpochMillis.System,
    private val channelSelector: ExecutionChannelSelector = ExecutionChannelSelector()
) {

    /**
     * Snapshots captured for automations with revertOnExit, keyed by automation id.
     * The value is nullable because a failed capture must not block the run; a
     * null snapshot simply means "nothing to restore" on exit.
     */
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, DeviceStateSnapshot?>()

    suspend fun runAutomation(automation: Automation): ExecutionRecord {
        val startedAt = epochMillis.now()
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        val channel = channelSelector.select(context)
        // Capture the device state when the run needs to restore anything on
        // exit: either the global revert-on-exit toggle or any action configured
        // with a per-action "restore original" end behavior. A failed snapshot
        // must never block the actual actions (e.g. an unreadable stream on an
        // unusual ROM used to abort the whole run before any action executed).
        val needsSnapshot = automation.revertOnExit ||
            automation.actions.any { it.endBehavior?.mode == EndMode.REVERT }
        if (needsSnapshot) {
            snapshots[automation.id] = runCatching { DeviceStateSnapshot.capture(context) }.getOrNull()
        }
        val results = automation.actions.map { action ->
            val actionStartedAt = epochMillis.now()
            val result = executeAction(action, controller, notif, channel)
            ActionExecutionResult(
                actionType = action.type.name,
                success = result.success,
                message = result.message,
                durationMs = epochMillis.now() - actionStartedAt
            )
        }
        // Actions are executed sequentially, so a SYSTEM_WAIT action placed anywhere
        // pauses the chain for the configured duration (counter mode).
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = buildMessage(results),
            executedAt = startedAt,
            channel = channel?.type?.name,
            actionResults = results
        )
        historyRepository.recordExecution(record)
        recordTimeline(automation, "RUN", record, startedAt)
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /**
     * Runs the exit behavior of a task when its condition stops being true:
     * either restores the device to its pre-run state (revertOnExit) or runs
     * the configured exit actions. Records the run in history as well.
     */
    suspend fun runExit(automation: Automation): ExecutionRecord {
        val startedAt = epochMillis.now()
        // Nothing to do when there are no exit actions and no state to restore.
        if (!automation.revertOnExit && automation.exitActions.isEmpty()) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "No exit behavior configured",
                executedAt = startedAt
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "EXIT", record, startedAt)
            return record
        }
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        val channel = channelSelector.select(context)
        val actionResults = if (automation.revertOnExit) {
            val snapshot = snapshots.remove(automation.id)
            if (snapshot != null) {
                snapshot.restore(context)
                listOf(
                    ActionExecutionResult(
                        actionType = "STATE_RESTORE",
                        success = true,
                        message = "Restored original state",
                        durationMs = 0
                    )
                )
            } else {
                listOf(
                    ActionExecutionResult(
                        actionType = "STATE_RESTORE",
                        success = true,
                        message = "Nothing to restore",
                        durationMs = 0
                    )
                )
            }
        } else {
            mutableListOf<ActionExecutionResult>().apply {
                // Adaptive per-action end behavior: each action configured with an
                // end behavior (leave / restore original / set a specific value)
                // is honored exactly as configured, before the custom exit actions.
                val snapshot = snapshots.remove(automation.id)
                automation.actions.forEach { action ->
                    val behavior = action.endBehavior ?: return@forEach
                    val actionStartedAt = epochMillis.now()
                    val result: SystemControlResult = when (behavior.mode) {
                        EndMode.LEAVE -> null
                        EndMode.REVERT -> snapshot?.restoreSetting(context, action)
                            ?: SystemControlResult.fail("No captured state to restore for ${action.type.name}")
                        EndMode.SET_VALUE -> executeAction(action.withConfig(behavior.config), controller, notif, channel)
                    } ?: return@forEach
                    add(
                        ActionExecutionResult(
                            actionType = "${action.type.name}_END",
                            success = result.success,
                            message = result.message,
                            durationMs = epochMillis.now() - actionStartedAt
                        )
                    )
                }
                // The explicitly configured exit actions run last.
                automation.exitActions.forEach { action ->
                    val actionStartedAt = epochMillis.now()
                    val result = executeAction(action, controller, notif, channel)
                    add(
                        ActionExecutionResult(
                            actionType = action.type.name,
                            success = result.success,
                            message = result.message,
                            durationMs = epochMillis.now() - actionStartedAt
                        )
                    )
                }
            }
        }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = actionResults.all { it.success },
            message = buildMessage(actionResults),
            executedAt = startedAt,
            channel = channel?.type?.name,
            actionResults = actionResults
        )
        historyRepository.recordExecution(record)
        recordTimeline(automation, "EXIT", record, startedAt)
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /** Discards any stored snapshot (e.g. when the automation is deleted). */
    fun clearSnapshot(automationId: String) {
        snapshots.remove(automationId)
    }

    private suspend fun executeAction(
        action: Action,
        controller: SystemController,
        notif: NotificationSettings,
        channel: ExecutionProvider?
    ): SystemControlResult {
        val handler = actionRegistry.handlerFor(action.type)
            ?: return SystemControlResult.fail("No handler registered for ${action.type}")
        return handler.execute(action, ActionExecutionContext(context, controller, notif, channel))
    }

    private suspend fun recordTimeline(
        automation: Automation,
        kind: String,
        record: ExecutionRecord,
        startedAt: Long
    ) {
        try {
            logStore.recordExecution(
                ExecutionTimelineEntry(
                    id = record.id,
                    automationId = automation.id,
                    automationName = automation.name,
                    kind = kind,
                    success = record.success,
                    message = record.message,
                    startedAt = startedAt,
                    durationMs = epochMillis.now() - startedAt,
                    channel = record.channel
                )
            )
        } catch (_: Throwable) {
            // Logging must never break execution.
        }
    }

    private fun buildMessage(results: List<ActionExecutionResult>): String {
        if (results.isEmpty()) return "No actions configured"
        return results.joinToString(" | ") { it.message }
    }
}
