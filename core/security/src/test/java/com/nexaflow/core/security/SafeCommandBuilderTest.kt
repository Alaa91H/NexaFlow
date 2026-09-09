package com.nexaflow.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security contract for command construction handed to elevated runtimes:
 * arguments are always single-quoted POSIX-style, control characters and
 * oversized commands are rejected, and user-supplied commands are validated
 * before execution.
 */
class SafeCommandBuilderTest {

    @Test
    fun `arguments are single quoted with embedded quotes escaped`() {
        assertEquals("'rm -rf'", SafeCommandBuilder.quote("rm -rf"))
        assertEquals("'it'\\''s'", SafeCommandBuilder.quote("it's"))
    }

    @Test
    fun `build quotes the program and every argument`() {
        assertEquals("'settings' 'put' 'system' 'a b'", SafeCommandBuilder.build("settings", "put", "system", "a b"))
    }

    @Test
    fun `control characters make a command unsafe`() {
        assertTrue(SafeCommandBuilder.isSafeCommand("settings put system a"))
        assertFalse(SafeCommandBuilder.isSafeCommand("settings\u0000 put"))
        assertFalse(SafeCommandBuilder.isSafeCommand("settings\u0007 put"))
        assertFalse(SafeCommandBuilder.isSafeCommand("x".repeat(8193)))
        // Whitespace is legitimate.
        assertTrue(SafeCommandBuilder.isSafeCommand("echo\ttab\ncrlf"))
    }

    @Test
    fun `validateUserCommand returns null for blank or unsafe input`() {
        assertNull(SafeCommandBuilder.validateUserCommand("   "))
        assertNull(SafeCommandBuilder.validateUserCommand("bad\u0000cmd"))
        assertEquals("wm density 440", SafeCommandBuilder.validateUserCommand("wm density 440"))
    }
}
