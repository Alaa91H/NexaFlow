package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Shizuku UserService (AIDL) migration: response parsing,
 * the AIDL-first / legacy-fallback execution order, and the graceful failure
 * when every channel is unavailable. Mirrors the [RootGrantFlowTest] seam
 * style — the real binder and process spawning are replaced by the internal
 * probes ([ShizukuShellBridge.execProbe] / [ShizukuShellBridge.legacyProbe])
 * so the behaviour is deterministic on any host.
 */
class ShizukuShellBridgeTest {

    @After
    fun tearDown() {
        ShizukuShellBridge.execProbe = null
        ShizukuShellBridge.legacyProbe = null
    }

    // ──────────────────────────────────────────────────────────────
    // parseAidlResponse — the "exitCode\noutput" contract (pure)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `parse ok response with output`() {
        val r = ShizukuShellBridge.parseAidlResponse("0\nhello world", "cmd")
        assertTrue(r.success)
        assertEquals("hello world", r.message)
    }

    @Test
    fun `parse ok response with blank output reports command executed`() {
        val r = ShizukuShellBridge.parseAidlResponse("0\n", "cmd")
        assertTrue(r.success)
        assertEquals("Command executed", r.message)
    }

    @Test
    fun `parse non-zero exit reports the code and the output`() {
        val r = ShizukuShellBridge.parseAidlResponse("1\npermission denied", "cmd")
        assertFalse(r.success)
        assertTrue("expected exit 1 in message", r.message.contains("exit 1"))
        assertTrue("expected output in message", r.message.contains("permission denied"))
    }

    @Test
    fun `parse garbage response fails without crashing`() {
        val r = ShizukuShellBridge.parseAidlResponse("not-an-exit-code", "cmd")
        assertFalse(r.success)
        assertTrue(r.message.contains("unknown"))
    }

    // ──────────────────────────────────────────────────────────────
    // Execution order: AIDL first, legacy second, failure last
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `bound user service is preferred over the legacy channel`() {
        var legacyCalled = false
        ShizukuShellBridge.execProbe = { "0\nvia-aidl" }
        ShizukuShellBridge.legacyProbe = {
            legacyCalled = true
            SystemControlResult.fail("legacy must not run")
        }
        val r = ShizukuShellBridge.execute("echo hi")
        assertTrue(r.success)
        assertEquals("via-aidl", r.message)
        assertFalse("legacy channel must not be reached while AIDL is bound", legacyCalled)
    }

    @Test
    fun `legacy channel runs when no user service is bound`() {
        ShizukuShellBridge.execProbe = { null }
        ShizukuShellBridge.legacyProbe = { SystemControlResult.ok("via-legacy") }
        val r = ShizukuShellBridge.execute("echo hi")
        assertTrue(r.success)
        assertEquals("via-legacy", r.message)
    }

    @Test
    fun `both channels unavailable fails gracefully`() {
        ShizukuShellBridge.execProbe = { null }
        ShizukuShellBridge.legacyProbe = { SystemControlResult.fail("legacy failed") }
        val r = ShizukuShellBridge.execute("echo hi")
        assertFalse(r.success)
        assertEquals("legacy failed", r.message)
    }

    @Test
    fun `no probes uses the real reflection path and fails cleanly in a unit test`() {
        // With no binder and no su, the real newProcess reflection can never
        // succeed on the JVM — the important contract is that it fails into a
        // result instead of throwing out of the bridge.
        val r = ShizukuShellBridge.execute("echo hi")
        assertFalse(r.success)
    }

    // ──────────────────────────────────────────────────────────────
    // PrivilegedRunner gate — Shizuku must be granted first
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `runShizuku rejects when not granted without touching the bridge`() {
        var bridgeCalled = false
        ShizukuShellBridge.execProbe = {
            bridgeCalled = true
            null
        }
        val r = PrivilegedRunner.runShizuku("echo hi")
        assertFalse(r.success)
        assertTrue("expected a not-granted message", r.message.contains("not granted"))
        assertFalse("bridge must not run before the grant gate", bridgeCalled)
    }
}
