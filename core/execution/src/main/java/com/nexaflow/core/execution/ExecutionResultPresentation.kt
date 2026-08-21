package com.nexaflow.core.execution

import android.content.Context
import androidx.annotation.StringRes
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.ExecutionResultClassification
import com.nexaflow.domain.models.ExecutionResultClassifier

/**
 * Resolves persisted execution outcomes to locale-aware, user-facing messages.
 *
 * Execution backends intentionally retain their diagnostic text in [ExecutionRecord]
 * for developer logs and support diagnostics. UI surfaces must use this resolver
 * instead, so an English backend diagnostic can never leak into a translated task
 * result, snackbar, toast, or history timeline.
 */
object ExecutionResultPresentation {

    fun summary(context: Context, record: ExecutionRecord): String =
        context.getString(summaryRes(record))

    fun action(context: Context, result: ActionExecutionResult): String =
        context.getString(actionRes(result))

    @StringRes
    fun summaryRes(record: ExecutionRecord): Int = when (
        ExecutionResultClassifier.classify(record)
    ) {
        ExecutionResultClassification.GOOGLE_PLAY_UPDATES_NOT_EXPOSED ->
            R.string.execution_google_play_updates_unavailable
        ExecutionResultClassification.MANAGED_GOOGLE_PLAY_POLICY_REQUIRED ->
            R.string.execution_google_play_managed_policy_required
        null -> when {
            record.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX) -> when {
                record.actionResults.isEmpty() ->
                    R.string.execution_conditions_not_satisfied_no_end_behavior
                record.success ->
                    R.string.execution_conditions_not_satisfied_end_behavior_completed
                else ->
                    R.string.execution_conditions_not_satisfied_end_behavior_failed
            }
            record.success && record.message.startsWith(SKIPPED_PREFIX) ->
                R.string.execution_task_skipped
            record.success ->
                R.string.execution_task_completed
            else ->
                R.string.execution_task_failed
        }
    }

    @StringRes
    fun actionRes(result: ActionExecutionResult): Int = when {
        result.actionType == STATE_RESTORE -> if (result.success) {
            R.string.execution_original_state_restored
        } else {
            R.string.execution_original_state_restore_failed
        }
        result.actionType.endsWith(END_ACTION_SUFFIX) -> if (result.success) {
            R.string.execution_end_action_completed
        } else {
            R.string.execution_end_action_failed
        }
        result.success -> R.string.execution_action_completed
        else -> R.string.execution_action_failed
    }

    private const val SKIPPED_PREFIX = "Skipped:"
    private const val STATE_RESTORE = "STATE_RESTORE"
    private const val END_ACTION_SUFFIX = "_END"
}
