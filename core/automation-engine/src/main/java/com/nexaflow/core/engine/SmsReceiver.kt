package com.nexaflow.core.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SmsManager
import com.nexaflow.core.datastore.SmsDeliveryStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.core.execution.variables.BuiltinVariables
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.repositories.SmsActivityRepository
import com.nexaflow.domain.models.SmsActivityEvent
import com.nexaflow.domain.variables.VariableResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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

    @Inject
    lateinit var smsDeliveryStore: SmsDeliveryStore

    @Inject
    lateinit var smsActivityRepository: SmsActivityRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val receivedAt = System.currentTimeMillis()
        val messageTimestamp = messages
            .mapNotNull { message -> message.timestampMillis.takeIf { it > 0L } }
            .minOrNull()
            ?: (receivedAt / FALLBACK_TIMESTAMP_BUCKET_MS) * FALLBACK_TIMESTAMP_BUCKET_MS
        val fingerprint = SmsTriggerMatcher.deliveryFingerprint(sender, body, messageTimestamp)

        val result = goAsync()
        scope.launch {
            try {
                val automations = repository.getAutomations().first()
                SmsTriggerMatcher.matchingAutomations(automations, sender, body)
                    .forEach { automation ->
                        if (
                            smsDeliveryStore.claim(
                                automationId = automation.id,
                                fingerprint = fingerprint,
                                cooldownMillis = automation.cooldownMillis,
                                occurredAt = receivedAt
                            )
                        ) {
                            val matchedTriggerIndices =
                                SmsTriggerMatcher.matchingTriggerIndices(automation, sender, body)
                            val eventId = java.util.UUID.randomUUID().toString()
                            val outcome = try { executionEngine.runAutomation(
                                automation = automation,
                                completeExitOnFinish = true,
                                triggerOccurrence = TriggerOccurrence(
                                    matchedTriggerIndices = matchedTriggerIndices,
                                    occurredAtEpochMs = receivedAt,
                                    sourceId = "sms",
                                    eventId = fingerprint,
                                ),
                            ) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                            val succeeded = outcome?.success == true
                            recordSmsActivitySafely(SmsActivityEvent(eventId, "INCOMING_TRIGGER", automation.id, automation.name, if (succeeded) "SUCCESS" else "FAILED", if (succeeded) null else "EXECUTION_FAILED", System.currentTimeMillis()))
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

    private suspend fun recordSmsActivitySafely(event: SmsActivityEvent) {
        try {
            smsActivityRepository.record(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Activity persistence is best-effort and cannot block automation execution.
        }
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

    private companion object {
        const val FALLBACK_TIMESTAMP_BUCKET_MS = 5_000L
    }
}
