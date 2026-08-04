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
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
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
                automations
                    .filter { automation ->
                        automation.enabled && automation.triggers.any { trigger ->
                            trigger.type == TriggerType.SMS && matches(trigger.config, sender, body)
                        }
                    }
                    .forEach { automation ->
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > COOLDOWN_MS) {
                            lastRunAt[automation.id] = now
                            executionEngine.runAutomation(automation)
                            val reply = automation.triggers
                                .first { it.type == TriggerType.SMS }
                                .config["reply"]
                            if (!reply.isNullOrBlank()) {
                                sendReply(context, sender, reply)
                            }
                        }
                    }
            } finally {
                result.finish()
            }
        }
    }

    private fun matches(config: Map<String, String>, sender: String, body: String): Boolean {
        val from = config["from"].orEmpty().trim()
        val contains = config["contains"].orEmpty().trim()
        val fromMatch = from.isEmpty() || sender.contains(from, ignoreCase = true)
        val textMatch = contains.isEmpty() || body.contains(contains, ignoreCase = true)
        return fromMatch && textMatch
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

    companion object {
        private const val COOLDOWN_MS = 5_000L
        private val lastRunAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }
}
