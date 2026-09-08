package com.nexaflow.core.engine

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import android.util.Log
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
 * Screens incoming calls pre-ring on behalf of enabled INCOMING_CALL tasks.
 *
 * Flow: the system delivers the call → [CallPolicyEvaluator] decides across
 * every matching task (emergency numbers always pass; BLOCK beats SILENCE) →
 * the verdict is applied synchronously → every matching task still runs
 * through the normal engine (so logs, notifications, SMS replies, etc. fire),
 * while non-matching tasks stay untouched.
 *
 * The role is optional: when the user has not granted "caller ID and spam
 * screening", the service never runs and INCOMING_CALL tasks degrade to the
 * post-ring CALL_STATE path via [CallStateMonitor]. No call detail is logged
 * beyond the existing engine execution history (numbers are truncated in
 * logs).
 */
@AndroidEntryPoint
class NexaCallScreeningService : CallScreeningService() {

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var repository: AutomationRepository

    @Inject
    lateinit var executionEngine: ExecutionEngine

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val isEmergency = number.isNotEmpty() && PhoneNumberUtils.isEmergencyNumber(number)

        scope.launch {
            val automations = try {
                repository.getAutomations().first()
            } catch (_: Throwable) {
                respondSafely(callDetails, CallPolicyEvaluator.Verdict.NONE)
                return@launch
            }
            val screeningTasks = automations.filter {
                it.enabled && it.triggers.any { t -> t.type == TriggerType.INCOMING_CALL }
            }
            if (screeningTasks.isEmpty() && !isEmergency) {
                respondSafely(callDetails, CallPolicyEvaluator.Verdict.NONE)
                return@launch
            }
            val category = when {
                number.isEmpty() -> CallPolicyEvaluator.CATEGORY_PRIVATE
                isKnownContact(number) -> CallPolicyEvaluator.CATEGORY_CONTACT
                else -> CallPolicyEvaluator.CATEGORY_UNKNOWN
            }
            val verdict = CallPolicyEvaluator.evaluate(
                automations = automations,
                number = number,
                category = category,
                isEmergency = isEmergency
            )
            respondSafely(callDetails, verdict)

            // Dispatch every task that matched this call (observers included)
            // through the normal engine path so the full workflow executes.
            screeningTasks
                .filter { task ->
                    CallPolicyEvaluator.verdictOf(task, number, category, isEmergency) != null
                }
                .forEach { task ->
                    runCatching {
                        executionEngine.runAutomation(
                            automation = task,
                            completeExitOnFinish = true
                        )
                    }
                }
        }
    }

    private fun respondSafely(details: Call.Details, verdict: CallPolicyEvaluator.Verdict) {
        runCatching {
            val block = verdict == CallPolicyEvaluator.Verdict.BLOCK
            val response = CallResponse.Builder()
                .setDisallowCall(block)
                .setRejectCall(block)
                .setSkipCallLog(block)
                .setSkipNotification(block)
                .setSilenceCall(verdict == CallPolicyEvaluator.Verdict.SILENCE)
                .build()
            respondToCall(details, response)
        }.onFailure {
            Log.w(TAG, "screening response failed", it)
        }
    }

    /**
     * True when the number matches a saved contact. Without READ_CONTACTS the
     * app cannot know, so callers classify as UNKNOWN — the same conservative
     * behavior BlackList uses for its unknown-call filter.
     */
    private fun isKnownContact(number: String): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone._ID),
                ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                arrayOf("%$number%"),
                null
            )?.use { it.count > 0 } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val TAG = "NexaCallScreening"
    }
}

/** Manifest registration helper for the screening service. */
object CallScreeningComponent {
    fun componentName(context: Context): ComponentName =
        ComponentName(context, NexaCallScreeningService::class.java)
}
