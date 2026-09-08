package com.nexaflow.core.execution.handler

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Runtime call-control actions. The synchronous screening pass (see
 * CallPolicyEvaluator + NexaCallScreeningService) applies CALL_BLOCK and
 * CALL_SILENCE pre-ring for INCOMING_CALL tasks; this handler covers the
 * remaining cases — a task triggered by something else (TIME, SMS, ...) that
 * wants to end or silence an ongoing ring, and the engine-dispatched path of
 * INCOMING_CALL tasks (where the verdict has already been applied and the
 * handler records the outcome honestly).
 */
class CallActionsHandler : ActionHandler {

    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.CALL_BLOCK,
        ActionType.CALL_SILENCE
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val telecom = ctx.appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return when (action.type) {
            ActionType.CALL_BLOCK -> {
                // Rejects the current incoming call. Without the screening
                // role this throws SecurityException on newer APIs — report
                // the real outcome instead of pretending success.
                val result = runCatching {
                    telecom?.endCall() == true
                }.getOrDefault(false)
                if (result) {
                    SystemControlResult.ok("Call rejected")
                } else {
                    SystemControlResult.fail(
                        "Call rejection unavailable (screening role or ANSWER_PHONE_CALLS required)"
                    )
                }
            }
            ActionType.CALL_SILENCE -> {
                // Silence the ringer for the current call without rejecting
                // it. Best-effort on the audio stream, exactly like the
                // screening setSilenceCall path but available at runtime.
                val audio = ctx.appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val result = runCatching {
                    audio?.adjustStreamVolume(
                        AudioManager.STREAM_RING,
                        AudioManager.ADJUST_MUTE,
                        0
                    )
                    true
                }.getOrDefault(false)
                if (result) {
                    SystemControlResult.ok("Ring silenced")
                } else {
                    SystemControlResult.fail("Could not silence ring (audio service unavailable)")
                }
            }
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
