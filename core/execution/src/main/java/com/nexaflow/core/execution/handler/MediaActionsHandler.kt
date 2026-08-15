package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Media play/pause, next, previous. */
class MediaActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
        ActionType.SYSTEM_MEDIA_NEXT,
        ActionType.SYSTEM_MEDIA_PREVIOUS,
        ActionType.SYSTEM_MEDIA_STOP
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_MEDIA_PLAY_PAUSE -> ctx.controller.mediaControl("PLAY_PAUSE")
            ActionType.SYSTEM_MEDIA_NEXT -> ctx.controller.mediaControl("NEXT")
            ActionType.SYSTEM_MEDIA_PREVIOUS -> ctx.controller.mediaControl("PREVIOUS")
            ActionType.SYSTEM_MEDIA_STOP -> ctx.controller.mediaControl("STOP")
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
