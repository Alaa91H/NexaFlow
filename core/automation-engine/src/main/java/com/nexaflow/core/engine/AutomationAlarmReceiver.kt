package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives immutable time-occurrence alarms and process/clock recovery events.
 * The receiver holds `goAsync()` and a bounded wake lock while it delegates to
 * the durable lifecycle coordinator; an alarm intent itself is never trusted as
 * proof that it may start or end a currently configured occurrence.
 */
@AndroidEntryPoint
class AutomationAlarmReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var repository: AutomationRepository

    @Inject
    lateinit var executionEngine: ExecutionEngine

    @Inject
    lateinit var exitCoordinator: ExitCoordinator

    @Inject
    lateinit var scheduler: AutomationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            restoreAfterBoot(context)
            return
        }
        // Android cancels future exact alarms when this access changes. The
        // scheduler may already be initialized in the current process, so
        // initialize() alone is intentionally insufficient here: explicitly
        // recompute and re-arm every enabled time task after a grant.
        if (intent.action in rescheduleActions) {
            rescheduleAll(context)
            return
        }
        if (intent.action == MonitoringService.ACTION_START_MONITORING) {
            // Fired by the alarm scheduled in restoreAfterBoot: Android 15+
            // forbids launching time-limited FGS types from BOOT_COMPLETED.
            runCatching { MonitoringService.start(context.applicationContext) }
                .onFailure { Log.e(TAG, "Failed to start background trigger monitoring", it) }
            return
        }
        if (intent.action != AutomationScheduler.ACTION_RUN_AUTOMATION &&
            intent.action != AutomationScheduler.ACTION_END_AUTOMATION
        ) return

        val automationId = intent.getStringExtra(AutomationScheduler.EXTRA_AUTOMATION_ID) ?: return
        val occurrenceId = intent.getStringExtra(AutomationScheduler.EXTRA_OCCURRENCE_ID) ?: run {
            Log.w(TAG, "Ignoring legacy alarm without occurrence identity")
            return
        }
        val generation = intent.getStringExtra(AutomationScheduler.EXTRA_SCHEDULE_GENERATION) ?: run {
            Log.w(TAG, "Ignoring alarm without schedule generation")
            return
        }
        val windowStartAt = intent.getLongExtra(AutomationScheduler.EXTRA_WINDOW_START_AT, -1L)
        if (windowStartAt < 0L) {
            Log.w(TAG, "Ignoring alarm without window start")
            return
        }
        val alarmAction = intent.action ?: return
        val retryCount = intent.getIntExtra(AutomationScheduler.EXTRA_RETRY_COUNT, 0)
        val isEndOfWindow = alarmAction == AutomationScheduler.ACTION_END_AUTOMATION
        val windowEndAt = if (intent.hasExtra(AutomationScheduler.EXTRA_WINDOW_END_AT)) {
            intent.getLongExtra(AutomationScheduler.EXTRA_WINDOW_END_AT, -1L).takeIf { it >= windowStartAt }
        } else {
            null
        }
        if (isEndOfWindow && windowEndAt == null) {
            Log.w(TAG, "Ignoring end alarm without a valid window end")
            return
        }

        val result = goAsync()
        // Hold a partial wake lock for the whole bounded receiver operation.
        val wakeLock = acquireWakeLock(context)
        scope.launch {
            try {
                val scheduleMatches = scheduler.matchesIncomingOccurrence(
                    automationId = automationId,
                    occurrenceId = occurrenceId,
                    generation = generation,
                    expectedEndAt = windowEndAt
                )
                if (!scheduleMatches) {
                    Log.i(TAG, "Ignoring stale scheduled occurrence for $automationId")
                    return@launch
                }

                val automation = repository.getAutomationById(automationId)
                if (automation == null) {
                    // A deleted automation has no immutable action definition
                    // left to execute. Discard the now-stale schedule identity.
                    scheduler.completeOccurrence(automationId, occurrenceId)
                    return@launch
                }

                if (isEndOfWindow) {
                    // Exit does not depend on reevaluating the current condition.
                    // The coordinator owns a matching active occurrence and
                    // deduplicates a simultaneous trigger-false/recovery event.
                    when (val exit = exitCoordinator.requestExit(
                        automation = automation,
                        reason = ExitReason.TIME_WINDOW_ENDED,
                        occurrenceId = occurrenceId
                    )) {
                        is ExitCoordinatorResult.RecoveryRequired ->
                            Log.w(TAG, "Exit needs recovery for $automationId: ${exit.state.lastError}")
                        ExitCoordinatorResult.AlreadyInProgress ->
                            Log.i(TAG, "Exit already in progress for $automationId")
                        ExitCoordinatorResult.StaleOccurrence ->
                            Log.i(TAG, "Ignoring non-active end occurrence for $automationId")
                        else -> Unit
                    }
                    // The delivered END cannot become a future start. Failed
                    // exits remain durable as EXIT_FAILED for reconciliation.
                    scheduler.completeOccurrence(automationId, occurrenceId)
                } else if (automation.enabled) {
                    val isTimeRange = automation.triggers
                        .firstOrNull { it.type == TriggerType.TIME }
                        ?.config?.get("timeMode") == "RANGE"
                    val now = System.currentTimeMillis()
                    val expiredRange = isTimeRange && windowEndAt != null && windowEndAt <= now
                    if (!shouldExecuteRangeStart(isTimeRange, windowEndAt)) {
                        Log.e(TAG, "Refusing malformed time range without an end for $automationId")
                        scheduler.completeOccurrence(automationId, occurrenceId)
                        scheduler.scheduleNext(automationId)
                        return@launch
                    }
                    // A delayed delivery is still a real occurrence. Never
                    // discard its main actions merely because Android delivered
                    // START after the nominal END; execute START, then close the
                    // same durable occurrence immediately below.
                    val record = executionEngine.runAutomation(
                        automation = automation,
                        completeExitOnFinish = !isTimeRange,
                        lifecycleContext = if (isTimeRange) {
                            AutomationLifecycleContext(
                                occurrenceId = occurrenceId,
                                source = SOURCE_TIME_RANGE,
                                sourceKey = occurrenceId,
                                expectedEndAt = windowEndAt,
                                scheduleGeneration = generation
                            )
                        } else {
                            null
                        }
                    )
                    if (!isTimeRange) {
                        // A one-shot occurrence has already been delivered,
                        // whether its action succeeded or recorded a failure.
                        // Keeping its identity would only accumulate stale data;
                        // retry policy belongs to the execution checkpoint.
                        scheduler.completeOccurrence(automationId, occurrenceId)
                        if (!record.success) {
                            Log.w(TAG, "Time occurrence failed for $automationId: ${record.message}")
                        }
                    } else if (expiredRange) {
                        // START was delivered late but must still have a real
                        // terminal lifecycle. The coordinator will execute the
                        // configured end behavior only after START has returned.
                        when (val exit = exitCoordinator.requestExit(
                            automation = automation,
                            reason = ExitReason.TIME_WINDOW_ENDED,
                            occurrenceId = occurrenceId
                        )) {
                            is ExitCoordinatorResult.RecoveryRequired ->
                                Log.e(TAG, "Delayed range exit requires recovery for $automationId: ${exit.state.lastError}")
                            ExitCoordinatorResult.AlreadyInProgress ->
                                Log.w(TAG, "Delayed range exit already in progress for $automationId")
                            else -> Unit
                        }
                        scheduler.completeOccurrence(automationId, occurrenceId)
                    }
                    // Only START arms a following occurrence. END must never
                    // reschedule over a lifecycle it just attempted to close.
                    scheduler.scheduleNext(automationId)
                }
            } catch (failure: Throwable) {
                // ExecutionEngine/ExitCoordinator persist action and lifecycle
                // failures. Re-arm the same immutable occurrence for a bounded
                // retry when the receiver itself fails before consuming it.
                val rearmed = runCatching {
                    scheduler.rearmOccurrence(
                        automationId = automationId,
                        occurrenceId = occurrenceId,
                        action = alarmAction,
                        retryCount = retryCount
                    )
                }.getOrDefault(false)
                Log.e(
                    TAG,
                    "Automatic lifecycle event failed for $automationId; " +
                        if (rearmed) "same occurrence re-armed" else "no retry available",
                    failure
                )
            } finally {
                releaseWakeLock(wakeLock)
                result.finish()
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NexaFlow:AutomationAlarm"
        ).apply {
            // Timeout guarantees the lock can never be leaked by a stuck run.
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    } catch (_: Throwable) {
        null
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Throwable) {
            // Ignore release races during system teardown.
        }
    }

    private fun restoreAfterBoot(context: Context) {
        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(context)
        scope.launch {
            try {
                // First reconcile elapsed/failed exits from the durable ledger;
                // then rebuild future alarms, which Android cancels at reboot.
                exitCoordinator.reconcile(ExitReason.BOOT_RECOVERY)
                scheduler.initialize()
                scheduler.rescheduleAll(repository.getAutomations().first())
                MonitoringService.scheduleStart(context.applicationContext)
            } catch (failure: Throwable) {
                Log.e(TAG, "Failed to restore automation schedules after boot", failure)
            } finally {
                releaseWakeLock(wakeLock)
                pendingResult.finish()
            }
        }
    }

    /** Recomputes every time trigger after clock, zone, or exact-alarm changes. */
    private fun rescheduleAll(context: Context) {
        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(context)
        scope.launch {
            try {
                exitCoordinator.reconcile(ExitReason.SCHEDULE_RECONCILIATION)
                scheduler.rescheduleAll(repository.getAutomations().first())
            } catch (failure: Throwable) {
                Log.e(TAG, "Failed to reconcile automatic tasks", failure)
            } finally {
                releaseWakeLock(wakeLock)
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * A valid range occurrence remains executable even when delivery is
         * late. The caller closes it through ExitCoordinator after START.
         */
        internal fun shouldExecuteRangeStart(
            isTimeRange: Boolean,
            windowEndAt: Long?
        ): Boolean = !isTimeRange || windowEndAt != null

        private const val TAG = "AutomationAlarmReceiver"
        private const val SOURCE_TIME_RANGE = "time-range"
        internal const val ALARM_PERMISSION_CHANGED_ACTION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        /** Android 17: sent when a fixed UTC offset changes without a zone switch. */
        internal const val TIMEZONE_OFFSET_CHANGED_ACTION =
            "android.intent.action.TIMEZONE_OFFSET_CHANGED"

        /** Every broadcast that invalidates pending user-selected wall-clock alarms. */
        internal val rescheduleActions: Set<String> = setOf(
            ALARM_PERMISSION_CHANGED_ACTION,
            RootPermissionGranter.ACTION_EXACT_ALARM_ACCESS_RECHECK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            TIMEZONE_OFFSET_CHANGED_ACTION
        )

        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
