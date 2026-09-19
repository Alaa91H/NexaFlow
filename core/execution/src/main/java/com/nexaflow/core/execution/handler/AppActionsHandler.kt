package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Open/launch/close apps and open their settings page. */
class AppActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_OPEN_APP,
        ActionType.APPLICATION_LAUNCH_APP,
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.APPLICATION_OPEN_APP_SETTINGS
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val capabilityRequest = com.nexaflow.core.execution.capability.CapabilityActionMapper.requestFor(
            action, ctx.automationId, ctx.runContext?.runId
        )
        if (capabilityRequest != null && ctx.capabilityService != null) {
            val res = ctx.capabilityService.execute(capabilityRequest)
            return if (res.status == com.nexaflow.domain.capability.CapabilityStatus.SUCCESS ||
                res.status == com.nexaflow.domain.capability.CapabilityStatus.PENDING_USER_ACTION
            ) {
                SystemControlResult.ok(res.message)
            } else {
                SystemControlResult.fail(res.message)
            }
        }

        return when (action.type) {
            ActionType.SYSTEM_OPEN_APP -> {
                val packages = (action.config["packages"] ?: action.config["package"] ?: "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (packages.isEmpty()) {
                    SystemControlResult.fail("No app selected")
                } else {
                    packages.forEach { ctx.controller.launchApp(it) }
                    SystemControlResult.ok("Opened ${packages.size} app(s)")
                }
            }
            ActionType.APPLICATION_LAUNCH_APP ->
                ctx.controller.launchApp(action.config["package"] ?: "")
            ActionType.APPLICATION_CLOSE_APP ->
                ctx.controller.forceStopPackage(action.config["package"] ?: "")
            ActionType.APPLICATION_OPEN_APP_SETTINGS ->
                ctx.controller.openAppSettings(action.config["package"] ?: "")
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
