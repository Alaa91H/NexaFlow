package com.nexaflow.core.execution.handler

import android.media.AudioManager
import com.nexaflow.core.execution.AudioStreams
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Volume streams, ringer mode, ring volume and Do Not Disturb. */
class SoundActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_STREAM_VOLUME,
        ActionType.SYSTEM_RINGER_MODE,
        ActionType.SYSTEM_RING_VOLUME,
        ActionType.SYSTEM_SET_RINGTONE,
        ActionType.SYSTEM_DND
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_VOLUME ->
                ctx.controller.setVolume(AudioManager.STREAM_MUSIC, action.config["value"]?.toIntOrNull() ?: 50)
            ActionType.SYSTEM_STREAM_VOLUME ->
                ctx.controller.setVolume(
                    AudioStreams.streamId(action.config["stream"] ?: "MUSIC"),
                    action.config["value"]?.toIntOrNull() ?: 50
                )
            ActionType.SYSTEM_RINGER_MODE ->
                ctx.controller.setRingerMode(action.config["mode"] ?: "NORMAL")
            ActionType.SYSTEM_RING_VOLUME ->
                ctx.controller.setRingVolume(action.config["value"]?.toIntOrNull() ?: 50)
            ActionType.SYSTEM_SET_RINGTONE ->
                ctx.controller.setRingtone(action.config["uri"] ?: "")
            ActionType.SYSTEM_DND ->
                ctx.controller.setDoNotDisturb(action.config["enabled"]?.toBoolean() ?: true)
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
