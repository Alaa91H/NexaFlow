package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Brightness, rotation, timeout, stay-awake, auto-brightness, dark mode, animations. */
class DisplayActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_ANIMATIONS
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_BRIGHTNESS ->
                ctx.controller.setBrightness(action.config["value"]?.toIntOrNull() ?: 128)
            ActionType.SYSTEM_SCREEN_ROTATION ->
                ctx.controller.setScreenRotation(action.config["autoRotate"]?.toBoolean() ?: true)
            ActionType.SYSTEM_SCREEN_TIMEOUT ->
                ctx.controller.setScreenTimeout(action.config["seconds"]?.toIntOrNull() ?: 60)
            ActionType.SYSTEM_STAY_AWAKE ->
                ctx.controller.setStayAwake(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_AUTO_BRIGHTNESS ->
                ctx.controller.setAutoBrightness(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_DARK_MODE ->
                ctx.controller.setDarkMode(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_ANIMATIONS ->
                ctx.controller.setAnimations(action.config["enabled"]?.toBoolean() ?: true)
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
