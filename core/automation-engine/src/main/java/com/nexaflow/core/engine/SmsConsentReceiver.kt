package com.nexaflow.core.engine

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.google.android.gms.auth.api.phone.SmsRetriever
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
 * Android 17 (API 37) safe SMS trigger path.
 *
 * For apps targeting SDK 37 the system withholds SMS_RECEIVED_ACTION and
 * filters provider rows for OTP/verification messages for 3 hours. The SMS
 * User Consent API bypasses that: [SmsConsentManager.startListening] arms a
 * request, the system shows the user a dialog, and on approval this receiver
 * gets the message via [SmsRetriever.SMS_CONSENT_REQUEST] — no READ_SMS
 * permission required, delivered instantly.
 *
 * Matching + execution logic mirrors the legacy [SmsReceiver] via the shared
 * [SmsTriggerMatcher].
 */
@AndroidEntryPoint
class SmsConsentReceiver : BroadcastReceiver() {

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
    lateinit var consentManager: SmsConsentManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsConsentManager.SMS_CONSENT_REQUEST) return

        // The 18.x consent API delivers the approved message intent under
        // EXTRA_CONSENT_INTENT (replacing the old Intent.EXTRA_INTENT).
        val consentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(SmsRetriever.EXTRA_CONSENT_INTENT)
        }
        if (consentIntent == null) {
            // User denied consent: re-arm so the *next* message still
            // triggers (a single denial must not stop listening).
            consentManager.startListening()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(consentIntent)
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        val sender = messages.firstOrNull()?.originatingAddress
        if (messages == null || sender.isNullOrBlank()) {
            consentManager.startListening()
            return
        }

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
                // Arm the next consent request so future SMS still work.
                consentManager.startListening()
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
