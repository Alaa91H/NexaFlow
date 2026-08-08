package com.nexaflow.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the "active while the app stays in the foreground" lifecycle
 * of the app trigger. Pure JUnit: the tracker has no Android dependencies, so
 * every enter/exit transition and cooldown gate is deterministic.
 */
class AppForegroundTrackerTest {

    private val tracker = AppForegroundTracker(now = { fakeTime })

    // Simulated clock, advanced explicitly by each test.
    private var fakeTime = 1_000L

    private val taskA = AppForegroundTracker.Task(id = "a", cooldownMillis = 0)
    private val taskB = AppForegroundTracker.Task(id = "b", cooldownMillis = 0)

    /** Matcher: task "a" triggers on com.app.a, task "b" on com.app.b. */
    private fun matches(taskId: String, packageName: String): Boolean = when (taskId) {
        "a" -> packageName == "com.app.a"
        "b" -> packageName == "com.app.b"
        else -> false
    }

    private fun change(pkg: String, vararg tasks: AppForegroundTracker.Task) =
        tracker.onForegroundChange(pkg, tasks.toList(), ::matches)

    // ──────────────────────────────────────────────────────────────
    // Enter / hold / exit lifecycle
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `matching app opens triggers a run and activates the task`() {
        val commands = change("com.app.a", taskA)
        assertEquals(listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Run("a")), commands)
        assertTrue(tracker.isActive("a"))
    }

    @Test
    fun `leaving the app fires exit and deactivates the task`() {
        change("com.app.a", taskA)
        val commands = change("com.app.b", taskA)
        assertEquals(listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Exit("a")), commands)
        assertFalse(tracker.isActive("a"))
    }

    @Test
    fun `repeated events for the same app do not re-run`() {
        change("com.app.a", taskA)
        // A second window-state event for the same package (deduped by the
        // service normally, but the tracker must also guard it).
        val commands = change("com.app.a", taskA)
        assertTrue("already-active task must not re-run", commands.isEmpty())
        assertTrue(tracker.isActive("a"))
    }

    @Test
    fun `different app opens exits one task and runs the other`() {
        change("com.app.a", taskA, taskB)
        val commands = change("com.app.b", taskA, taskB)
        assertEquals(
            listOf<AppForegroundTracker.Command>(
                AppForegroundTracker.Command.Exit("a"),
                AppForegroundTracker.Command.Run("b")
            ),
            commands
        )
        assertFalse(tracker.isActive("a"))
        assertTrue(tracker.isActive("b"))
    }

    @Test
    fun `non-matching app never activates or exits anything`() {
        change("com.app.a", taskA)
        val commands = change("com.other.app", taskA, taskB)
        assertEquals(listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Exit("a")), commands)
        assertFalse(tracker.isActive("a"))
        assertFalse(tracker.isActive("b"))
    }

    @Test
    fun `opening the automation app itself ends active tasks (exit still fires)`() {
        change("com.app.a", taskA)
        // Previously the service early-returned on its own package, silently
        // keeping the task active forever; the tracker must still exit.
        val commands = change("com.nexaflow.app", taskA)
        assertEquals(listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Exit("a")), commands)
        assertFalse(tracker.isActive("a"))
    }

    // ──────────────────────────────────────────────────────────────
    // Cooldown gating
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `reopening within the cooldown does not re-run`() {
        val gated = AppForegroundTracker.Task(id = "a", cooldownMillis = 5_000)
        change("com.app.a", gated)          // t=1000: run
        change("com.app.b", gated)          // exit
        fakeTime += 2_000                   // t=3000: within cooldown
        val commands = change("com.app.a", gated)
        assertTrue("cooldown must block the re-run", commands.isEmpty())
        assertFalse(tracker.isActive("a"))
    }

    @Test
    fun `reopening after the cooldown elapses runs again`() {
        val gated = AppForegroundTracker.Task(id = "a", cooldownMillis = 5_000)
        change("com.app.a", gated)          // t=1000: run
        change("com.app.b", gated)          // exit
        fakeTime += 6_000                   // t=7000: cooldown elapsed
        val commands = change("com.app.a", gated)
        assertEquals(listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Run("a")), commands)
        assertTrue(tracker.isActive("a"))
    }

    @Test
    fun `exit fires only for tasks that were actually active`() {
        // "b" was never active: switching away must not fabricate an exit.
        change("com.app.a", taskA, taskB)
        val commands = change("com.app.b", taskA, taskB)
        assertEquals(
            listOf<AppForegroundTracker.Command>(
                AppForegroundTracker.Command.Exit("a"),
                AppForegroundTracker.Command.Run("b")
            ),
            commands
        )
    }

    @Test
    fun `task disabled mid-session fires exit on the next foreground change`() {
        change("com.app.a", taskA) // enabled: run + active
        var enabled = true
        // The user disables the task while it is active: the next change must
        // still end it (restore original state / run end options).
        val commands = tracker.onForegroundChange("com.app.b", listOf(taskA)) { id, pkg ->
            enabled && matches(id, pkg)
        }
        assertEquals(
            listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Exit("a")),
            commands
        )
        assertFalse(tracker.isActive("a"))
    }

    @Test
    fun `task matching two apps does not re-run when switching between them`() {
        val multi = AppForegroundTracker.Task(id = "m", cooldownMillis = 0)
        val matchMulti: (String, String) -> Boolean = { _, pkg ->
            pkg == "com.app.a" || pkg == "com.app.b"
        }
        tracker.onForegroundChange("com.app.a", listOf(multi), matchMulti) // run
        val whileStillOpen = tracker.onForegroundChange("com.app.b", listOf(multi), matchMulti)
        assertTrue("switching between matching apps must not re-run", whileStillOpen.isEmpty())
        assertTrue(tracker.isActive("m"))
        // Leaving both apps ends the session once.
        val exit = tracker.onForegroundChange("com.other.app", listOf(multi), matchMulti)
        assertEquals(
            listOf<AppForegroundTracker.Command>(AppForegroundTracker.Command.Exit("m")),
            exit
        )
        assertFalse(tracker.isActive("m"))
    }
}
