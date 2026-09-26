package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Outcome of one action inside an execution run, kept for the run-details timeline. */
@Immutable
@Serializable
data class ActionExecutionResult(
    /** ActionType.name (e.g. "SYSTEM_BRIGHTNESS"). */
    val actionType: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long,
    /** Concrete backend/strategy used for this action, when known. */
    val channel: String? = null,
    /** Stable capability failure classification; null for legacy handlers. */
    val errorCode: String? = null,
    /** Whether execution attempted an independent post-condition verification. */
    val verificationAttempted: Boolean = false,
    /** null = not applicable/unavailable; true/false = explicit verification verdict. */
    val verified: Boolean? = null,
    /**
     * True when the side effect may already have landed but its final state
     * could not be confirmed. Recovery must never auto-replay this action.
     */
    val outcomeUncertain: Boolean = false
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
