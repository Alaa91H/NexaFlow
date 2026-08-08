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
import com.nexaflow.core.execution.variables.BuiltinVariables
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.constraints.ConstraintEvaluator
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.VariableResolver
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
    private val channelSelector: ExecutionChannelSelector = ExecutionChannelSelector(),
    // Optional user-defined %variables. Null (default) keeps the engine fully
    // functional with only the built-in device-context variables.
    private val variableRepository: VariableRepository? = null,
    // Test seam: pins the device state used by the constraint gate so tests
    // can exercise pass/block deterministically without real system probes.
    private val constraintStateProvider: (() -> ConstraintSnapshot?)? = null
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
        // Constraint gate: every configured constraint must pass before the
        // task runs. A blocked run is recorded (not silently dropped) so the
        // user can see why nothing happened. Captured before any state snapshot
        // so a blocked run never leaks a snapshot that would never be restored.
        if (automation.constraints.isNotEmpty()) {
            val state = constraintStateProvider?.invoke()
                ?: runCatching { ConstraintStateReader.capture(context) }.getOrNull()
            if (state == null || !ConstraintEvaluator.allSatisfied(automation.constraints, state)) {
                // A deliberately-blocked run is not a failure: success=true keeps
                // history stats honest, while the BLOCKED timeline kind + the
                // "Skipped:" prefix let UIs surface why nothing ran.
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = true,
                    message = "Skipped: constraints not met",
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                historyRepository.recordExecution(record)
                recordTimeline(automation, "BLOCKED", record, startedAt)
                return record
            }
        }
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
        // Resolve %variables once per run (single repo read + device probe),
        // then apply pure string substitution per action.
        val variables = runCatching { resolveVariables() }.getOrDefault(emptyMap())
        val results = automation.actions.map { action ->
            val actionStartedAt = epochMillis.now()
            val result = executeAction(resolveAction(action, variables), controller, notif, channel)
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
                // %variables resolved only when exit actions actually run — pure
                // revert tasks never pay the extra repo read + device probe.
                val variables = runCatching { resolveVariables() }.getOrDefault(emptyMap())
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
                        EndMode.SET_VALUE -> executeAction(resolveAction(action.withConfig(behavior.config), variables), controller, notif, channel)
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
                    val result = executeAction(resolveAction(action, variables), controller, notif, channel)
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

    /**
     * Config keys that carry structured data (JSON) rather than free text:
     * %variable substitution would corrupt them, so they are skipped.
     */
    private val opaqueConfigKeys = setOf("bundleJson", "action_buttons")

    /**
     * Resolves %variable placeholders (built-ins + user globals) inside every
     * text-bearing config value before the handler sees it. Unknown names are
     * left untouched. Pure string substitution — the variable map is resolved
     * once per run by the caller. Opaque (structured) keys are skipped.
     */
    private fun resolveAction(action: Action, variables: Map<String, String>): Action {
        if (variables.isEmpty()) return action
        return action.copy(
            config = action.config.mapValues { (key, value) ->
                if (key in opaqueConfigKeys) value
                else VariableResolver.resolve(value, variables)
            }
        )
    }

    private suspend fun resolveVariables(): Map<String, String> {
        val builtins = runCatching { BuiltinVariables.provide(context) }.getOrDefault(emptyMap())
        val globals = runCatching { variableRepository?.getVariablesOnce().orEmpty() }.getOrDefault(emptyList())
        if (globals.isEmpty()) return builtins
        return builtins + globals.associate { it.name to it.value }
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
