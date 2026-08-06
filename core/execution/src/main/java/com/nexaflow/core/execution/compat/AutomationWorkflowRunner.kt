package com.nexaflow.core.execution.compat

import android.content.Context
import android.content.Intent
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.compat.ExecutionChannelSelector
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ACTION_AUTOMATIONS_CHANGED
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.execution.state.DeviceStateTransactionStore
import com.nexaflow.core.execution.state.StateTransactionStore
import com.nexaflow.core.execution.workflow.ActionExecutor
import com.nexaflow.core.execution.workflow.ActionRegistryExecutor
import com.nexaflow.core.execution.workflow.WorkflowExecutionResult
import com.nexaflow.core.execution.workflow.WorkflowInterpreter
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Executes legacy [Automation]s through the Phase-3 workflow engine.
 *
 * Each run maps the automation with [AutomationWorkflowMapper] (actions →
 * sequential workflow, exitActions → exit workflow, revertOnExit → captured
 * [com.nexaflow.core.execution.state.StateTransaction] rolled back on exit) and
 * executes the mapped tree with [WorkflowInterpreter]. History and the
 * execution timeline are recorded exactly like the legacy `ExecutionEngine`.
 */
class AutomationWorkflowRunner(
    private val executorProvider: suspend (ExecutionProvider?) -> ActionExecutor,
    private val historyRepository: HistoryRepository,
    private val stateStore: StateTransactionStore,
    private val logStore: LogStore = InMemoryLogStore(),
    private val epochMillis: EpochMillis = EpochMillis.System,
    private val channelProvider: suspend () -> ExecutionProvider? = { null },
    private val onChanged: () -> Unit = {}
) {

    suspend fun runAutomation(automation: Automation): ExecutionRecord {
        val startedAt = epochMillis.now()
        // Select the best channel ONCE per run; the exact same provider is
        // passed to the executor and recorded, so the log always reflects the
        // channel the actions were actually executed through.
        val selected = channelProvider()
        val channel = selected?.type?.name
        val mapped = AutomationWorkflowMapper.map(automation)
        if (mapped.revertOnExit) {
            stateStore.capture(automation.id)
        }
        val interpreter = WorkflowInterpreter(executorProvider(selected), epochMillis = epochMillis)
        val outcome = interpreter.execute(mapped.runWorkflow.root)
        val actionResults = outcome.nodeResults.map { it.toActionExecutionResult() }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = outcome.success,
            message = buildMessage(outcome),
            executedAt = startedAt,
            channel = channel,
            actionResults = actionResults
        )
        historyRepository.recordExecution(record)
        recordTimeline(automation, "RUN", record, startedAt)
        onChanged()
        return record
    }

    /**
     * Runs the exit behavior: rolls back the captured state when [revertOnExit],
     * otherwise executes the mapped exit workflow. Records the run in history.
     */
    suspend fun runExit(automation: Automation): ExecutionRecord {
        val startedAt = epochMillis.now()
        // Same channel is used for execution and recording (see runAutomation).
        val selected = channelProvider()
        val channel = selected?.type?.name
        val mapped = AutomationWorkflowMapper.map(automation)
        // Nothing to do when there are no exit actions and no state to restore.
        if (!mapped.revertOnExit && mapped.exitWorkflow == null) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "No exit behavior configured",
                executedAt = startedAt,
                channel = channel
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "EXIT", record, startedAt)
            return record
        }
        val actionResults: List<ActionExecutionResult>
        val results: List<SystemControlResult>
        if (mapped.revertOnExit) {
            val rollback = stateStore.rollback(automation.id)
            actionResults = listOf(
                ActionExecutionResult(
                    actionType = "STATE_RESTORE",
                    success = rollback.success,
                    message = rollback.message,
                    durationMs = 0
                )
            )
            results = listOf(rollback)
        } else {
            val interpreter = WorkflowInterpreter(executorProvider(selected), epochMillis = epochMillis)
            val outcome = interpreter.execute(mapped.exitWorkflow!!.root)
            actionResults = outcome.nodeResults.map { it.toActionExecutionResult() }
            results = outcome.nodeResults.map { SystemControlResult(it.success, it.message) }.ifEmpty {
                listOf(SystemControlResult(outcome.success, outcome.message))
            }
        }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = results.joinToString(" | ") { it.message },
            executedAt = startedAt,
            channel = channel,
            actionResults = actionResults
        )
        historyRepository.recordExecution(record)
        recordTimeline(automation, "EXIT", record, startedAt)
        onChanged()
        return record
    }

    /** Discards any stored snapshot (e.g. when the automation is deleted). */
    fun clearSnapshot(automationId: String) {
        stateStore.clear(automationId)
    }

    private fun buildMessage(outcome: WorkflowExecutionResult): String {
        if (outcome.nodeResults.isEmpty()) return "No actions configured"
        return outcome.nodeResults.joinToString(" | ") { it.message }
    }

    /** Maps a workflow node result to the timeline entry (type carried by the node). */
    private fun com.nexaflow.core.execution.workflow.NodeResult.toActionExecutionResult(): ActionExecutionResult {
        return ActionExecutionResult(
            actionType = actionType ?: "WORKFLOW",
            success = success,
            message = message,
            durationMs = durationMs
        )
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

    companion object {
        /**
         * Production factory: builds the registry-backed executor (reading the
         * current notification settings per run) and the device state store.
         *
         * Phase 6: every run detects the live device profile via
         * [ExecutionChannelSelector] and picks the best execution channel
         * automatically, passing it into the action context so shell actions
         * route through the runtime-selected provider instead of a hard-coded
         * runtime.
         */
        fun forDevice(
            context: Context,
            historyRepository: HistoryRepository,
            notificationPreferences: NotificationPreferences,
            registry: ActionRegistry = ActionRegistry.default(),
            logStore: LogStore = InMemoryLogStore(),
            epochMillis: EpochMillis = EpochMillis.System,
            channelSelector: ExecutionChannelSelector = ExecutionChannelSelector()
        ): AutomationWorkflowRunner {
            val controller = RomIntegrationManager.controller(context)
            return AutomationWorkflowRunner(
                // The runner selects the best provider once per run and passes
                // it in, so the executed channel always matches the recorded one.
                executorProvider = { channel ->
                    val notif = notificationPreferences.settings.first()
                    ActionRegistryExecutor(
                        registry,
                        ActionExecutionContext(context, controller, notif, channel)
                    )
                },
                channelProvider = { channelSelector.select(context) },
                historyRepository = historyRepository,
                stateStore = DeviceStateTransactionStore(context),
                logStore = logStore,
                epochMillis = epochMillis,
                onChanged = {
                    context.sendBroadcast(
                        Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName)
                    )
                }
            )
        }
    }
}
