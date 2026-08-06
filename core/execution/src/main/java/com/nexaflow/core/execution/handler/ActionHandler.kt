package com.nexaflow.core.execution.handler

import android.content.Context
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Everything an [ActionHandler] needs to run one action. */
data class ActionExecutionContext(
    val appContext: Context,
    val controller: SystemController,
    val notificationSettings: NotificationSettings,
    /**
     * The best execution channel selected for this run (Phase 6). Null when
     * the legacy engine (which hard-codes its runtime) built the context.
     */
    val channel: ExecutionProvider? = null
)

/**
 * Executes a single [Action] type (or a small family of related types).
 *
 * Handlers are the unit of extensibility in the framework: adding a new action
 * means adding a handler and registering it — the engine itself never changes.
 * Each handler declares the [ActionType]s it supports and must be stateless
 * (any per-run state belongs in the execution context).
 */
interface ActionHandler {
    val supportedTypes: Set<ActionType>

    suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult
}
