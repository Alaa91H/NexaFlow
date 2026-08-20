package com.nexaflow.app.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.execution.WorkflowRunContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device contract for the per-run payload context.
 *
 * This test has no Android privileged dependency, but it is intentionally executed by
 * AndroidJUnit4 so the production Kotlin runtime path is exercised in the installed app process.
 * It must not be used to claim Android integration verification until connectedAndroidTest runs.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowRunContextAndroidTest {

    @Test
    fun jsonPathMergeSnapshotAndBudgetAreAtomicOnDevice() {
        val context = WorkflowRunContext(
            runId = "android-context-run",
            automationId = "android-context-automation",
            triggeredAt = 1_700_000_000_000L
        )

        context.put("$.weather.temp", 21.4)
        context.put("$.weather.units", "celsius")
        context.put("$.items", listOf(mapOf("name" to "first"), mapOf("name" to "second")))

        assertEquals(21.4, context.get("$.weather.temp"))
        assertEquals("celsius", context.get("$.weather.units"))
        assertEquals("second", context.get("$.items[1].name"))
        assertNull(context.get("$.weather.missing"))
        assertEquals(
            listOf("$.items[0].name", "$.items[1].name", "$.weather.temp", "$.weather.units"),
            context.paths()
        )

        @Suppress("UNCHECKED_CAST")
        val firstSnapshot = context.snapshot() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val snapshotWeather = firstSnapshot.getValue("weather") as Map<String, Any?>
        assertEquals(21.4, snapshotWeather.getValue("temp"))

        context.put("$.weather.temp", 22.0)
        @Suppress("UNCHECKED_CAST")
        val secondSnapshot = context.snapshot() as Map<String, Any?>
        assertNotSame(firstSnapshot, secondSnapshot)
        assertEquals(21.4, snapshotWeather.getValue("temp"))
        assertEquals(22.0, context.get("$.weather.temp"))

        val pathsBeforeRejectedWrite = context.paths()
        try {
            context.put("$.oversized", "x".repeat(WorkflowRunContext.MAX_BYTES.toInt()))
            fail("An oversized payload must be rejected before it mutates the context")
        } catch (_: IllegalStateException) {
            // Expected: the production budget guard rejects the candidate tree atomically.
        }

        assertEquals(pathsBeforeRejectedWrite, context.paths())
        assertNull(context.get("$.oversized"))
        assertTrue(context.size < WorkflowRunContext.MAX_BYTES)
    }
}
