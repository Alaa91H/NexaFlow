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
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == ALARM_PERMISSION_CHANGED_ACTION
        ) {
            restoreAfterBoot(context)
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
                        executionEngine.runAutomation(automation)
                    }
                }
                scheduler.scheduleNext(automationId)
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
        try {
            scheduler.initialize()
            MonitoringService.start(context.applicationContext)
        } catch (t: Throwable) {
            Log.e("AutomationAlarmReceiver", "Failed to restore after boot", t)
        }
    }

    companion object {
        const val ALARM_PERMISSION_CHANGED_ACTION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
