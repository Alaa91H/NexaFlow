package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable

/** Outcome of one action inside an execution run, kept for the run-details timeline. */
@Immutable
data class ActionExecutionResult(
    /** ActionType.name (e.g. "SYSTEM_BRIGHTNESS"). */
    val actionType: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long
)

/**
 * A single automation execution. [channel] names the execution provider that
 * actually ran the actions (e.g. "ROOT", "SHIZUKU", "SYSTEM_APP") so the
 * history log shows *how* the task was executed; null when no provider was
 * selected (legacy records / default engine path). [actionResults] carries the
 * per-action timeline (each action's outcome + duration) shown on the run
 * details screen; empty for pre-v7 records.
 */
@Immutable
data class ExecutionRecord(
    val id: String,
    val automationId: String,
    val automationName: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long,
    val channel: String? = null,
    val actionResults: List<ActionExecutionResult> = emptyList()
)
