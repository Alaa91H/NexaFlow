package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the elevated-route selection contract: a granted-but-
 * unusable transport (Shizuku server alive with its UserService gone, or a
 * dropped bind) must never mask the other granted runtime. Root is an
 * absolute privilege — any command or typed operation that fails on the
 * Shizuku transport must be retried through a granted root shell, and only
 * a failure from BOTH granted transports surfaces as an error.
 *
 * Every real transport is replaced by a seam ([PrivilegedRunner] probes and
 * [ShizukuShellBridge.operationProbe]), so the whole matrix runs on a plain
 * JVM without a Shizuku server or a real `su` binary.
 */
class PrivilegedRunnerRoutesTest {

    @After
    fun tearDown() {
        PrivilegedRunner.shizukuGrantProbe = null
        PrivilegedRunner.rootProbeOverride = null
        PrivilegedRunner.suRunnerProbe = null
        PrivilegedRunner.shellRouteProbe = null
        PrivilegedRunner.operationRouteProbe = null
        ShizukuShellBridge.operationProbe = null
    }

    // ── Root-only devices: everything executes through su ────────────────

    @Test
    fun `runShell executes through root when only root is granted`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("done") }

        val result = PrivilegedRunner.runShell("settings put global wifi_on 1")

        assertTrue(result.success)
    }

    @Test
    fun `runElevatedOperation executes through root when only root is granted`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("ok") }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ForceStopPackage("com.example.app")
        )

        assertTrue(result.success)
    }

    // ── THE reported regression: granted-but-unbound Shizuku + granted root ──

    @Test
    fun `runShell falls back to root when Shizuku is granted but its UserService is gone`() {
        // Shizuku grant passes, but no UserService is bound in this JVM
        // (operationProbe is unset), so the bridge reports an unavailable
        // transport. The command must still succeed through root.
        PrivilegedRunner.shizukuGrantProbe = { true }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("root-done") }

        val result = PrivilegedRunner.runShell("settings put global wifi_on 1")

        assertTrue("root fallback failed: ${result.message}", result.success)
    }

    @Test
    fun `runElevatedOperation falls back to root when Shizuku UserService is unbound`() {
        PrivilegedRunner.shizukuGrantProbe = { true }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("root-ok") }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.GrantNotificationPolicyAccess("com.nexaflow.app")
        )

        assertTrue("root fallback failed: ${result.message}", result.success)
        assertFalse(result.message.contains("reconnect Shizuku"))
    }

    @Test
    fun `runElevatedOperation falls back to root when Shizuku transport fails mid-operation`() {
        // The operation probe returns a transport-level failure (not a command
        // failure): "UserService is unavailable" — same as a dropped bind.
        PrivilegedRunner.shizukuGrantProbe = { true }
        ShizukuShellBridge.operationProbe = { null }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("root-ok") }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.WriteSetting(
                namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                key = "wifi_on",
                value = "1"
            )
        )

        assertTrue("root fallback failed: ${result.message}", result.success)
    }

    // ── Shizuku stays preferred while it actually works ───────────────────

    @Test
    fun `runElevatedOperation prefers working Shizuku over granted root`() {
        PrivilegedRunner.shizukuGrantProbe = { true }
        ShizukuShellBridge.operationProbe = { "0\nShizuku-ok" }
        PrivilegedRunner.rootProbeOverride = { true }
        var suInvocations = 0
        PrivilegedRunner.suRunnerProbe = {
            suInvocations++
            SystemControlResult.ok("must not be used")
        }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ForceStopPackage("com.example.app")
        )

        assertTrue(result.success)
        assertEquals(0, suInvocations)
    }

    // ── Shizuku-only devices keep working without a root demand ───────────

    @Test
    fun `runElevatedOperation succeeds through Shizuku without root`() {
        PrivilegedRunner.shizukuGrantProbe = { true }
        ShizukuShellBridge.operationProbe = { "0\nShizuku-ok" }
        PrivilegedRunner.rootProbeOverride = { false }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ForceStopPackage("com.example.app")
        )

        assertTrue(result.success)
    }

    // ── Genuine command failures must surface, not be retried forever ─────

    @Test
    fun `runShell surfaces a genuine root command failure`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = {
            SystemControlResult.fail("Root command failed (exit 1): bad usage")
        }

        val result = PrivilegedRunner.runShell("pm grant bad bad")

        assertFalse(result.success)
        assertTrue(result.message.contains("exit 1"))
    }

    @Test
    fun `runElevatedOperation surfaces the root failure when only root is granted`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = {
            SystemControlResult.fail("Root command failed (exit 1): rejected")
        }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ForceStopPackage("com.example.app")
        )

        assertFalse(result.success)
    }

    // ── No runtime at all keeps the stable, engine-recognized message ─────

    @Test
    fun `runShell without any runtime fails with the stable message`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { false }

        val result = PrivilegedRunner.runShell("settings put global wifi_on 1")

        assertFalse(result.success)
        assertEquals(
            "No elevated runtime available (Shizuku or root)",
            result.message
        )
    }

    @Test
    fun `runElevatedOperation without any runtime fails with the stable message`() {
        PrivilegedRunner.shizukuGrantProbe = { false }
        PrivilegedRunner.rootProbeOverride = { false }

        val result = PrivilegedRunner.runElevatedOperation(
            PrivilegedOperation.ForceStopPackage("com.example.app")
        )

        assertFalse(result.success)
        assertEquals(
            "No elevated runtime available (Shizuku or root)",
            result.message
        )
    }
}
