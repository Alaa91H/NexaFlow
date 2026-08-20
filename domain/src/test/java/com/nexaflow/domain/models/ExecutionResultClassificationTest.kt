package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExecutionResultClassificationTest {

    @Test
    fun `classifies play discovery unavailability from stable marker`() {
        assertEquals(
            ExecutionResultClassification.GOOGLE_PLAY_UPDATES_NOT_EXPOSED,
            ExecutionResultClassifier.classify(
                recordWithUpdateMessage("SKIPPED:${ExecutionResultClassifier.DISCOVERY_NOT_EXPOSED_MARKER}")
            )
        )
    }

    @Test
    fun `classifies managed play policy requirement from stable marker`() {
        assertEquals(
            ExecutionResultClassification.MANAGED_GOOGLE_PLAY_POLICY_REQUIRED,
            ExecutionResultClassifier.classify(
                recordWithUpdateMessage("SKIPPED:${ExecutionResultClassifier.MANAGED_POLICY_MARKER}")
            )
        )
    }

    @Test
    fun `does not classify an unrelated successful action`() {
        val record = ExecutionRecord(
            id = "run",
            automationId = "automation",
            automationName = "Automation",
            success = true,
            message = "Completed",
            executedAt = 1L,
            actionResults = listOf(
                ActionExecutionResult(
                    actionType = ActionType.SYSTEM_BLUETOOTH.name,
                    success = true,
                    message = "Enabled",
                    durationMs = 1L
                )
            )
        )

        assertNull(ExecutionResultClassifier.classify(record))
    }

    private fun recordWithUpdateMessage(message: String): ExecutionRecord = ExecutionRecord(
        id = "run",
        automationId = "automation",
        automationName = "Update applications",
        success = true,
        message = message,
        executedAt = 1L,
        actionResults = listOf(
            ActionExecutionResult(
                actionType = ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS.name,
                success = true,
                message = message,
                durationMs = 1L
            )
        )
    )
}
