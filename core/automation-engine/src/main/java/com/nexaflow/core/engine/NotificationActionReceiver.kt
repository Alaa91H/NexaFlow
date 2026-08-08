package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ACTION_RUN_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.EXTRA_AUTOMATION_ID
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Routing point for the interactive notification action buttons attached to
 * NexaFlow notifications. A button's PendingIntent broadcasts
 * [ACTION_RUN_TASK_FROM_NOTIFICATION] with an automation id; this receiver
 * resolves the task and runs it through the [ExecutionEngine] exactly like any
 * other trigger would — including cooldowns, constraints and history logging.
 *
 * Runs on the injected application scope (not the main thread) and keeps the
 * broadcast alive with `goAsync()` until the run finishes.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var repository: AutomationRepository

    @Inject
    lateinit var executionEngine: ExecutionEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_TASK_FROM_NOTIFICATION) return
        val automationId = intent.getStringExtra(EXTRA_AUTOMATION_ID) ?: return

        val result = goAsync()
        scope.launch {
            try {
                val automation = repository.getAutomationById(automationId)
                if (automation != null) {
                    executionEngine.runAutomation(automation)
                }
            } finally {
                result.finish()
            }
        }
    }
}
