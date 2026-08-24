package com.nexaflow.core.execution.recovery

import org.junit.Assert.*
import org.junit.Test

class IdempotencyKeyTest {

    @Test
    fun `generate creates deterministic key`() {
        val key1 = IdempotencyKey.generate("run123", "node456")
        val key2 = IdempotencyKey.generate("run123", "node456")
        val key3 = IdempotencyKey.generate("run123", "node456", 1)
        
        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertEquals("run123:node456:0", key1.value)
    }

    @Test
    fun `rejects blank value`() {
        assertThrows(IllegalArgumentException::class.java) {
            IdempotencyKey("   ")
        }
    }
}
