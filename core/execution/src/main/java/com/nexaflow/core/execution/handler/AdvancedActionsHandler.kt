package com.nexaflow.core.execution.handler

import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.compat.ExecutionProviderType
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Raw shell actions executed through Shizuku or root. */
class AdvancedActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val command = action.config["command"] ?: "echo nexaflow"
        return route(ctx.channel, action.type, command)
    }

    /**
     * Pure routing (Phase 6): the command goes through the run-time selected
     * [channel] only when that channel matches the explicit runtime the action
     * requests (Shizuku actions via a Shizuku channel, root actions via a Root
     * channel). Auto-selection never silently overrides an explicit user
     * choice — on a device with both root and Shizuku, an ADVANCED_SHIZUKU
     * action still runs via Shizuku. When no matching channel was selected,
     * the legacy explicit runtime runs (which validates availability itself).
     */
    internal fun route(
        channel: ExecutionProvider?,
        type: ActionType,
        command: String
    ): SystemControlResult {
        val expected = when (type) {
            ActionType.ADVANCED_SHIZUKU -> ExecutionProviderType.SHIZUKU
            ActionType.ADVANCED_ROOT -> ExecutionProviderType.ROOT
            else -> null
        }
        if (channel != null && channel.hasShellAccess && channel.type == expected) {
            return channel.execute(command)
        }
        return when (type) {
            ActionType.ADVANCED_SHIZUKU -> PrivilegedRunner.runShizuku(command)
            ActionType.ADVANCED_ROOT -> PrivilegedRunner.runRoot(command)
            else -> SystemControlResult.fail("Unsupported action $type")
        }
    }
}
