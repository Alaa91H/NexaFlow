package com.nexaflow.core.execution.handler

import android.content.Context
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.WorkflowRunContext
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
    val channel: ExecutionProvider? = null,
    /**
     * Id of the enclosing task (when the engine knows it). Handlers that send
     * notifications can attach the "restore original state" button that routes
     * back to `runExit` for this task. Null when the engine has no automation
     * context (e.g. the workflow runner's shared executor).
     */
    val automationId: String? = null,
    /**
     * Whether the enclosing task restores its state when it ends. When true,
     * notification-sending handlers append the revert action button so the
     * user can restore the pre-run state straight from the notification.
     */
    val revertOnExit: Boolean = false,
    /**
     * The per-run payload context (Phase 2): a JSON Merge Patch delta with a
     * 256KB budget. Handlers read prior node outputs via [WorkflowRunContext.get]
     * and publish their own via [WorkflowRunContext.put]. Null when the engine
     * does not thread a context (e.g. exit-behavior runs).
     */
    val runContext: WorkflowRunContext? = null
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
