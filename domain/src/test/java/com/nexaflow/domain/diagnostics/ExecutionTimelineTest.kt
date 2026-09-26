package com.nexaflow.domain.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionTimelineTest {

    @Test
    fun `timeline entry validates identity and bounded summary`() {
        val entry = ExecutionTimelineEntry(
            timestampMs = 1L,
            kind = TimelineEventKind.ACTION_COMPLETED,
            runId = "run-1",
            workflowId = "workflow-1",
            nodeId = "node-1",
            success = true,
            summary = "completed",
            metadata = mapOf("backend" to "android")
        )
        assertTrue(entry.success == true)
        assertEquals("node-1", entry.nodeId)

        assertThrows(IllegalArgumentException::class.java) {
            entry.copy(runId = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            entry.copy(workflowId = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            entry.copy(summary = "x".repeat(513))
        }
    }

    @Test
    fun `timeline derives duration and terminal success from the last entry`() {
        val completed = ExecutionTimeline(
            runId = "run",
            workflowId = "workflow",
            startedAtMs = 100L,
            completedAtMs = 175L,
            entries = listOf(
                entry(TimelineEventKind.ACTION_COMPLETED),
                entry(TimelineEventKind.WORKFLOW_COMPLETED)
            )
        )
        assertEquals(75L, completed.durationMs)
        assertEquals(true, completed.success)

        val failed = completed.copy(
            entries = listOf(entry(TimelineEventKind.WORKFLOW_FAILED))
        )
        assertEquals(false, failed.success)

        val active = completed.copy(completedAtMs = null, entries = emptyList())
        assertNull(active.durationMs)
        assertNull(active.success)
    }

    @Test
    fun `builder records ordered metadata and immutable snapshots`() {
        var now = 10L
        val builder = ExecutionTimelineBuilder(
            runId = "run-builder",
            workflowId = "workflow-builder",
            nowMs = { now++ }
        )

        builder.append(
            kind = TimelineEventKind.ACTION_STARTED,
            summary = "start",
            nodeId = "node-1",
            actionType = "SYSTEM_WIFI",
            backendId = "ANDROID_API"
        )
        builder.append(
            kind = TimelineEventKind.ACTION_COMPLETED,
            summary = "done",
            nodeId = "node-1",
            durationMs = 5L,
            retryCount = 1,
            success = true,
            metadata = mapOf("verified" to "true")
        )

        val firstSnapshot = builder.build(completedAtMs = 30L)
        assertEquals(10L, firstSnapshot.startedAtMs)
        assertEquals(2, firstSnapshot.entries.size)
        assertEquals(TimelineEventKind.ACTION_STARTED, firstSnapshot.entries[0].kind)
        assertEquals("ANDROID_API", firstSnapshot.entries[0].backendId)
        assertEquals(1, firstSnapshot.entries[1].retryCount)
        assertEquals("true", firstSnapshot.entries[1].metadata["verified"])

        builder.append(TimelineEventKind.WORKFLOW_COMPLETED, "finished")
        assertEquals(2, firstSnapshot.entries.size)
        assertEquals(3, builder.build(31L).entries.size)
    }

    @Test
    fun `in memory store replaces run ids sorts recent runs and prunes old ones`() = runBlocking {
        val store = InMemoryExecutionTimelineStore()
        val old = timeline("old", 10L)
        val middle = timeline("middle", 20L)
        val newest = timeline("newest", 30L)

        store.save(old)
        store.save(middle)
        store.save(newest)

        assertEquals(listOf("newest", "middle"), store.recentTimelines(2).map { it.runId })
        assertEquals(middle, store.findByRunId("middle"))
        assertNull(store.findByRunId("missing"))

        val replacement = timeline("middle", 40L)
        store.save(replacement)
        assertEquals(replacement, store.findByRunId("middle"))
        assertEquals(3, store.recentTimelines().size)

        store.pruneOlderThan(30L)
        val remaining = store.recentTimelines()
        assertEquals(setOf("newest", "middle"), remaining.map { it.runId }.toSet())
        assertFalse(remaining.any { it.runId == "old" })
    }

    private fun entry(kind: TimelineEventKind) = ExecutionTimelineEntry(
        timestampMs = 1L,
        kind = kind,
        runId = "run",
        workflowId = "workflow",
        summary = kind.name
    )

    private fun timeline(runId: String, startedAt: Long) = ExecutionTimeline(
        runId = runId,
        workflowId = "workflow",
        startedAtMs = startedAt,
        completedAtMs = startedAt + 1,
        entries = listOf(
            ExecutionTimelineEntry(
                timestampMs = startedAt,
                kind = TimelineEventKind.WORKFLOW_COMPLETED,
                runId = runId,
                workflowId = "workflow",
                summary = "done"
            )
        )
    )
}
