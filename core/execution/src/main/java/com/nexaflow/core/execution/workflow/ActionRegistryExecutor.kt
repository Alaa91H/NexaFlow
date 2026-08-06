package com.nexaflow.core.execution.workflow

import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action

/**
 * Production [ActionExecutor]: resolves each action through the [ActionRegistry]
 * so the workflow engine runs the exact same handlers as the legacy
 * `ExecutionEngine`. Phase 4 will route legacy automations here via the
 * compatibility mapper.
 */
class ActionRegistryExecutor(
    private val registry: ActionRegistry,
    private val ctx: ActionExecutionContext
) : ActionExecutor {

    override suspend fun execute(action: Action): SystemControlResult {
        val handler = registry.handlerFor(action.type)
            ?: return SystemControlResult.fail("No handler registered for ${action.type}")
        return handler.execute(action, ctx)
    }
}
