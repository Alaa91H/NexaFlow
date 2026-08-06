package com.nexaflow.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeCommandBuilderTest {

    @Test
    fun quote_wrapsInSingleQuotes() {
        assertEquals("'hello'", SafeCommandBuilder.quote("hello"))
    }

    @Test
    fun quote_escapesEmbeddedSingleQuote() {
        assertEquals("'it'\\''s'", SafeCommandBuilder.quote("it's"))
    }

    @Test
    fun build_joinsQuotedArgs() {
        val cmd = SafeCommandBuilder.build("settings", "put", "system", "screen_off_timeout", "30000")
        assertEquals("'settings' 'put' 'system' 'screen_off_timeout' '30000'", cmd)
    }

    @Test
    fun build_escapesInjectingArg() {
        val cmd = SafeCommandBuilder.build("sh", "-c", "id; rm -rf /")
        // The whole payload stays a single quoted argument — no command break-out.
        assertEquals("'sh' '-c' 'id; rm -rf /'", cmd)
    }

    @Test
    fun isSafeCommand_rejectsNulByte() {
        assertFalse(SafeCommandBuilder.isSafeCommand("echo a\u0000b"))
    }

    @Test
    fun isSafeCommand_rejectsControlChars() {
        assertFalse(SafeCommandBuilder.isSafeCommand("echo a\u0001b"))
    }

    @Test
    fun isSafeCommand_acceptsNormalText() {
        assertTrue(SafeCommandBuilder.isSafeCommand("echo hello world"))
    }

    @Test
    fun isSafeCommand_rejectsOverlongCommand() {
        val long = "a".repeat(SafeCommandBuilder.MAX_COMMAND_LENGTH + 1)
        assertFalse(SafeCommandBuilder.isSafeCommand(long))
    }

    @Test
    fun validateUserCommand_allowsValidAndRejectsInvalid() {
        assertEquals("echo ok", SafeCommandBuilder.validateUserCommand("echo ok"))
        assertNull(SafeCommandBuilder.validateUserCommand("echo a\u0000b"))
        assertNull(SafeCommandBuilder.validateUserCommand("   "))
    }
}
