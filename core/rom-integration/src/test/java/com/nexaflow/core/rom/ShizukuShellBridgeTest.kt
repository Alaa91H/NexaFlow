package com.nexaflow.core.rom

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the public UserService transport contract. The legacy reflection path
 * is intentionally absent: typed operations use only AIDL and an unavailable
 * binder is represented as a normal result by the production bridge.
 */
class ShizukuShellBridgeTest {

    @After
    fun tearDown() {
        ShizukuShellBridge.operationProbe = null
        ShizukuShellBridge.legacyExecProbe = null
    }

    @Test
    fun `parse ok response with output`() {
        val result = ShizukuShellBridge.parseAidlResponse("0\nhello world", "settings.write")

        assertTrue(result.success)
        assertEquals("hello world", result.message)
    }

    @Test
    fun `parse blank typed response reports operation executed`() {
        val result = ShizukuShellBridge.parseAidlResponse("0\n", "package.force_stop")

        assertTrue(result.success)
        assertEquals("Operation executed", result.message)
    }

    @Test
    fun `parse non-zero exit reports the code and output`() {
        val result = ShizukuShellBridge.parseAidlResponse("1\npermission denied", "package.force_stop")

        assertFalse(result.success)
        assertTrue(result.message.contains("exit 1"))
        assertTrue(result.message.contains("permission denied"))
    }

    @Test
    fun `typed wire operation round trips only allowlisted shape`() {
        val operation = PrivilegedOperation.fromWire(
            wireId = "settings.write",
            first = "GLOBAL",
            second = "airplane_mode_on",
            third = "1"
        )

        assertEquals(
            PrivilegedOperation.WriteSetting(
                PrivilegedOperation.SettingNamespace.GLOBAL,
                "airplane_mode_on",
                "1"
            ),
            operation
        )
        assertEquals(null, PrivilegedOperation.fromWire("shell.exec", "echo hi", "", ""))
    }

    @Test
    fun `typed operation rejects a shell-like package name`() {
        val operation = PrivilegedOperation.fromWire(
            wireId = "package.force_stop",
            first = "com.example.app;reboot",
            second = "",
            third = ""
        )

        assertEquals(null, operation)
    }

    @Test
    fun `runShizuku rejects when not granted without touching legacy bridge`() {
        var bridgeCalled = false
        ShizukuShellBridge.legacyExecProbe = {
            bridgeCalled = true
            "0\nshould not run"
        }

        val result = PrivilegedRunner.runShizuku("echo hi")

        assertFalse(result.success)
        assertTrue(result.message.contains("not granted"))
        assertFalse(bridgeCalled)
    }
}
