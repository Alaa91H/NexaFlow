package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
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
                result.finish()
            }
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
}
