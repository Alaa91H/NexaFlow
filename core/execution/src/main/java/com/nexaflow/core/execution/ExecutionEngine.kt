package com.nexaflow.core.execution

import android.content.Context
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
import com.nexaflow.core.datastore.DurableVerificationState
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.capability.CapabilityActionMapper
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowPlanner
import com.nexaflow.core.execution.capability.toSystemControlResult
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.execution.variables.ScopedDataRuntime
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.core.execution.constraints.AutomationConstraintGate
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.canonical.AtomicCommand
import com.nexaflow.core.execution.compat.CanonicalCompatibilityActionDispatcher
import com.nexaflow.core.execution.compat.CanonicalRuntimeCutoverAdapter
import com.nexaflow.domain.canonical.CommandIdempotency
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.FaultInjectionController
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.canonical.ConditionLogic
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
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.SmsActivityRepository
import com.nexaflow.domain.repositories.VariableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 *
 * This class is intentionally the lifecycle orchestration boundary. Most
 * implementation work is already delegated to focused collaborators (registry,
 * admission gates, diagnostics, capability execution, progress tracking and
 * durable stores), while keeping run/exit ownership in one place prevents
 * lifecycle races. Suppress the size-only rule locally rather than weakening
 * LargeClass for the rest of the project.
 */
