package com.nexaflow.core.execution

import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionResultPresentationTest {

    @Test
    fun manualConditionRejection_withSuccessfulEndBehavior_usesLocalizedSummary() {
        val record = record(
            success = true,
            message = ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX + "end behavior: Ringer mode set to Normal",
            actionResults = listOf(action("SYSTEM_RINGER_MODE_END", success = true))
        )

        assertEquals(
            R.string.execution_conditions_not_satisfied_end_behavior_completed,
            ExecutionResultPresentation.summaryRes(record)
        )
    }

    @Test
    fun manualConditionRejection_withoutEndBehavior_usesLocalizedSummary() {
        val record = record(
            success = true,
            message = ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX + "no end behavior configured"
        )

        assertEquals(
            R.string.execution_conditions_not_satisfied_no_end_behavior,
            ExecutionResultPresentation.summaryRes(record)
        )
    }

    @Test
    fun actionAndEndAction_selectLocalizedOutcomeResources() {
        assertEquals(
            R.string.execution_action_completed,
            ExecutionResultPresentation.actionRes(action("SYSTEM_RINGER_MODE", success = true))
        )
        assertEquals(
            R.string.execution_action_failed,
            ExecutionResultPresentation.actionRes(action("SYSTEM_RINGER_MODE", success = false))
        )
        assertEquals(
            R.string.execution_end_action_completed,
            ExecutionResultPresentation.actionRes(action("SYSTEM_RINGER_MODE_END", success = true))
        )
        assertEquals(
            R.string.execution_end_action_failed,
            ExecutionResultPresentation.actionRes(action("SYSTEM_RINGER_MODE_END", success = false))
        )
    }

    @Test
    fun stateRestore_selectsDedicatedLocalizedOutcomeResources() {
        assertEquals(
            R.string.execution_original_state_restored,
            ExecutionResultPresentation.actionRes(action("STATE_RESTORE", success = true))
        )
        assertEquals(
            R.string.execution_original_state_restore_failed,
            ExecutionResultPresentation.actionRes(action("STATE_RESTORE", success = false))
        )
    }

    private fun record(
        success: Boolean,
        message: String,
        actionResults: List<ActionExecutionResult> = emptyList()
    ) = ExecutionRecord(
        id = "execution-id",
        automationId = "automation-id",
        automationName = "Automation",
        success = success,
        message = message,
        executedAt = 1L,
        actionResults = actionResults
    )

    private fun action(type: String, success: Boolean) = ActionExecutionResult(
        actionType = type,
        success = success,
        message = "Raw backend diagnostic that must not reach the UI",
        durationMs = 1L
    )
}
