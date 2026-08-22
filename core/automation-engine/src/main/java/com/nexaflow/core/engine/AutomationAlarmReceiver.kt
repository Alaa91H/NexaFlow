package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        // The user changed the clock, crossed into another time zone, or
        // granted/revoked exact-alarm access. In each case every RTC alarm must
        // be recomputed; Android cancels exact alarms when the access changes.
        if (intent.action in rescheduleActions) {
            rescheduleAll()
            return
        }
        if (intent.action == MonitoringService.ACTION_START_MONITORING) {
            // Fired by the alarm scheduled in restoreAfterBoot: Android 15+
            // forbids launching time-limited FGS types from BOOT_COMPLETED, so
            // the service is started from here instead of the boot receiver.
            runCatching { MonitoringService.start(context.applicationContext) }
                .onFailure { Log.e(TAG, "Failed to start background trigger monitoring", it) }
            return
        }
        if (intent.action != AutomationScheduler.ACTION_RUN_AUTOMATION &&
            intent.action != AutomationScheduler.ACTION_END_AUTOMATION
        ) return
        val automationId = intent.getStringExtra(AutomationScheduler.EXTRA_AUTOMATION_ID) ?: return
        val isEndOfWindow = intent.action == AutomationScheduler.ACTION_END_AUTOMATION
        val result = goAsync()
        // Hold a partial wake lock for the whole run so the alarm fires the task
        // even if the screen is off and the device would otherwise doze.
        val wakeLock = acquireWakeLock(context)
        scope.launch {
            try {
                val automation = repository.getAutomationById(automationId)
                if (automation != null && automation.enabled) {
                    if (isEndOfWindow) {
                        // The time-range window closed: run the exit/revert behavior.
                        executionEngine.runExit(automation)
                    } else {
                        val isTimeRange = automation.triggers
                            .firstOrNull { it.type == com.nexaflow.domain.models.TriggerType.TIME }
                            ?.config?.get("timeMode") == "RANGE"
                        // A single clock event has no inverse state; close its
                        // lifecycle after its actions so "when task ends" is
                        // guaranteed to run. A range remains active until the
                        // separately scheduled end alarm arrives.
                        executionEngine.runAutomation(
                            automation = automation,
                            completeExitOnFinish = !isTimeRange
                        )
                    }
                }
                scheduler.scheduleNext(automationId)
            } catch (failure: Throwable) {
                // Never lose an automatic execution failure behind a receiver
                // crash. ExecutionEngine records action-level results; this
                // guard covers repository/scheduling failures before or after
                // the engine can persist its own execution record.
                Log.e(TAG, "Automatic execution failed for $automationId", failure)
            } finally {
                releaseWakeLock(wakeLock)
                result.finish()
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
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
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun restoreAfterBoot(context: Context) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                scheduler.initialize()
                // Do not rely only on the long-lived repository collector:
                // boot/package replacement must synchronously request a fresh
                // schedule pass before Android can reclaim the receiver process.
                scheduler.rescheduleAll(repository.getAutomations().first())
                // Android 15+ forbids launching time-limited FGS types directly
                // from BOOT_COMPLETED, so the service starts from a short alarm.
                MonitoringService.scheduleStart(context.applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore automation schedules after boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Recomputes every time trigger after clock, time-zone, or alarm-access changes. */
    private fun rescheduleAll() {
        val pendingResult = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll(repository.getAutomations().first())
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to reschedule automatic tasks", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutomationAlarmReceiver"
        const val ALARM_PERMISSION_CHANGED_ACTION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        /** Android 17: sent when a fixed UTC offset changes without a zone switch. */
        const val TIMEZONE_OFFSET_CHANGED_ACTION =
            "android.intent.action.TIMEZONE_OFFSET_CHANGED"

        /** Every broadcast that invalidates pending user-selected wall-clock alarms. */
        internal val rescheduleActions: Set<String> = setOf(
            ALARM_PERMISSION_CHANGED_ACTION,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            TIMEZONE_OFFSET_CHANGED_ACTION
        )

        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
