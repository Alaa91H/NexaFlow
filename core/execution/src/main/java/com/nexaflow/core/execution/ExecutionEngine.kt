package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.compat.ExecutionChannelSelector
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.capability.CapabilityActionMapper
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.execution.variables.BuiltinVariables
import com.nexaflow.core.execution.variables.ScopedDataRuntime
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.execution.constraints.AutomationConstraintGate
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.MaintenanceReadiness
import com.nexaflow.domain.models.MaintenanceReadinessEvaluator
import com.nexaflow.domain.models.MaintenanceExecutionIdentity
import com.nexaflow.domain.models.completesExitOnFinish
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValueCodec
import com.nexaflow.domain.variables.VariableResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.time.ZonedDateTime
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
    private val constraintStateProvider: (() -> ConstraintSnapshot?)? = null,
    // Durable counterpart to [activeExecutions]. It survives a service or
    // process restart so a genuinely running task can still execute its end
    // behavior when the monitor reconciles the current device state.
    private val activeExecutionStore: ActiveExecutionStore = ActiveExecutionStore(context),
    /** Occurrence-aware durable source of truth for stateful trigger lifecycles. */
    private val automationRuntimeStore: AutomationRuntimeStore = AutomationRuntimeStore(context),
    /** Optional safe-capability seam; null preserves legacy handler-only construction. */
    private val capabilityExecutionService: CapabilityExecutionService? = null,
    /** Current shared availability observation; absent only in legacy/test construction. */
    private val capabilitySnapshotProvider: (() -> CapabilitySnapshot)? = null
) {

    companion object {
        /** Prefix used by UI callers to present a manual condition rejection accurately. */
        const val MANUAL_CONDITION_NOT_MET_PREFIX = "Conditions not satisfied; "
    }

    /**
     * Snapshots captured for automations with revertOnExit, keyed by automation id.
     * The value is nullable because a failed capture must not block the run; a
     * null snapshot simply means "nothing to restore" on exit.
     */
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, DeviceStateSnapshot?>()

    /**
     * Runtime ledger for tasks whose main actions actually started. Monitors may
     * observe a trigger state before the constraint gate accepts it; an end
     * behavior must only run after a successful entry into the task lifecycle,
     * never merely because a trigger later flips back.
     */
    private val activeExecutions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Serializes the paired in-memory and durable exit-ledger consumption per task. */
    private val exitConsumptionLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    suspend fun runAutomation(
        automation: Automation,
        // Phase-2 payload context (JSON Merge Patch delta, 256KB budget). When
        // null, a fresh context is created for this run so handlers always see
        // one — callers may also pass their own to seed or inspect it.
        runContext: WorkflowRunContext? = null,
        /**
         * One-shot event sources (SMS, a single scheduled time, webhook, etc.)
         * have no later opposite state that can close the lifecycle. When true,
         * execute the configured end behavior immediately after the main action
         * chain finishes. State and time-range sources keep the default false
         * and close only when their actual condition ends.
         */
        completeExitOnFinish: Boolean = false,
        /** Present only for a stateful trigger occurrence owned by ExitCoordinator. */
        lifecycleContext: AutomationLifecycleContext? = null
    ): ExecutionRecord {
        val startedAt = epochMillis.now()
        capabilitySnapshotProvider?.invoke()?.let { snapshot ->
            val validation = WorkflowCapabilityValidator.validate(automation, snapshot)
            if (!validation.admissible) {
                val missing = validation.missingCapabilities.joinToString().ifBlank { "unmapped or unavailable execution path" }
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = false,
                    message = "Blocked: required capability is unavailable ($missing)",
                    executedAt = startedAt
                )
                historyRepository.recordExecution(record)
                recordTimeline(automation, "CAPABILITY_BLOCKED", record, startedAt)
                return record
            }
        }
        val maintenanceNow = ZonedDateTime.now()
        val maintenanceOccurrenceKey = MaintenanceExecutionIdentity.occurrenceKey(automation, maintenanceNow)
        // Local name avoids shadowing the [Context] property used below.
        val payloadContext = runContext ?: WorkflowRunContext.create(automation.id, startedAt)
        // One typed, scoped facade per run. It layers over the existing payload
        // context and repository; no action accesses a raw variable store.
        val dataRuntime = variableRepository?.let { ScopedDataRuntime(payloadContext, it) }
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        val channel = channelSelector.select(context)
        if (maintenanceOccurrenceKey != null &&
            activeExecutionStore.hasCompletedMaintenanceOccurrence(maintenanceOccurrenceKey)
        ) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "Skipped: maintenance occurrence already completed",
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "MAINTENANCE_DUPLICATE_SKIPPED", record, startedAt)
            return record
        }
        // Constraint and maintenance-window gates run before any snapshot,
        // checkpoint or side effect. One capture is reused by both paths so a
        // maintenance run cannot observe inconsistent resource values.
        val requiresDeviceState = automation.constraints.isNotEmpty() ||
            automation.maintenanceProfile?.window != null
        val state = if (requiresDeviceState) {
            constraintStateProvider?.invoke()
                ?: runCatching { ConstraintStateReader.capture(context) }.getOrNull()
        } else {
            null
        }
        if (automation.constraints.isNotEmpty()) {
            val constraintResult = AutomationConstraintGate(capabilityExecutionService).evaluate(automation, state)
            if (constraintResult != ConditionResult.Satisfied) {
                // A deliberately-blocked run is not a failure: success=true keeps
                // history stats honest. The typed gate reason preserves UNKNOWN
                // and UNAVAILABLE rather than silently labelling them false.
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = true,
                    message = "Skipped: ${constraintResult.toGateMessage()}",
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                historyRepository.recordExecution(record)
                recordTimeline(automation, "BLOCKED", record, startedAt)
                return record
            }
        }
        val maintenanceReadiness = MaintenanceReadinessEvaluator.evaluate(
            profile = automation.maintenanceProfile,
            snapshot = state ?: ConstraintSnapshot(),
            now = maintenanceNow
        )
        if (maintenanceReadiness is MaintenanceReadiness.WaitingForWindow) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "Skipped: maintenance waiting for ${maintenanceReadiness.reason.name}",
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "MAINTENANCE_WAITING", record, startedAt)
            return record
        }
        // Checkpoint must exist before any side effect. A rejected durable
        // admission is recorded as a failed run instead of pretending actions
        // were safely started without an idempotency/recovery record.
        val checkpointAccepted = activeExecutionStore.beginCheckpoint(
            DurableExecutionCheckpoint(
                runId = payloadContext.runId,
                automationId = automation.id,
                totalActions = automation.actions.size,
                nextActionIndex = 0,
                status = DurableExecutionStatus.STARTED,
                startedAt = startedAt,
                updatedAt = startedAt
            )
        )
        if (!checkpointAccepted) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = false,
                message = "Unable to create durable execution checkpoint",
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "CHECKPOINT_REJECTED", record, startedAt)
            return record
        }
        // The constraint gate accepted this run, so it owns a lifecycle exit if
        // a monitor later reports that the trigger condition ended.
        activeExecutions.add(automation.id)
        activeExecutionStore.markStarted(automation.id)
        // Capture the device state when the run needs to restore anything on
        // exit: either the global revert-on-exit toggle or any action configured
        // with a per-action "restore original" end behavior. A failed snapshot
        // must never block the actual actions (e.g. an unreadable stream on an
        // unusual ROM used to abort the whole run before any action executed).
        val needsSnapshot = automation.revertOnExit ||
            automation.actions.any { it.endBehavior?.mode == EndMode.REVERT }
        val capturedSnapshot = if (needsSnapshot) {
            runCatching { DeviceStateSnapshot.capture(context) }.getOrNull()
        } else {
            null
        }
        // Stateful sources must establish an occurrence-aware durable owner
        // before their first side effect. A new activation cannot overwrite a
        // still-active, exiting, or failed occurrence of the same automation.
        if (lifecycleContext != null) {
            val lifecycleAccepted = automationRuntimeStore.activate(
                AutomationRuntimeState(
                    automationId = automation.id,
                    occurrenceId = lifecycleContext.occurrenceId,
                    source = lifecycleContext.source,
                    sourceKey = lifecycleContext.sourceKey,
                    lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                    activatedAt = startedAt,
                    expectedEndAt = lifecycleContext.expectedEndAt,
                    scheduleGeneration = lifecycleContext.scheduleGeneration,
                    snapshotJson = capturedSnapshot?.encodeForRuntime()
                )
            )
            if (!lifecycleAccepted) {
                // The action checkpoint and legacy marker were accepted earlier
                // solely to reserve this run. Undo that reservation before
                // returning the explicit skip record; never overwrite the
                // previous lifecycle or its original-state snapshot.
                // Never remove the id-scoped legacy marker or in-memory
                // snapshot here: they may belong to the older lifecycle that
                // correctly caused this admission to be rejected.
                activeExecutionStore.completeCheckpoint(payloadContext.runId)
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = true,
                    message = "Skipped: a prior automation lifecycle still requires cleanup",
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                historyRepository.recordExecution(record)
                recordTimeline(automation, "LIFECYCLE_CONFLICT", record, startedAt)
                return record
            }
        }
        // The durable admission succeeded (or this is a legacy/stateless run),
        // so this invocation may now own the in-memory restore snapshot too.
        if (needsSnapshot) {
            snapshots[automation.id] = capturedSnapshot
        }
        // Resolve %variables once per run (single repo read + device probe),
        // then apply pure string substitution per action.
        val variables = runCatching { resolveVariables() }.getOrDefault(emptyMap())
        var inProgressActionIndex: Int? = null
        val results = try {
            automation.actions.mapIndexed { actionIndex, action ->
                inProgressActionIndex = actionIndex
                val actionStartedAt = epochMillis.now()
                val idempotencyKey = "${payloadContext.runId}:$actionIndex:${action.type.name}"
                activeExecutionStore.markActionStarted(
                    runId = payloadContext.runId,
                    actionIndex = actionIndex,
                    idempotencyKey = idempotencyKey,
                    updatedAt = actionStartedAt
                ) ?: error("Missing durable checkpoint for run ${payloadContext.runId}")
                // Actions run sequentially and each handler may publish to the
                // shared context (Step 4), so %CTX selectors are resolved here —
                // after the previous node ran, before this node dispatches.
                val resolved = resolveContextRefs(resolveAction(action, variables), payloadContext)
                val result = executeAction(
                    resolved,
                    controller,
                    notif,
                    channel,
                    automation.id,
                    automation.revertOnExit,
                    payloadContext,
                    dataRuntime
                )
                activeExecutionStore.markActionCompleted(
                    runId = payloadContext.runId,
                    actionIndex = actionIndex,
                    updatedAt = epochMillis.now()
                ) ?: error("Unable to commit durable checkpoint for run ${payloadContext.runId}")
                inProgressActionIndex = null
                ActionExecutionResult(
                    actionType = action.type.name,
                    success = result.success,
                    message = result.message,
                    durationMs = epochMillis.now() - actionStartedAt
                )
            }
        } catch (cancellation: CancellationException) {
            // The process may have interrupted a side effect after it began;
            // recovery must verify/compensate instead of replaying it blindly.
            inProgressActionIndex?.let { index ->
                runCatching {
                    activeExecutionStore.markActionUnknown(
                        runId = payloadContext.runId,
                        message = "Action $index interrupted before durable completion",
                        updatedAt = epochMillis.now()
                    )
                }
            }
            throw cancellation
        } catch (failure: Throwable) {
            inProgressActionIndex?.let { index ->
                runCatching {
                    activeExecutionStore.markActionUnknown(
                        runId = payloadContext.runId,
                        message = "Action $index crashed: ${failure.message?.take(100)}",
                        updatedAt = epochMillis.now()
                    )
                }
            }
            throw failure
        } finally {
            // Strict enforcement: ensure checkpoint is removed and one-shot exit is consumed 
            // even if the run timed out or was forcefully cancelled.
            activeExecutionStore.completeCheckpoint(payloadContext.runId)
            // Explicit callers can force completion, while the domain policy
            // closes purely event-driven tasks even if their monitor omitted the
            // flag. Stateful conditions remain open until their opposite state
            // is observed by the relevant monitor.
            if (completeExitOnFinish || automation.completesExitOnFinish) {
                withContext(NonCancellable) {
                    runExit(automation)
                }
            }
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
        // History is now durable. The active checkpoint and exit lifecycle were 
        // guaranteed to be consumed by the finally block above.
        if (record.success && maintenanceOccurrenceKey != null) {
            activeExecutionStore.recordCompletedMaintenanceOccurrence(
                occurrenceKey = maintenanceOccurrenceKey,
                automationId = automation.id,
                completedAt = epochMillis.now()
            )
        }
        recordTimeline(automation, "RUN", record, startedAt)
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /**
     * Manual "run now" gate: executes the task's actions when its trigger
     * condition is currently satisfied, and its exit behavior (the "when the
     * task ends" actions) otherwise — so a manual run is always correct even
     * when the condition that should fire the task is not met right now.
     */
    suspend fun runWithConditionGate(automation: Automation): ExecutionRecord {
        // The manual "run now" gate is tied to the task's full condition set:
        // triggers AND constraints. Only when EVERY configured trigger and
        // EVERY constraint is currently and verifiably satisfied do the task's
        // main actions run. Any false or unreadable condition follows the
        // configured "when the task ends" behavior instead.
        val triggersOk = TriggerStateEvaluator.isSatisfiedAsync(context, automation.triggers)
        val constraintResult = if (automation.constraints.isEmpty()) {
            ConditionResult.Satisfied
        } else {
            val state = constraintStateProvider?.invoke()
                ?: runCatching { ConstraintStateReader.capture(context) }.getOrNull()
            AutomationConstraintGate(capabilityExecutionService).evaluate(automation, state)
        }
        return if (triggersOk && constraintResult == ConditionResult.Satisfied) {
            runAutomation(automation)
        } else {
            runExit(
                automation = automation,
                forceConfiguredEnd = true,
                manualConditionRejected = true
            )
        }
    }

    /**
     * Runs the exit behavior of a task when its condition stops being true:
     * either restores the device to its pre-run state (revertOnExit) or runs
     * the configured exit actions. Records the run in history as well.
     */
    suspend fun runExit(
        automation: Automation,
        // A manual Run now is explicit: it may intentionally preview a configured
        // end action even when this process did not observe the task start.
        forceConfiguredEnd: Boolean = false,
        // Distinguishes a condition-gated manual tap from a monitor-driven exit
        // so the timeline and UI never report the outcome as a successful main run.
        manualConditionRejected: Boolean = false,
        /** Durable local snapshot supplied by the occurrence coordinator after restart. */
        runtimeSnapshotJson: String? = null
    ): ExecutionRecord {
        val startedAt = epochMillis.now()
        // Consume both ledgers as one critical section. Without this per-task
        // lock, two concurrent monitor callbacks can each consume a different
        // ledger and both execute the same end behavior.
        val exitLock = exitConsumptionLocks.computeIfAbsent(automation.id) { Mutex() }
        val hadActiveExecution = exitLock.withLock {
            val hadActiveInMemory = activeExecutions.remove(automation.id)
            val hadActiveInStore = activeExecutionStore.consumeStarted(automation.id)
            hadActiveInMemory || hadActiveInStore
        }
        if (!hadActiveExecution && !forceConfiguredEnd) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = "Skipped: task was not active",
                executedAt = startedAt
            )
            historyRepository.recordExecution(record)
            recordTimeline(automation, "EXIT_SKIPPED", record, startedAt)
            return record
        }
        // Nothing to do when there are no exit actions, no per-action end
        // behaviors and no state to restore. The per-action check is what makes
        // the unified builder model work: without it, a task that only configures
        // "when the task ends" options inside its actions (leave / restore / set
        // value) would silently never run them on exit.
        val hasPerActionEndBehavior = automation.actions.any { it.endBehavior != null }
        if (!automation.revertOnExit &&
            automation.exitActions.isEmpty() &&
            !hasPerActionEndBehavior
        ) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = if (manualConditionRejected) {
                    MANUAL_CONDITION_NOT_MET_PREFIX + "no end behavior configured"
                } else {
                    "No exit behavior configured"
                },
                executedAt = startedAt
            )
            historyRepository.recordExecution(record)
            recordTimeline(
                automation,
                if (manualConditionRejected) "MANUAL_CONDITION_NOT_MET" else "EXIT",
                record,
                startedAt
            )
            return record
        }
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        val channel = channelSelector.select(context)
        // revertOnExit deliberately supersedes both per-action end behaviors and
        // exitActions: the whole device state is restored instead. Do not "fix"
        // this to run them too — that would double-apply end actions after a revert.
        val actionResults = if (automation.revertOnExit) {
            val snapshot = snapshots.remove(automation.id)
                ?: DeviceStateSnapshot.decodeForRuntime(runtimeSnapshotJson)
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
                    ?: DeviceStateSnapshot.decodeForRuntime(runtimeSnapshotJson)
                automation.actions.forEach { action ->
                    val behavior = action.endBehavior ?: return@forEach
                    val actionStartedAt = epochMillis.now()
                    val result: SystemControlResult = when (behavior.mode) {
                        EndMode.LEAVE -> null
                        EndMode.REVERT -> snapshot?.restoreSetting(context, action)
                            ?: SystemControlResult.fail("No captured state to restore for ${action.type.name}")
                        EndMode.RERUN -> executeAction(resolveAction(action, variables), controller, notif, channel)
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
            message = if (manualConditionRejected) {
                MANUAL_CONDITION_NOT_MET_PREFIX + "end behavior: ${buildMessage(actionResults)}"
            } else {
                buildMessage(actionResults)
            },
            executedAt = startedAt,
            channel = channel?.type?.name,
            actionResults = actionResults
        )
        historyRepository.recordExecution(record)
        recordTimeline(
            automation,
            if (manualConditionRejected) "MANUAL_CONDITION_NOT_MET" else "EXIT",
            record,
            startedAt
        )
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /** Discards any stored snapshot (e.g. when the automation is deleted). */
    suspend fun clearSnapshot(automationId: String) {
        snapshots.remove(automationId)
        activeExecutions.remove(automationId)
        activeExecutionStore.clear(automationId)
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

    /**
     * Resolves `%CTX.<jsonpath>` selectors (Step 5) against the shared run
     * context so a node can consume the output of an earlier node. Runs after
     * [resolveAction] (so %NAME is already substituted) and after the previous
     * actions executed — the context then holds what they published.
     */
    private fun resolveContextRefs(action: Action, runContext: WorkflowRunContext): Action =
        action.copy(
            config = action.config.mapValues { (key, value) ->
                if (key in opaqueConfigKeys) value
                else ContextVariableResolver.resolve(value, runContext)
            }
        )

    private suspend fun resolveVariables(): Map<String, String> {
        val builtins = runCatching { BuiltinVariables.provide(context) }.getOrDefault(emptyMap())
        val globals = runCatching {
            variableRepository?.snapshot(epochMillis.now())?.variables.orEmpty()
        }.getOrDefault(emptyList())
        if (globals.isEmpty()) return builtins
        return builtins + globals.associate { it.name to RuntimeValueCodec.display(it.value) }
    }

    private suspend fun executeAction(
        action: Action,
        controller: SystemController,
        notif: NotificationSettings,
        channel: ExecutionProvider?,
        automationId: String? = null,
        revertOnExit: Boolean = false,
        runContext: WorkflowRunContext? = null,
        dataRuntime: ScopedDataRuntime? = null
    ): SystemControlResult {
        val capabilityRequest = CapabilityActionMapper.requestFor(
            action = action,
            workflowId = automationId,
            executionId = runContext?.runId
        )
        if (capabilityRequest != null && capabilityExecutionService != null) {
            val capabilityResult = capabilityExecutionService.execute(capabilityRequest)
            if (capabilityResult.status == CapabilityStatus.SUCCESS) {
                publishPluginOutputVariables(capabilityResult.metadata, runContext)
            }
            return if (
                capabilityResult.status == CapabilityStatus.SUCCESS ||
                capabilityResult.status == CapabilityStatus.PENDING_USER_ACTION
            ) {
                SystemControlResult.ok(capabilityResult.message)
            } else {
                SystemControlResult.fail(capabilityResult.message)
            }
        }

        val handler = actionRegistry.handlerFor(action.type)
            ?: return SystemControlResult.fail("No handler registered for ${action.type}")
        return try {
            handler.execute(
                action,
                ActionExecutionContext(
                    appContext = context,
                    controller = controller,
                    notificationSettings = notif,
                    channel = channel,
                    automationId = automationId,
                    revertOnExit = revertOnExit,
                    runContext = runContext,
                    dataRuntime = dataRuntime
                )
            )
        } catch (cancellation: CancellationException) {
            // Cancellation is control flow, not an action failure. Preserve the
            // caller's structured-concurrency contract.
            throw cancellation
        } catch (failure: Throwable) {
            // Extension and OEM handlers run outside the engine's trust boundary.
            // Convert an unexpected failure into a normal action result so the
            // automation is recorded and its one-shot exit lifecycle remains valid.
            SystemControlResult.fail(failure.message ?: "Action execution failed")
        }
    }

    /**
     * Makes Tasker setting outputs available to actions later in the same run as
     * `%CTX.pluginOutputs.<lower_case_name>`. Values remain execution-local.
     */
    private fun publishPluginOutputVariables(
        metadata: Map<String, String>,
        runContext: WorkflowRunContext?
    ) {
        val context = runContext ?: return
        val outputs = metadata
            .asSequence()
            .filter { (key, _) -> key.startsWith("pluginOutput.") }
            .associate { (key, value) -> key.removePrefix("pluginOutput.") to value }
        if (outputs.isEmpty()) return
        val merged = LinkedHashMap<String, Any?>()
        (context.get("$.pluginOutputs") as? Map<*, *>)
            ?.forEach { (key, value) -> if (key is String) merged[key] = value }
        merged.putAll(outputs)
        // The client bounds the collection and every value. The run-context
        // budget remains authoritative, and a rejected best-effort publication
        // must not turn a successful external action into a failure.
        runCatching { context.put("$.pluginOutputs", merged) }
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

    private fun ConditionResult.toGateMessage(): String = when (this) {
        ConditionResult.Satisfied -> "constraints satisfied"
        ConditionResult.Unsatisfied -> "constraints not met"
        ConditionResult.Unknown -> "constraint state is unknown"
        ConditionResult.Unavailable -> "constraint provider is unavailable"
        is ConditionResult.Error -> "constraint evaluation error: $reason"
    }

    private fun buildMessage(results: List<ActionExecutionResult>): String {
        if (results.isEmpty()) return "No actions configured"
        return results.joinToString(" | ") { it.message }
    }
}
