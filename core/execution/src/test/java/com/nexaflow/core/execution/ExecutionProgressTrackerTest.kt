package com.nexaflow.core.execution

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionProgressTrackerTest {

    @Test
    fun progressTransitionsRetainRouteAndVerification() = runBlocking {
        val tracker = ExecutionProgressTracker()
        val automation = task()

        tracker.start(automation, startedAt = 100L)
        tracker.markRunning(automation.id, 0)
        tracker.markResult(
            automation.id,
            0,
            SystemControlResult.ok(
                message = "done",
                executionChannel = "ROOT_SHELL",
                verificationAttempted = true,
                verified = true
            ),
            fallbackChannel = "ROOT"
        )

        val running = tracker.observe(automation.id).first()!!
        val first = running.actions.first()
        assertEquals(LiveActionStatus.SUCCEEDED, first.status)
        assertEquals("ROOT_SHELL", first.channel)
        assertTrue(first.verificationAttempted)
        assertEquals(true, first.verified)
        assertFalse(running.finished)

        tracker.finish(automation.id)
        assertTrue(tracker.observe(automation.id).first()!!.finished)
    }

    @Test
    fun fallbackChannelAndSkippedStateAreExplicit() = runBlocking {
        val tracker = ExecutionProgressTracker()
        val automation = task()

        tracker.start(automation, startedAt = 100L)
        tracker.markSkipped(automation.id, 0)
        tracker.markRunning(automation.id, 1)
        tracker.markResult(
            automation.id,
            1,
            SystemControlResult.fail("failed", errorCode = "TIMEOUT"),
            fallbackChannel = "SHIZUKU"
        )

        val snapshot = tracker.observe(automation.id).first()!!
        assertEquals(LiveActionStatus.SKIPPED, snapshot.actions[0].status)
        assertEquals(LiveActionStatus.FAILED, snapshot.actions[1].status)
        assertEquals("SHIZUKU", snapshot.actions[1].channel)
        assertEquals("TIMEOUT", snapshot.actions[1].errorCode)
    }

    private fun task() = Automation(
        id = "progress-task",
        name = "Progress",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(
            Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true")),
            Action(ActionType.SYSTEM_NFC, mapOf("enabled" to "true"))
        ),
        createdAt = 0L,
        updatedAt = 0L
    )
}