@Suppress("LargeClass")
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
    private val capabilitySnapshotProvider: (() -> CapabilitySnapshot)? = null,
    /** Current unified permission/authority observation. */
    private val privilegeSnapshotProvider: (() -> PrivilegeSnapshot)? = null,
    /** Targeted refresh after a fresh-snapshot block, so the next run sees a new grant. */
    private val capabilitySnapshotInvalidator: (() -> Unit)? = null,
    /** Targeted privilege refresh after an observed authorization block. */
    private val privilegeSnapshotInvalidator: (() -> Unit)? = null,
    /** Live semantic strategy planner; null preserves legacy/test construction. */
    private val semanticWorkflowPlanner: SemanticWorkflowPlanner? = null,
    /**
     * T26 product cutover boundary. Every trigger is canonicalized at run
     * admission and every side-effecting action is planned to an atomic
     * canonical command before a compatibility provider may execute it.
     */
    private val canonicalRuntimeCutover: CanonicalRuntimeCutoverAdapter =
        CanonicalRuntimeCutoverAdapter(),
    /**
     * T32 deterministic fault seam. Production is disabled; tests/device
     * diagnostics can target the exact canonical command before provider I/O.
     */
    private val faultInjectionGate: CanonicalFaultInjectionGate =
        CanonicalFaultInjectionGate.DISABLED,
    /** Bounded timeout used only for injected HANG faults. */
    private val faultInjectionHangTimeoutMs: Long = 1_000L,
    /** Test seam for deterministic snapshot-capture failure coverage. */
    private val snapshotCapture: () -> DeviceStateSnapshot = { DeviceStateSnapshot.capture(context) },
    /** Test seam for deterministic whole-snapshot restore outcome coverage. */
    private val snapshotRestorer: (DeviceStateSnapshot?, List<Action>) -> SystemControlResult =
        { snapshot, changedActions ->
            snapshot?.restore(context, changedActions) ?: SystemControlResult.ok("Nothing to restore")
        },
    /** Suppresses repeated durable-admission diagnostics from high-frequency triggers. */
    private val checkpointAdmissionReportThrottle: CheckpointAdmissionReportThrottle =
        CheckpointAdmissionReportThrottle(),
    private val smsActivityRepository: SmsActivityRepository? = null,
    /** Coalesces identical intentional skips emitted by noisy state monitors. */
    private val skipReportThrottle: ExecutionSkipReportThrottle =
        ExecutionSkipReportThrottle(),
    /** Typed trace sink (P0.4); defaults to the same LogStore the engine already writes. */
    private val traceRecorder: com.nexaflow.core.logging.TraceRecorder =
        com.nexaflow.core.logging.TraceRecorder(logStore),
    /** In-process per-action status for an open task-details screen. */
    private val executionProgressTracker: ExecutionProgressTracker =
        ExecutionProgressTracker(),
    /** Run lifecycle fan-out; failures never affect execution. */
    private val runListener: AutomationRunListener = AutomationRunListener.NO_OP,
    private val canonicalNodeDispatcher: com.nexaflow.core.execution.canonical.CanonicalNodeDispatcher? = null,
    private val canonicalTriggerDispatcher: com.nexaflow.core.execution.canonical.CanonicalTriggerDispatcher? = null
) {
    private val diagnostics = ExecutionDiagnostics(
        context = context,
        historyRepository = historyRepository,
        logStore = logStore,
        epochMillis = epochMillis,
        traceRecorder = traceRecorder,
    )
    /** The only remaining ActionType-based provider dispatch boundary. */
    private val compatibilityActionDispatcher =
        CanonicalCompatibilityActionDispatcher(actionRegistry)

    private val manualAdmissionEvaluator = ManualAdmissionEvaluator(
        context = context,
        capabilityExecutionService = capabilityExecutionService,
        constraintStateProvider = constraintStateProvider
    )

    private val historyWriter = ExecutionHistoryWriter(historyRepository, runListener)
    private val valueResolver = ExecutionValueResolver(context, variableRepository, epochMillis)
    private val recoveryLedger = ExecutionRecoveryLedger(activeExecutionStore)
    private val automationChangeNotifier = AutomationChangeNotifier(context)
    private val workflowAdmissionGate = WorkflowAdmissionGate(
        capabilitySnapshotProvider = capabilitySnapshotProvider,
        privilegeSnapshotProvider = privilegeSnapshotProvider,
        capabilitySnapshotInvalidator = capabilitySnapshotInvalidator,
        privilegeSnapshotInvalidator = privilegeSnapshotInvalidator,
        nowMs = epochMillis::now,
        freshnessMs = CAPABILITY_SNAPSHOT_FRESHNESS_MS
    )

    companion object {
        /** Prefix used by UI callers to present a manual condition rejection accurately. */
        const val MANUAL_CONDITION_NOT_MET_PREFIX = "Conditions not satisfied; "

        /** Prefix logged on user-forced runs so history shows the bypass. */
        const val MANUAL_FORCE_PREFIX = "Force run; "

        /**
         * A capability snapshot older than this is treated as stale for the
         * whole-run admission gate: it no longer blocks the run, because the
         * concrete privilege is re-verified live before each action executes.
         * Aligned with the store's 30s min-refresh interval plus generous
         * background dwell time.
         */
        const val CAPABILITY_SNAPSHOT_FRESHNESS_MS = 60_000L
    }

    /**
     * Snapshots captured for automations with revertOnExit, keyed by automation id.
     * Failed capture is represented by absence from the map; ConcurrentHashMap
     * does not permit null values.
     */
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, DeviceStateSnapshot>()

    /**
     * Runtime ledger for tasks whose main actions actually started. Monitors may
     * observe a trigger state before the constraint gate accepts it; an end
     * behavior must only run after a successful entry into the task lifecycle,
     * never merely because a trigger later flips back.
     */
    private val activeExecutions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Process-local single-flight admission per automation.
     *
     * The production ExecutionEngine is a singleton, so every monitor converges
     * on this boundary before any gate/checkpoint/side effect. A second callback
     * for the same automation while one run is still open is intentionally
     * skipped rather than queued: queueing would replay stale event evidence
     * after the triggering occurrence has already passed.
     *
     * Durable crash/recovery semantics remain owned by ActiveExecutionStore;
     * this set only closes the in-process concurrency race.
     */
    private val runningAutomationIds =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Same-process replay protection for sources with a trustworthy event id. */
    private val occurrenceDeduplicator = TriggerOccurrenceDeduplicator()

    /** Serializes the paired in-memory and durable exit-ledger consumption per task. */
    private val exitConsumptionLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** Live Update run-progress cards for executing tasks (user-gated). */
    private val runProgressNotifier: TaskRunProgressNotifier =
        TaskRunProgressNotifier(context, notificationPreferences)
    private val canonicalActionSequenceExecutor = canonicalNodeDispatcher?.let { dispatcher ->
        CanonicalActionSequenceExecutor(
            activeExecutionStore = activeExecutionStore,
            runProgressNotifier = runProgressNotifier,
            epochMillis = epochMillis,
            dispatcher = dispatcher,
        )
    }

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
        lifecycleContext: AutomationLifecycleContext? = null,
        /**
         * Ephemeral proof for the trigger event that started this evaluation.
         * Event-only triggers may be satisfied only by this current occurrence;
         * state-readable triggers are still verified live.
         */
        triggerOccurrence: TriggerOccurrence? = null,
        /** Explicit user-approved manual paths may bypass the automatic trigger gate. */
        bypassTriggerMatch: Boolean = false,
        /** Optional durable history label for explicit caller intent (for example Force Run). */
        recordMessagePrefix: String = ""
    ): ExecutionRecord {
        val startedAt = epochMillis.now()
        fun historyMessage(message: String): String =
            if (recordMessagePrefix.isBlank()) message
            else (recordMessagePrefix + message).take(500)
        // Allocate the run identity before admission gates. This lets blocked
        // runs correlate their durable history row with the structured trace
        // without timestamp guessing.
        val payloadContext = runContext ?: WorkflowRunContext.create(automation.id, startedAt)

        // Single-flight admission is intentionally process-local. The durable
        // checkpoint store handles crash recovery; this guard prevents two live
        // callbacks from producing two action chains for the same automation.
        if (!runningAutomationIds.add(automation.id)) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = historyMessage("Skipped: automation is already running"),
                executedAt = startedAt
            )
            if (skipReportThrottle.shouldReport(
                    automationId = automation.id,
                    reasonKey = "CONCURRENT_RUN",
                    now = startedAt
                )
            ) {
                historyWriter.record(record)
                diagnostics.recordTimeline(
                    automation = automation,
                    kind = "CONCURRENT_RUN_SKIPPED",
                    record = record,
                    startedAt = startedAt,
                    runId = payloadContext.runId
                )
                traceRecorder.recordBlockedRun(
                    payloadContext.runId,
                    automation.id,
                    TraceReasons.ADMISSION_REJECTED,
                    "automation is already running",
                    epochMillis.now()
                )
            }
            return record
        }

        // Strict mode: acquire wake lock for forceful execution (bypasses Doze, ensures CPU stays on)
        val wakeLock = acquireExecutionWakeLock(context, "NexaFlow:runAutomation:${automation.id}")
        try {
        if (!occurrenceDeduplicator.tryAdmit(automation.id, triggerOccurrence, startedAt)) {
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = historyMessage("Skipped: trigger occurrence was already processed"),
                executedAt = startedAt
            )
            if (skipReportThrottle.shouldReport(
                    automationId = automation.id,
                    reasonKey = "DUPLICATE_OCCURRENCE",
                    now = startedAt
                )
            ) {
                historyWriter.record(record)
                diagnostics.recordTimeline(
                    automation = automation,
                    kind = "DUPLICATE_OCCURRENCE_SKIPPED",
                    record = record,
                    startedAt = startedAt,
                    runId = payloadContext.runId
                )
                traceRecorder.recordBlockedRun(
                    payloadContext.runId,
                    automation.id,
                    TraceReasons.ADMISSION_REJECTED,
                    "trigger occurrence was already processed",
                    epochMillis.now()
                )
            }
            return record
        }

        if (automation.requiresTimeRangeForEndBehavior) {
            return diagnostics.rejectIncompleteTimeRange(automation, startedAt, payloadContext.runId)
        }

        // T26: canonical trigger admission happens before snapshots,
        // checkpoints, lifecycle ownership or any action side effect.
        val canonicalTriggerFailure = runCatching {
            automation.triggers.forEachIndexed { index, trigger ->
                canonicalRuntimeCutover.prepareTrigger(
                    trigger = trigger,
                    runId = payloadContext.runId,
                    instanceId = "v3.trigger.$index",
                )
            }
            val nativeTriggers = automation.canonicalNodes.filter { it.kind == NodeSchemaKind.TRIGGER }
            require(nativeTriggers.isEmpty() || automation.triggers.isEmpty()) {
                "mixed legacy and canonical triggers require unified evaluation"
            }
            if (nativeTriggers.isNotEmpty() && !bypassTriggerMatch) {
                val dispatcher = canonicalTriggerDispatcher ?: error("canonical trigger runtime is not registered")
                val result = dispatcher.evaluate(
                    nodes = nativeTriggers,
                    logic = if (automation.triggerMatch == com.nexaflow.domain.models.TriggerMatchMode.ALL) {
                        ConditionLogic.ALL
                    } else ConditionLogic.ANY,
                    occurrenceId = triggerOccurrence?.eventId,
                )
                require(result.decision == ConditionResult.Satisfied) {
                    "canonical trigger graph is not currently satisfied"
                }
            }
            val nativeActions = automation.canonicalNodes.filter { it.kind == NodeSchemaKind.ACTION }
            if (nativeActions.isNotEmpty()) {
                require(!automation.revertOnExit && nativeActions.none { it.endBehavior != null }) {
                    "canonical actions require registered compensation and end-behavior adapters"
                }
                (canonicalNodeDispatcher ?: error("canonical action runtime is not registered"))
                    .preflight(nativeActions)
            }
        }.exceptionOrNull()
        if (canonicalTriggerFailure != null) {
            val detail = canonicalTriggerFailure.message.orEmpty().take(240)
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = false,
                message = historyMessage("Canonical runtime refused trigger graph: $detail"),
                executedAt = startedAt,
            )
            historyWriter.record(record)
            diagnostics.recordTimeline(
                automation, "CANONICAL_TRIGGER_REJECTED", record, startedAt, payloadContext.runId
            )
            traceRecorder.recordBlockedRun(
                payloadContext.runId,
                automation.id,
                TraceReasons.ADMISSION_REJECTED,
                "canonical trigger graph rejected",
                epochMillis.now(),
            )
            return record
        }

        val admission = workflowAdmissionGate.evaluate(automation)
        when (admission.state) {
            WorkflowAdmissionState.ADMITTED -> Unit
            WorkflowAdmissionState.STALE_EVIDENCE_ADMITTED ->
                diagnostics.recordStaleRequirementAdmission(
                    automation = automation,
                    startedAt = startedAt,
                    runId = payloadContext.runId
                )
            WorkflowAdmissionState.BLOCKED ->
                return diagnostics.rejectUnavailableRequirements(
                    automation = automation,
                    startedAt = startedAt,
                    runId = payloadContext.runId,
                    missingDetail = admission.missingDetail
                )
        }
        semanticWorkflowPlanner?.plan(
            automation = automation,
            executionId = payloadContext.runId
        )?.takeIf { !it.executable }?.let { semanticPlan ->
            return diagnostics.rejectUnavailableSemanticRoutes(
                automation = automation,
                startedAt = startedAt,
                runId = payloadContext.runId,
                detail = semanticPlan.blockingDetail
            )
        }
        val maintenanceNow = ZonedDateTime.now()
        val maintenanceOccurrenceKey = MaintenanceExecutionIdentity.occurrenceKey(automation, maintenanceNow)
        // One typed, scoped facade per run. It layers over the existing payload
        // context and repository; no action accesses a raw variable store.
        val dataRuntime = variableRepository?.let { ScopedDataRuntime(payloadContext, it) }
        val controller = RomIntegrationManager.controller(context, smsActivityRepository, automation.id, automation.name)
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
                message = historyMessage("Skipped: maintenance occurrence already completed"),
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            if (skipReportThrottle.shouldReport(automation.id, "MAINTENANCE_DUPLICATE", startedAt)) {
                historyWriter.record(record)
            }
            diagnostics.recordTimeline(
                automation = automation,
                kind = "MAINTENANCE_DUPLICATE_SKIPPED",
                record = record,
                startedAt = startedAt,
                runId = payloadContext.runId
            )
            traceRecorder.recordBlockedRun(
                payloadContext.runId, automation.id, TraceReasons.MAINTENANCE_DUPLICATE,
                "maintenance occurrence already completed", epochMillis.now()
            )
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
                    message = historyMessage("Skipped: ${constraintResult.toGateMessage()}"),
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                if (skipReportThrottle.shouldReport(
                        automation.id, "CONSTRAINT:" + constraintResult.toGateMessage(), startedAt
                    )) historyWriter.record(record)
                diagnostics.recordTimeline(automation, "BLOCKED", record, startedAt, payloadContext.runId)
                traceRecorder.recordGateBlocked(
                    runId = payloadContext.runId,
                    automationId = automation.id,
                    reasonCode = com.nexaflow.core.logging.TraceReasons.CONSTRAINT_BLOCKED,
                    detail = constraintResult.toGateMessage(),
                    atEpochMs = startedAt,
                )
                return record
            }
        }
        // ALL-mode trigger gate: the firing monitor only starts the evaluation —
        // every configured trigger must be verifiably satisfied right now,
        // otherwise the run is an intentional skip (same semantics as a failed
        // constraint, never a failure). Evaluated after the constraint gate and
        // before any checkpoint so a rejected run performs no work and leaves
        // no queue residue. A single condition is live-evaluated too: a past
        // event is not current truth.
        if (!bypassTriggerMatch &&
            automation.triggerMatch == com.nexaflow.domain.models.TriggerMatchMode.ALL &&
            automation.triggers.isNotEmpty()
        ) {
            // Evaluate one coherent expression snapshot. Event-only triggers
            // can be proven by the occurrence that started this run; every
            // state-readable trigger is re-read live. A past event is never
            // carried forward as current truth.
            val triggerSnapshot = TriggerExpressionEvaluator.evaluate(
                context = context,
                automation = automation,
                occurrence = triggerOccurrence,
                evaluatedAtEpochMs = startedAt,
            )
            val gateResults = triggerSnapshot.results
            if (triggerSnapshot.decision(com.nexaflow.domain.models.TriggerMatchMode.ALL) !=
                ConditionResult.Satisfied
            ) {
                val semanticsReviewRequired =
                    TriggerMatchPolicy.requiresOccurrenceSemanticsReview(automation)
                val skipDetail = if (semanticsReviewRequired) {
                    "Skipped: legacy ALL event semantics require review and save before event matching can run"
                } else {
                    TriggerMatchPolicy.skipMessage(automation.triggers, gateResults)
                }
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = true,
                    message = historyMessage(skipDetail),
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                if (skipReportThrottle.shouldReport(
                        automation.id, "TRIGGER_ALL:" + skipDetail, startedAt
                    )) historyWriter.record(record)
                diagnostics.recordTimeline(
                    automation, "TRIGGER_ALL_GATE_BLOCKED", record, startedAt, payloadContext.runId
                )
                val reasonCode = if (semanticsReviewRequired) {
                    TraceReasons.TRIGGER_SEMANTICS_REVIEW_REQUIRED
                } else {
                    when (triggerSnapshot.blockKind()) {
                        TriggerBlockKind.UNSATISFIED -> TraceReasons.TRIGGER_AND_UNSATISFIED
                        TriggerBlockKind.ERROR -> TraceReasons.TRIGGER_STATE_ERROR
                        TriggerBlockKind.UNAVAILABLE -> TraceReasons.TRIGGER_STATE_UNAVAILABLE
                        TriggerBlockKind.UNKNOWN -> TraceReasons.TRIGGER_STATE_UNKNOWN
                        null -> TraceReasons.TRIGGER_ALL_GATE_BLOCKED
                    }
                }
                traceRecorder.recordGateBlocked(
                    runId = payloadContext.runId,
                    automationId = automation.id,
                    reasonCode = reasonCode,
                    detail = triggerSnapshot.diagnosticDetail(),
                    atEpochMs = startedAt,
                )
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
                message = historyMessage("Skipped: maintenance waiting for ${maintenanceReadiness.reason.name}"),
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            if (skipReportThrottle.shouldReport(
                    automation.id, "MAINTENANCE_WAITING:" + maintenanceReadiness.reason.name, startedAt
                )) historyWriter.record(record)
            diagnostics.recordTimeline(
                automation = automation,
                kind = "MAINTENANCE_WAITING",
                record = record,
                startedAt = startedAt,
                runId = payloadContext.runId
            )
            traceRecorder.recordGateBlocked(
                runId = payloadContext.runId,
                automationId = automation.id,
                reasonCode = com.nexaflow.core.logging.TraceReasons.MAINTENANCE_WAITING,
                detail = maintenanceReadiness.reason.name,
                atEpochMs = epochMillis.now(),
            )
            return record
        }
        // Checkpoint must exist before any side effect. A rejected durable
        // admission performs no work and is an intentional skip, never a
        // failed automation. The unresolved checkpoint remains preserved for
        // recovery rather than being silently discarded to make room.
        val checkpointAdmission = activeExecutionStore.admitCheckpoint(
            DurableExecutionCheckpoint(
                runId = payloadContext.runId,
                automationId = automation.id,
                workflowVersion = automation.workflowVersion,
                workflowRevision = automation.updatedAt,
                totalActions = automation.actions.size + automation.canonicalNodes.count { it.kind == NodeSchemaKind.ACTION },
                nextActionIndex = 0,
                status = DurableExecutionStatus.STARTED,
                startedAt = startedAt,
                updatedAt = startedAt
            )
        )
        if (checkpointAdmission != ActiveExecutionStore.CheckpointAdmission.ACCEPTED) {
            val admissionMessage = when (checkpointAdmission) {
                ActiveExecutionStore.CheckpointAdmission.DUPLICATE_RUN_ID ->
                    "Skipped: this event was already admitted and is still being processed"
                ActiveExecutionStore.CheckpointAdmission.CAPACITY_RESERVED_FOR_RECOVERY ->
                    "Skipped: recovery queue awaits review before this routine can run"
                ActiveExecutionStore.CheckpointAdmission.ACCEPTED -> error("Unreachable checkpoint admission")
            }
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = true,
                message = historyMessage(admissionMessage),
                executedAt = startedAt,
                channel = channel?.type?.name
            )
            if (checkpointAdmissionReportThrottle.shouldReport(
                    automationId = automation.id,
                    admission = checkpointAdmission,
                    now = startedAt
                )
            ) {
                historyWriter.record(record)
                diagnostics.recordTimeline(automation, "CHECKPOINT_REJECTED", record, startedAt, payloadContext.runId)
                traceRecorder.recordBlockedRun(
                    payloadContext.runId, automation.id, TraceReasons.ADMISSION_REJECTED,
                    admissionMessage.removePrefix("Skipped: ").trim(), epochMillis.now()
                )
            }
            return record
        }
        // Capture the device state when the run needs to restore anything on
        // exit: either the global revert-on-exit toggle or any action configured
        // with a per-action "restore original" end behavior. A failed snapshot
        // must never block the actual actions (e.g. an unreadable stream on an
        // unusual ROM used to abort the whole run before any action executed).
        val needsSnapshot = automation.revertOnExit ||
            automation.actions.any { it.endBehavior?.mode == EndMode.REVERT }
        val capturedSnapshot = if (needsSnapshot) {
            runCatching { snapshotCapture() }.getOrNull()
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
                // The action checkpoint was accepted only to reserve this
                // candidate run. No active marker has been armed yet, so a
                // losing concurrent occurrence cannot leave legacy lifecycle
                // state behind or cause a later duplicate exit.
                activeExecutionStore.completeCheckpoint(payloadContext.runId)
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    automationId = automation.id,
                    automationName = automation.name,
                    success = true,
                    message = historyMessage("Skipped: a prior automation lifecycle still requires cleanup"),
                    executedAt = startedAt,
                    channel = channel?.type?.name
                )
                historyWriter.record(record)
                diagnostics.recordTimeline(automation, "LIFECYCLE_CONFLICT", record, startedAt, payloadContext.runId)
                traceRecorder.recordBlockedRun(
                    payloadContext.runId, automation.id, TraceReasons.ADMISSION_REJECTED,
                    "a prior automation lifecycle still requires cleanup", epochMillis.now()
                )
                return record
            }
        }
        // Arm the legacy/in-memory exit marker only after durable lifecycle
        // admission has succeeded. For stateless/one-shot runs there is no
        // lifecycle claim, so reaching this point is the admission boundary.
        activeExecutions.add(automation.id)
        activeExecutionStore.markStarted(automation.id)
        historyWriter.triggered(automation.id, payloadContext.runId)

        // The durable admission succeeded (or this is a legacy/stateless run),
        // so this invocation may now own the in-memory restore snapshot too.
        if (needsSnapshot) {
            capturedSnapshot?.let { snapshots[automation.id] = it }
        }
        // Resolve %variables once per run (single repo read + device probe),
        // then apply pure string substitution per action.
        val variables = runCatching { valueResolver.snapshot() }.getOrDefault(emptyMap())
        var inProgressActionIndex: Int? = null
        // Live Update card for this run: a silent IMPORTANCE_MIN notification
        // that advances per action and disappears when the run ends. Shown
        // only after every admission gate passed, so blocked/skipped runs
        // never flash a card.
        val progressOutcomes = mutableListOf<Boolean>()
        executionProgressTracker.start(automation, startedAt)
        val totalExecutableActions = automation.actions.size + automation.canonicalNodes.count { it.kind == NodeSchemaKind.ACTION }
        runCatching { runProgressNotifier.start(automation, totalExecutableActions) }
        // A checkpoint is removed only after a fully known action chain. Once
        // an action starts and the coroutine is interrupted, its side effect
        // may have happened; startup recovery must be able to claim that
        // checkpoint rather than losing the evidence in this invocation's
        // finally block.
        var checkpointRequiresRecovery = false
        var actionChainCompleted = false
        val results = try {
            val list = mutableListOf<ActionExecutionResult>()
            for ((actionIndex, action) in automation.actions.withIndex()) {
                inProgressActionIndex = actionIndex
                executionProgressTracker.markRunning(automation.id, actionIndex)
                val actionStartedAt = epochMillis.now()
                val idempotencyKey = "${payloadContext.runId}:$actionIndex:${action.type.name}"
                activeExecutionStore.markActionStarted(
                    runId = payloadContext.runId,
                    actionIndex = actionIndex,
                    idempotencyKey = idempotencyKey,
                    updatedAt = actionStartedAt
                ) ?: error("Missing durable checkpoint for run ${payloadContext.runId}")
                // Advance the Live Update card: this step becomes active and
                // earlier steps are colored by their recorded outcomes.
                runCatching {
                    runProgressNotifier.update(automation, totalExecutableActions, actionIndex, progressOutcomes)
                }
                // Actions run sequentially and each handler may publish to the
                // shared context (Step 4), so %CTX selectors are resolved here —
                // after the previous node ran, before this node dispatches.
                val resolved = valueResolver.resolve(action, variables, payloadContext)

                // 1. Condition evaluation: skip action if condition is false
                val conditionExpr = resolved.config["condition"]?.trim()
                if (!conditionExpr.isNullOrEmpty() &&
                    !com.nexaflow.domain.workflow.ConditionExpressionEvaluator.evaluate(conditionExpr)
                ) {
                    activeExecutionStore.markActionCompleted(
                        runId = payloadContext.runId,
                        actionIndex = actionIndex,
                        updatedAt = epochMillis.now()
                    ) ?: error("Unable to commit durable checkpoint for run ${payloadContext.runId}")
                    progressOutcomes.add(true)
                    inProgressActionIndex = null
                    executionProgressTracker.markSkipped(automation.id, actionIndex)
                    list.add(
                        ActionExecutionResult(
                            actionType = action.type.name,
                            success = true,
                            message = historyMessage("Skipped: condition not satisfied ($conditionExpr)"),
                            durationMs = epochMillis.now() - actionStartedAt
                        )
                    )
                    continue
                }

                // 2. Canonical T26 planning + retry safety. No legacy
                // handler is reached until the resolved action has produced one
                // typed atomic command.
                val prepared = runCatching {
                    canonicalRuntimeCutover.prepareAction(
                        action = resolved,
                        runId = payloadContext.runId,
                        instanceId = "v3.action.$actionIndex",
                    )
                }
                val canonicalFailure = prepared.exceptionOrNull()
                val canonicalAction = prepared.getOrNull()
                val configuredRetryCount =
                    resolved.config["retryCount"]?.toIntOrNull()?.coerceIn(0, 5) ?: 0
                // Blind retries are permitted only for commands whose
                // idempotency is explicitly proven. Conditional and
                // non-idempotent commands get one attempt until a provider
                // exposes a stronger idempotency contract.
                val retryCount = if (
                    canonicalAction?.command?.idempotency == CommandIdempotency.IDEMPOTENT
                ) {
                    configuredRetryCount
                } else {
                    0
                }
                val retryDelayMs =
                    resolved.config["retryDelayMs"]?.toLongOrNull()?.coerceIn(0, 10_000L) ?: 500L
                var currentAttempt = 0
                var result: SystemControlResult
                if (canonicalFailure != null || canonicalAction == null) {
                    result = SystemControlResult.fail(
                        "Canonical runtime refused action: " +
                            canonicalFailure?.message.orEmpty().take(200)
                    )
                } else {
                    while (true) {
                        currentAttempt++
                        result = executeCanonicalCommandWithFaultInjection(
                            command = canonicalAction.command,
                        ) {
                            executeAction(
                                resolved,
                                controller,
                                notif,
                                channel,
                                automation.id,
                                automation.revertOnExit,
                                payloadContext,
                                dataRuntime,
                                executionId = payloadContext.runId,
                                canonicalCommand = canonicalAction.command,
                            )
                        }
                        // UNKNOWN means the backend may already have applied the
                        // side effect. Never blind-retry it: preserve the durable
                        // ACTION_UNKNOWN checkpoint and let reconciliation decide.
                        if (
                            result.success ||
                            result.outcomeUncertain ||
                            currentAttempt > retryCount
                        ) break
                        kotlinx.coroutines.delay(retryDelayMs)
                    }
                }

                if (result.outcomeUncertain) {
                    checkpointRequiresRecovery = true
                    activeExecutionStore.markActionUnknown(
                        runId = payloadContext.runId,
                        message = "Action $actionIndex outcome is unconfirmed: ${result.message.take(160)}",
                        updatedAt = epochMillis.now()
                    ) ?: error("Unable to preserve uncertain checkpoint for run ${payloadContext.runId}")
                } else if (result.success) {
                    activeExecutionStore.markActionCompleted(
                        runId = payloadContext.runId,
                        actionIndex = actionIndex,
                        updatedAt = epochMillis.now(),
                        verificationState = when {
                            result.verified == true -> DurableVerificationState.VERIFIED
                            result.verificationAttempted -> DurableVerificationState.UNKNOWN
                            else -> DurableVerificationState.NOT_REQUIRED
                        }
                    ) ?: error("Unable to commit durable checkpoint for run ${payloadContext.runId}")
                } else {
                    activeExecutionStore.markActionFailed(
                        runId = payloadContext.runId,
                        actionIndex = actionIndex,
                        updatedAt = epochMillis.now(),
                        failureCode = result.errorCode ?: "ACTION_FAILED",
                        verificationState = when {
                            result.verified == false -> DurableVerificationState.FAILED
                            result.verified == true -> DurableVerificationState.VERIFIED
                            result.verificationAttempted -> DurableVerificationState.UNKNOWN
                            else -> DurableVerificationState.NOT_REQUIRED
                        }
                    ) ?: error("Unable to commit failed durable action for run ${payloadContext.runId}")
                }
                progressOutcomes.add(result.success)
                inProgressActionIndex = null
                if (result.outcomeUncertain) {
                    executionProgressTracker.markUnknown(automation.id, actionIndex)
                } else {
                    executionProgressTracker.markResult(
                        automation.id,
                        actionIndex,
                        result,
                        channel?.type?.name
                    )
                }
                val execResult = ActionExecutionResult(
                    actionType = action.type.name,
                    success = result.success,
                    message = result.message,
                    durationMs = epochMillis.now() - actionStartedAt,
                    channel = result.executionChannel ?: channel?.type?.name,
                    errorCode = result.errorCode,
                    verificationAttempted = result.verificationAttempted,
                    verified = result.verified,
                    outcomeUncertain = result.outcomeUncertain
                )
                list.add(execResult)

                // 3. An uncertain side effect is always a hard safety stop:
                // continuing could compound an effect whose state is unknown.
                // Definite failures retain the configured OnError policy.
                if (result.outcomeUncertain ||
                    (!result.success &&
                        resolved.config["onError"]?.equals("ABORT", ignoreCase = true) == true)
                ) {
                    break
                }
            }
            if (list.size == automation.actions.size && !checkpointRequiresRecovery) {
                val nativeActions = automation.canonicalNodes
                    .filter { it.kind == NodeSchemaKind.ACTION }
                    .sortedBy { it.sequenceIndex }
                if (nativeActions.isNotEmpty()) {
                    val executor = canonicalActionSequenceExecutor
                        ?: error("canonical action runtime is not registered")
                    val result = executor.execute(
                        automation = automation,
                        nodes = nativeActions,
                        runId = payloadContext.runId,
                        firstActionIndex = automation.actions.size,
                        totalActions = totalExecutableActions,
                        progressOutcomes = progressOutcomes,
                        onCurrentActionChanged = { inProgressActionIndex = it },
                    )
                    list += result.actions
                    checkpointRequiresRecovery = result.checkpointRequiresRecovery
                }
            }
            list.also { actionChainCompleted = !checkpointRequiresRecovery && it.size == totalExecutableActions }
        } catch (cancellation: CancellationException) {
            // The process may have interrupted a side effect after it began.
            // Persist this classification in NonCancellable: otherwise the
            // cancelled caller context can abort the DataStore write and the
            // finally block would erase the only recovery evidence.
            inProgressActionIndex?.let { index ->
                checkpointRequiresRecovery = true
                executionProgressTracker.markUnknown(automation.id, index)
                withContext(NonCancellable) {
                    runCatching {
                        activeExecutionStore.markActionUnknown(
                            runId = payloadContext.runId,
                            message = "Action $index interrupted before durable completion",
                            updatedAt = epochMillis.now()
                        )
                    }
                }
            }
            throw cancellation
        } catch (failure: Throwable) {
            inProgressActionIndex?.let { index ->
                checkpointRequiresRecovery = true
                executionProgressTracker.markUnknown(automation.id, index)
                withContext(NonCancellable) {
                    runCatching {
                        activeExecutionStore.markActionUnknown(
                            runId = payloadContext.runId,
                            message = "Action $index crashed: ${failure.message?.take(100)}",
                            updatedAt = epochMillis.now()
                        )
                    }
                }
            }
            throw failure
        } finally {
            // Progress UI is process-local and can be cleaned immediately.
            // The durable checkpoint deliberately stays until the main history
            // row (and any one-shot exit) is committed below.
            executionProgressTracker.finish(automation.id)
            runCatching { runProgressNotifier.finish(automation.id) }
        }
        // Actions are executed sequentially, so a SYSTEM_WAIT action placed anywhere
        // pauses the chain for the configured duration (counter mode).
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = historyMessage(buildExecutionMessage(results)),
            executedAt = startedAt,
            channel = channel?.type?.name,
            actionResults = results
        )
        historyWriter.record(record)
        // History is the durable commit point for a known main-action chain.
        // Never remove the checkpoint before this succeeds: a process death in
        // that window would otherwise lose both the run evidence and recovery
        // ownership after a side effect already happened.
        if (record.success && maintenanceOccurrenceKey != null) {
            try {
                activeExecutionStore.recordCompletedMaintenanceOccurrence(
                    occurrenceKey = maintenanceOccurrenceKey,
                    automationId = automation.id,
                    completedAt = epochMillis.now()
                )
            } catch (failure: Throwable) {
                activeExecutionStore.markRecoveryRequired(
                    runId = payloadContext.runId,
                    message = "Main history committed but maintenance completion receipt failed",
                    updatedAt = epochMillis.now()
                )
                throw failure
            }
        }
        // A one-shot exit is meaningful only after the entire main chain is
        // known and its history row is durable. Preserve EXIT_PENDING until the
        // end behavior itself commits a successful history record; a known
        // failure becomes explicit recovery work instead of disappearing.
        if (actionChainCompleted && (completeExitOnFinish || automation.completesExitOnFinish)) {
            activeExecutionStore.markExitPending(payloadContext.runId, epochMillis.now())
            val exitRecord = withContext(NonCancellable) { runExit(automation) }
            if (!exitRecord.success) {
                checkpointRequiresRecovery = true
                activeExecutionStore.markRecoveryRequired(
                    runId = payloadContext.runId,
                    message = "One-shot exit failed after main history commit: ${exitRecord.message.take(160)}",
                    updatedAt = epochMillis.now()
                )
            }
        }
        if (!checkpointRequiresRecovery) {
            activeExecutionStore.completeCheckpoint(payloadContext.runId)
        }
        diagnostics.recordTimeline(
            automation = automation,
            kind = "RUN",
            record = record,
            startedAt = startedAt,
            runId = payloadContext.runId
        )
        traceRecorder.recordRunOutcome(
            payloadContext.runId, automation.id, results, record.channel, startedAt, epochMillis.now()
        )
        automationChangeNotifier.notifyChanged()
        return record
        } finally {
            runningAutomationIds.remove(automation.id)
            try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        }
    }

    /**
     * Manual "run now" gate. A mismatch is side-effect free: it never silently
     * turns a request to run the main task into an end-behavior execution.
     * Callers that explicitly want to preview/run the configured end behavior
     * must use [runManualEndBehavior] after a dedicated user action.
     */
    suspend fun runWithConditionGate(automation: Automation): ExecutionRecord {
        val startedAt = epochMillis.now()
        if (automation.requiresTimeRangeForEndBehavior) {
            return diagnostics.rejectIncompleteTimeRange(
                automation = automation,
                startedAt = startedAt,
                runId = WorkflowRunContext.create(automation.id, startedAt).runId
            )
        }
        if (manualAdmissionEvaluator.describe(automation).kind == ManualBlockKind.NONE) {
            return runAutomation(automation, bypassTriggerMatch = true)
        }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = true,
            message = "Skipped: manual conditions not satisfied",
            executedAt = startedAt
        )
        historyWriter.record(record)
        diagnostics.recordTimeline(
            automation = automation,
            kind = "MANUAL_CONDITION_BLOCKED",
            record = record,
            startedAt = startedAt
        )
        return record
    }

    /**
     * Explicit manual end-behavior command. This is intentionally separate
     * from [runWithConditionGate] so a trigger mismatch can never execute end
     * actions unless the user chose that operation in the mismatch dialog.
     */
    suspend fun runManualEndBehavior(automation: Automation): ExecutionRecord =
        runExit(
            automation = automation,
            forceConfiguredEnd = true,
            manualConditionRejected = true
        )

    /**
     * Typed, UI-presentable explanation of why a manual run was rejected by
     * the admission gate. Evaluates the same checks as [runWithConditionGate]
     * and returns at most one primary reason plus every trigger-level detail:
     * an explicit user question ("why can this not run?") deserves the full
     * picture rather than the first failure alone.
     */
    suspend fun describeManualBlock(automation: Automation): ManualBlockReason =
        manualAdmissionEvaluator.describe(automation)

    /** Side-effect-free live status for every trigger and constraint row. */
    suspend fun diagnoseManualAdmission(automation: Automation): ManualAdmissionDiagnostics = manualAdmissionEvaluator.diagnostics(automation)

    /** Live main-chain progress for the selected automation, if a run exists. */
    fun observeExecutionProgress(automationId: String): Flow<AutomationExecutionProgress?> = executionProgressTracker.observe(automationId)

    /**
     * Explicit user override of the manual admission gate: skips trigger and
     * constraint checks entirely and runs the main chain. Only reachable from
     * an explicit confirmation dialog. The decision is durably logged so the
     * history shows the run was user-forced, not trigger-driven.
     */
    suspend fun forceRun(automation: Automation): ExecutionRecord =
        runAutomation(
            automation = automation,
            bypassTriggerMatch = true,
            recordMessagePrefix = MANUAL_FORCE_PREFIX
        )

    /**
     * Disable-path handoff used by UI surfaces.
     *
     * A stateful monitor-owned occurrence must be closed by ExitCoordinator;
     * running [runExit] directly here would bypass the atomic lifecycle claim
     * and can double-apply end behavior when the monitor reconciles the same
     * disable. Legacy/stateless tasks have no runtime occurrence, so they keep
     * the historical immediate end-behavior semantics.
     *
     * Returns null when cleanup is delegated to the owning monitor.
     */
    suspend fun runDisableCleanup(automation: Automation): ExecutionRecord? {
        if (automationRuntimeStore.current(automation.id) != null) {
            notifyAutomationsChanged()
            return null
        }
        return runExit(automation, forceConfiguredEnd = true)
    }

    /**
     * Pre-delete cleanup barrier.
     *
     * Callers must first persist the task as disabled so monitor-owned
     * lifecycles can observe that policy change while the immutable definition
     * still exists. Stateless/legacy cleanup completes synchronously through
     * [runDisableCleanup]. Stateful cleanup is delegated to the owning monitor
     * and this method waits only for the durable occurrence to disappear.
     * EXIT_FAILED or a timeout blocks deletion so recoverable state is never
     * discarded merely because the user tapped Delete.
     */
    suspend fun prepareForDeletion(
        automation: Automation,
        timeoutMillis: Long = 15_000L
    ): Boolean {
        require(timeoutMillis > 0L) { "timeoutMillis must be positive" }

        val direct = runDisableCleanup(automation)
        if (direct != null) return direct.success

        val deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            val state = automationRuntimeStore.current(automation.id) ?: return true
            if (state.lifecycleState == AutomationRuntimeLifecycleState.EXIT_FAILED) {
                return false
            }
            delay(50L)
        }
        return automationRuntimeStore.current(automation.id) == null
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
        val wakeLock = acquireExecutionWakeLock(context, "NexaFlow:runExit:${automation.id}")
        try {
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
            if (skipReportThrottle.shouldReport(automation.id, "EXIT_NOT_ACTIVE", startedAt)) {
                historyWriter.record(record)
            }
            diagnostics.recordTimeline(automation, "EXIT_SKIPPED", record, startedAt)
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
            historyWriter.record(record)
            diagnostics.recordTimeline(
                automation,
                if (manualConditionRejected) "MANUAL_CONDITION_NOT_MET" else "EXIT",
                record,
                startedAt
            )
            return record
        }
        val controller = RomIntegrationManager.controller(context, smsActivityRepository, automation.id, automation.name)
        val notif = notificationPreferences.settings.first()
        val channel = channelSelector.select(context)
        val exitExecutionId = UUID.randomUUID().toString()
        // revertOnExit deliberately supersedes both per-action end behaviors and
        // exitActions: the whole device state is restored instead. Do not "fix"
        // this to run them too — that would double-apply end actions after a revert.
        val actionResults = if (automation.revertOnExit) {
            val snapshot = snapshots[automation.id]
                ?: DeviceStateSnapshot.decodeForRuntime(runtimeSnapshotJson)
            val restoreResult = snapshotRestorer(snapshot, automation.actions)
            listOf(
                ActionExecutionResult(
                    actionType = "STATE_RESTORE",
                    success = restoreResult.success,
                    message = restoreResult.message,
                    durationMs = 0,
                    channel = restoreResult.executionChannel ?: channel?.type?.name,
                    errorCode = restoreResult.errorCode,
                    verificationAttempted = restoreResult.verificationAttempted,
                    verified = restoreResult.verified,
                    outcomeUncertain = restoreResult.outcomeUncertain
                )
            )
        } else {
            mutableListOf<ActionExecutionResult>().apply {
                // %variables resolved only when exit actions actually run — pure
                // revert tasks never pay the extra repo read + device probe.
                val variables = runCatching { valueResolver.snapshot() }.getOrDefault(emptyMap())
                // Adaptive per-action end behavior: each action configured with an
                // end behavior (leave / restore original / set a specific value)
                // is honored exactly as configured, before the custom exit actions.
                val snapshot = snapshots.remove(automation.id)
                    ?: DeviceStateSnapshot.decodeForRuntime(runtimeSnapshotJson)
                automation.actions.forEachIndexed { actionIndex, action ->
                    val behavior = action.endBehavior ?: return@forEachIndexed
                    val actionStartedAt = epochMillis.now()
                    val result: SystemControlResult = when (behavior.mode) {
                        EndMode.LEAVE -> null
                        EndMode.REVERT -> snapshot?.restoreSetting(context, action)
                            ?: SystemControlResult.fail("No captured state to restore for ${action.type.name}")
                        EndMode.RERUN -> executeCanonicalCompatibilityAction(
                            action = valueResolver.resolve(action, variables),
                            controller = controller,
                            notif = notif,
                            channel = channel,
                            automationId = automation.id,
                            revertOnExit = automation.revertOnExit,
                            executionId = exitExecutionId,
                            instanceId = "v3.end.$actionIndex"
                        )
                        EndMode.SET_VALUE -> executeCanonicalCompatibilityAction(
                            action = valueResolver.resolve(action.withConfig(behavior.config), variables),
                            controller = controller,
                            notif = notif,
                            channel = channel,
                            automationId = automation.id,
                            revertOnExit = automation.revertOnExit,
                            executionId = exitExecutionId,
                            instanceId = "v3.end.$actionIndex"
                        )
                    } ?: return@forEachIndexed
                    add(
                        ActionExecutionResult(
                            actionType = "${action.type.name}_END",
                            success = result.success,
                            message = result.message,
                            durationMs = epochMillis.now() - actionStartedAt,
                            channel = result.executionChannel ?: channel?.type?.name,
                            errorCode = result.errorCode,
                            verificationAttempted = result.verificationAttempted,
                            verified = result.verified,
                            outcomeUncertain = result.outcomeUncertain
                        )
                    )
                }
                // The explicitly configured exit actions run last.
                automation.exitActions.forEachIndexed { exitIndex, action ->
                    val actionStartedAt = epochMillis.now()
                    val result = executeCanonicalCompatibilityAction(
                        action = valueResolver.resolve(action, variables),
                        controller = controller,
                        notif = notif,
                        channel = channel,
                        automationId = automation.id,
                        revertOnExit = automation.revertOnExit,
                        executionId = exitExecutionId,
                        instanceId = "v3.exit.$exitIndex"
                    )
                    add(
                        ActionExecutionResult(
                            actionType = action.type.name,
                            success = result.success,
                            message = result.message,
                            durationMs = epochMillis.now() - actionStartedAt,
                            channel = result.executionChannel ?: channel?.type?.name,
                            errorCode = result.errorCode,
                            verificationAttempted = result.verificationAttempted,
                            verified = result.verified,
                            outcomeUncertain = result.outcomeUncertain
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
                MANUAL_CONDITION_NOT_MET_PREFIX + "end behavior: ${buildExecutionMessage(actionResults)}"
            } else {
                buildExecutionMessage(actionResults)
            },
            executedAt = startedAt,
            channel = channel?.type?.name,
            actionResults = actionResults
        )
        // Exit side effects are already decided at this point. A history
        // persistence failure must never turn a known-successful exit into a
        // retryable execution failure: replaying the end behavior could duplicate
        // an external side effect. Keep the returned action outcome authoritative
        // and surface the history failure through diagnostics instead.
        val historyFailure = runCatching { historyWriter.record(record) }.exceptionOrNull()
        diagnostics.recordTimeline(
            automation,
            if (historyFailure != null) {
                "EXIT_HISTORY_PERSIST_FAILED"
            } else if (manualConditionRejected) {
                "MANUAL_CONDITION_NOT_MET"
            } else {
                "EXIT"
            },
            record,
            startedAt
        )
        if (record.success) {
            // The captured state is consumed only after the exit itself is known
            // to have succeeded. A definitive failed restore/end action retains
            // the snapshot for explicit or coordinator-owned recovery.
            snapshots.remove(automation.id)
        }
        automationChangeNotifier.notifyChanged()
        return record
        } finally {
            try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        }
    }

    /** Discards any stored snapshot (e.g. when the automation is deleted). */
    suspend fun clearSnapshot(automationId: String) {
        snapshots.remove(automationId)
        activeExecutions.remove(automationId)
        executionProgressTracker.clear(automationId)
        activeExecutionStore.clear(automationId)
    }

    /** Current unresolved recovery count from the durable checkpoint ledger. */
    suspend fun recoveryBacklogCount(automationId: String): Int =
        recoveryLedger.backlogCount(automationId)

    /**
     * Read-only recovery evidence for diagnostics/UI. No claim, retry or
     * acknowledgement occurs here.
     */
    suspend fun recoveryReviewItems(automationId: String): List<RecoveryReviewItem> =
        recoveryLedger.reviewItems(automationId)

    /**
     * Discards recovery records that the user explicitly acknowledged for one
     * automation. This does not retry uncertain work or mark it successful.
     */
    suspend fun clearRecoveryBacklog(automationId: String): Int =
        recoveryLedger.clear(automationId)

    /**
     * Single owner of the engine-side half of deleting an automation. Call
     * after the row has been removed from the repository: drops captured
     * device state, active-run/checkpoint evidence, runtime lifecycle ownership,
     * and pending schedule identities for [automationId], then broadcasts
     * [ACTION_AUTOMATIONS_CHANGED]. Missing definitions discovered accidentally
     * are preserved for review elsewhere; explicit user deletion is different:
     * it is the deliberate policy boundary that makes this id unreachable.
     */
    suspend fun onAutomationDeleted(automationId: String) {
        snapshots.remove(automationId)
        activeExecutions.remove(automationId)
        executionProgressTracker.clear(automationId)

        // Explicit user deletion is the policy-resolution boundary: no future
        // screen/monitor can address this id, so retain no unreachable durable
        // execution, lifecycle, or scheduled-occurrence state.
        activeExecutionStore.clearAutomationState(automationId)
        automationRuntimeStore.clear(automationId)
        automationRuntimeStore.clearSchedule(automationId)

        notifyAutomationsChanged()
    }

    /**
     * Broadcasts [ACTION_AUTOMATIONS_CHANGED] after the automation set or an
     * enabled flag changed without an execution (dashboard/details toggles,
     * saves). MonitoringService listens and re-evaluates every stateful
     * monitor against the current device state, so a freshly enabled task
     * whose condition already holds runs immediately and a task disabled
     * while active runs its end behavior right away.
     */
    fun notifyAutomationsChanged() = automationChangeNotifier.notifyChanged()

    /**
     * T32 fault boundary over the real canonical command. ACTION_STARTED is
     * already durable when this runs, so injected timeout/cancellation drives
     * the same recovery state machine as a real provider interruption.
     */
    private suspend fun executeCanonicalCommandWithFaultInjection(
        command: AtomicCommand,
        dispatch: suspend () -> SystemControlResult,
    ): SystemControlResult = when (val decision = faultInjectionGate.decide(command)) {
        FaultInjectionController.Decision.Pass,
        is FaultInjectionController.Decision.Refused -> dispatch()

        is FaultInjectionController.Decision.Inject -> when (decision.action) {
            FaultInjectionController.FaultAction.FAIL -> SystemControlResult.fail(
                message = "Injected fault: ${decision.errorLabel}",
                executionChannel = "FAULT_INJECTION",
                errorCode = "FAULT_INJECTED",
            )

            FaultInjectionController.FaultAction.HANG -> try {
                withTimeout(faultInjectionHangTimeoutMs.coerceAtLeast(1L)) {
                    awaitCancellation()
                }
            } catch (_: TimeoutCancellationException) {
                SystemControlResult.fail(
                    message = "Injected timeout: ${decision.errorLabel}",
                    executionChannel = "FAULT_INJECTION",
                    errorCode = "FAULT_TIMEOUT",
                    outcomeUncertain = true,
                )
            }

            FaultInjectionController.FaultAction.STALL -> {
                // Deliberately suspend until the owning run is cancelled. The
                // outer cancellation handler persists ACTION_UNKNOWN in a
                // NonCancellable context, exactly like process/service death.
                awaitCancellation()
            }
        }
    }

    /**
     * T26/T39 compatibility-provider bridge for exit/end actions. Canonical
     * planning is mandatory; the historical Action object is only the provider
     * payload until every backend accepts AtomicCommand directly.
     */
    private suspend fun executeCanonicalCompatibilityAction(
        action: Action,
        controller: SystemController,
        notif: NotificationSettings,
        channel: ExecutionProvider?,
        automationId: String,
        executionId: String,
        instanceId: String,
        revertOnExit: Boolean = false,
        runContext: WorkflowRunContext? = null,
        dataRuntime: ScopedDataRuntime? = null,
    ): SystemControlResult {
        val prepared = runCatching {
            canonicalRuntimeCutover.prepareAction(
                action = action,
                runId = executionId,
                instanceId = instanceId,
            )
        }.getOrElse { failure ->
            return SystemControlResult.fail(
                "Canonical runtime refused action: ${failure.message.orEmpty().take(200)}"
            )
        }
        return executeAction(
            action = action,
            controller = controller,
            notif = notif,
            channel = channel,
            automationId = automationId,
            revertOnExit = revertOnExit,
            runContext = runContext,
            dataRuntime = dataRuntime,
            executionId = executionId,
            canonicalCommand = prepared.command,
        )
    }

    private suspend fun executeAction(
        action: Action,
        controller: SystemController,
        notif: NotificationSettings,
        channel: ExecutionProvider?,
        automationId: String? = null,
        revertOnExit: Boolean = false,
        runContext: WorkflowRunContext? = null,
        dataRuntime: ScopedDataRuntime? = null,
        executionId: String? = runContext?.runId,
        canonicalCommand: AtomicCommand,
    ): SystemControlResult {
        val capabilityRequest = CapabilityActionMapper.requestFor(
            action = action,
            workflowId = automationId,
            executionId = executionId
        )
        if (capabilityRequest != null && capabilityExecutionService != null) {
            val capabilityResult = capabilityExecutionService.execute(capabilityRequest)
            if (capabilityResult.status == CapabilityStatus.SUCCESS) {
                publishPluginOutputVariables(capabilityResult.metadata, runContext)
            }
            return capabilityResult.toSystemControlResult()
        }

        return compatibilityActionDispatcher.dispatch(
            action = action,
            canonicalCommand = canonicalCommand,
            executionContext = ActionExecutionContext(
                appContext = context,
                controller = controller,
                notificationSettings = notif,
                channel = channel,
                automationId = automationId,
                executionId = executionId,
                nodeId = canonicalCommand.commandId,
                revertOnExit = revertOnExit,
                runContext = runContext,
                dataRuntime = dataRuntime,
                capabilityService = capabilityExecutionService,
            ),
        )
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

    private fun ConditionResult.toGateMessage(): String = when (this) {
        ConditionResult.Satisfied -> "constraints satisfied"
        ConditionResult.Unsatisfied -> "constraints not met"
        ConditionResult.Unknown -> "constraint state is unknown"
        ConditionResult.Unavailable -> "constraint provider is unavailable"
        is ConditionResult.Error -> "constraint evaluation error: $reason"
    }


}
