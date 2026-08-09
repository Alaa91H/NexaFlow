package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ACTION_REVERT_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.ACTION_RUN_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.EXTRA_AUTOMATION_ID
import com.nexaflow.core.execution.EXTRA_REPLY_VARIABLE
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.REMOTE_INPUT_REPLY_KEY
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.VariableRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
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

    @Inject
    lateinit var variableRepository: VariableRepository

    override fun onReceive(context: Context, intent: Intent) {
        val revert = intent.action == ACTION_REVERT_TASK_FROM_NOTIFICATION
        if (!revert && intent.action != ACTION_RUN_TASK_FROM_NOTIFICATION) return
        val automationId = intent.getStringExtra(EXTRA_AUTOMATION_ID) ?: return

        val result = goAsync()
        scope.launch {
            try {
                // A reply action button (P2-1): the typed text arrives through
                // RemoteInput; write it into the configured %variable BEFORE the
                // task runs so its actions can reference the reply.
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(REMOTE_INPUT_REPLY_KEY)
                    ?.toString()
                val replyVariable = intent.getStringExtra(EXTRA_REPLY_VARIABLE)
                if (reply != null && !replyVariable.isNullOrBlank()) {
                    saveReplyVariable(replyVariable, reply)
                }
                val automation = repository.getAutomationById(automationId)
                if (automation != null) {
                    // The revert button restores the pre-run state directly; the
                    // run button executes the task like any other trigger.
                    if (revert) executionEngine.runExit(automation)
                    else executionEngine.runAutomation(automation)
                }
            } finally {
                result.finish()
            }
        }
    }

    /**
     * Upserts the global variable [name] with [value]. A variable that does not
     * exist yet is created (sensible default for a reply target); an existing
     * one keeps its id/sensitive flag so encrypted variables stay encrypted.
     */
    private suspend fun saveReplyVariable(name: String, value: String) {
        val existing = variableRepository.getVariablesOnce().firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
        val variable = if (existing != null) {
            existing.copy(value = value, updatedAt = System.currentTimeMillis())
        } else {
            GlobalVariable(
                id = UUID.randomUUID().toString(),
                name = name,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        }
        variableRepository.saveVariable(variable)
    }
}
