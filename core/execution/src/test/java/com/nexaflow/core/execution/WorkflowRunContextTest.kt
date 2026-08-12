package com.nexaflow.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Atomic tests for [WorkflowRunContext]: JSON Merge Patch delta semantics
 * (deep writes merge, shallow writes replace), JSONPath read/write, the 256KB
 * budget (rejected writes never mutate state), and path validation.
 * Pure JVM — no Android, no coroutines.
 */
class WorkflowRunContextTest {

    // --- construction -------------------------------------------------------

    @Test
    fun `create generates a run id and carries the identity fields`() {
        val ctx = WorkflowRunContext.create("auto-1", triggeredAt = 1234L)
        assertTrue(ctx.runId.isNotBlank())
        assertEquals("auto-1", ctx.automationId)
        assertEquals(1234L, ctx.triggeredAt)
        assertNotEquals(WorkflowRunContext.create("auto-1", 1L).runId, ctx.runId)
    }

    @Test
    fun `fresh context is empty`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        assertEquals(0, ctx.size)
        assertEquals(emptyList<String>(), ctx.paths())
        // Root read of an empty delta is the empty document, not null.
        assertEquals(emptyMap<String, Any?>(), ctx.get("\$"))
    }

    // --- JSON Merge Patch delta semantics -----------------------------------

    @Test
    fun `deep writes merge under the same branch instead of clobbering`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        ctx.put("\$.weather.humidity", 55)
        assertEquals(21.4, ctx.get("\$.weather.temp"))
        assertEquals(55, ctx.get("\$.weather.humidity"))
        assertEquals(mapOf("temp" to 21.4, "humidity" to 55), ctx.get("\$.weather"))
    }

    @Test
    fun `whole-branch write replaces the previous branch`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        ctx.put("\$.weather", mapOf("condition" to "sunny"))
        assertEquals(mapOf("condition" to "sunny"), ctx.get("\$.weather"))
        assertNull(ctx.get("\$.weather.temp"))
    }

    @Test
    fun `root read returns the whole delta document`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        ctx.put("\$.location.lat", 33.5)
        assertEquals(
            mapOf("weather" to mapOf("temp" to 21.4), "location" to mapOf("lat" to 33.5)),
            ctx.get("\$")
        )
    }

    @Test
    fun `root write replaces the whole document`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        ctx.put("\$", mapOf("reset" to true))
        assertEquals(mapOf("reset" to true), ctx.get("\$"))
        assertNull(ctx.get("\$.weather"))
    }

    @Test
    fun `scalar at an intermediate key is replaced by a map (RFC 7386)`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.a", 5)
        ctx.put("\$.a.b", 1)
        assertEquals(mapOf("b" to 1), ctx.get("\$.a"))
        assertEquals(1, ctx.get("\$.a.b"))
    }

    @Test
    fun `nested lists support index read and write`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.results", listOf("a", "b", "c"))
        ctx.put("\$.results[1]", "x")
        assertEquals("x", ctx.get("\$.results[1]"))
        assertEquals(listOf("a", "x", "c"), ctx.get("\$.results"))
        assertEquals("a", ctx.get("\$.results[0]"))
    }

    @Test
    fun `navigation through a list index reaches nested maps`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.users", listOf(mapOf("name" to "Alaa"), mapOf("name" to "Sara")))
        ctx.put("\$.users[1].name", "Sara J.")
        assertEquals("Sara J.", ctx.get("\$.users[1].name"))
        assertEquals("Alaa", ctx.get("\$.users[0].name"))
    }

    @Test
    fun `root list index read works`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$", listOf(10, 20))
        assertEquals(10, ctx.get("\$[0]"))
        assertEquals(20, ctx.get("\$[1]"))
    }

    @Test
    fun `stored null is distinct from a missing path`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.maybe", null)
        assertNull(ctx.get("\$.maybe"))
        assertNull(ctx.get("\$.never"))
        // paths() tells them apart: only the stored one is listed.
        assertEquals(listOf("\$.maybe"), ctx.paths())
    }

    @Test
    fun `overwriting shrinks the size estimate correctly`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.x", "a very long string that costs many bytes")
        val large = ctx.size
        ctx.put("\$.x", "s")
        assertTrue("expected shrink, was $large -> ${ctx.size}", ctx.size < large)
        assertEquals("s", ctx.get("\$.x"))
    }

    // --- JSONPath validation ------------------------------------------------

    @Test
    fun `malformed paths are rejected`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        listOf("", "a", ".", "\$.", "\$.a..b", "\$.a b", "\$[x]", "\$.a[", "\$[1").forEach { bad ->
            try {
                ctx.put(bad, 1)
                fail("expected IllegalArgumentException for '$bad'")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `out of range list index is rejected`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.r", listOf("only"))
        try {
            ctx.put("\$.r[5]", "x")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    // --- 256KB budget -------------------------------------------------------

    @Test
    fun `write larger than the budget is rejected before mutation`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.keep", "before")
        val big = "x".repeat(300 * 1024)
        try {
            ctx.put("\$.big", big)
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // expected
        }
        // The failed write left no trace: previous value intact, no partial map.
        assertEquals("before", ctx.get("\$.keep"))
        assertNull(ctx.get("\$.big"))
        assertEquals(listOf("\$.keep"), ctx.paths())
    }

    @Test
    fun `crossing the budget on a later write is rejected transactionally`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.a", "y".repeat(100 * 1024))
        // Second write pushes the total over 256KB.
        try {
            ctx.put("\$.b", "z".repeat(200 * 1024))
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // expected
        }
        assertEquals("y".repeat(100 * 1024), ctx.get("\$.a"))
        assertNull(ctx.get("\$.b"))
        assertTrue(ctx.size <= WorkflowRunContext.MAX_BYTES)
    }

    @Test
    fun `filling to just under the budget is allowed`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        // ~200KB of strings, comfortably inside the 256KB budget.
        ctx.put("\$.payload", "p".repeat(200 * 1024))
        assertTrue(ctx.size <= WorkflowRunContext.MAX_BYTES)
        assertEquals("p".repeat(200 * 1024), ctx.get("\$.payload"))
    }

    // --- diagnostics / snapshot ----------------------------------------------

    @Test
    fun `paths lists the leaf json paths in order`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        ctx.put("\$.location.lat", 33.5)
        ctx.put("\$.results", listOf("a", "b"))
        // Deterministic lexicographic order.
        assertEquals(
            listOf("\$.location.lat", "\$.results[0]", "\$.results[1]", "\$.weather.temp"),
            ctx.paths()
        )
    }

    @Test
    fun `snapshot is a deep copy - mutating it does not affect the context`() {
        val ctx = WorkflowRunContext.create("a", 1L)
        ctx.put("\$.weather.temp", 21.4)
        val snap = ctx.snapshot() as Map<*, *>
        (snap["weather"] as MutableMap<*, *>).clear()
        assertEquals(21.4, ctx.get("\$.weather.temp"))
        // And the context's own document is unchanged.
        assertEquals(mapOf("weather" to mapOf("temp" to 21.4)), ctx.get("\$"))
    }
}
