package com.nexaflow.core.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SmsManager
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.variables.BuiltinVariables
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.VariableResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for incoming SMS and fires automations that use an SMS trigger.
 * The trigger config supports:
 *  - "from": sender number or part of it (optional, blank = any sender)
 *  - "contains": text the message must contain (optional, blank = any text)
 *  - "reply": auto-reply text (optional; sent back to the sender)
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        val sender = messages.firstOrNull()?.originatingAddress ?: return

        val result = goAsync()
        scope.launch {
            try {
                val automations = repository.getAutomations().first()
                val now = System.currentTimeMillis()
                SmsTriggerMatcher.matchingAutomations(automations, sender, body)
                    .forEach { automation ->
                        val last = SmsTriggerMatcher.lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            SmsTriggerMatcher.lastRunAt[automation.id] = now
                            executionEngine.runAutomation(
                                automation = automation,
                                completeExitOnFinish = true
                            )
                            val reply = SmsTriggerMatcher.replyOf(automation)
                            if (!reply.isNullOrBlank()) {
                                // Auto-reply text supports the same %variables
                                // as action texts (built-ins + user globals).
                                val resolved = VariableResolver.resolve(
                                    reply,
                                    resolveReplyVariables(context)
                                )
                                sendReply(context, sender, resolved)
                            }
                        }
                    }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun resolveReplyVariables(context: Context): Map<String, String> = try {
        BuiltinVariables.provide(context) +
            variableRepository.getVariablesOnce().associate { it.name to it.value }
    } catch (_: Throwable) {
        emptyMap()
    }

    @SuppressLint("MissingPermission")
    private fun sendReply(context: Context, to: String, text: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(to, null, parts, null, null)
        } catch (_: Throwable) {
            // Ignore failures; the automation itself still ran.
        }
    }

}
