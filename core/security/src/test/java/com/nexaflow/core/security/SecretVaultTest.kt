package com.nexaflow.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Contract tests for the secret vault: namespaced storage keys, validation of
 * reference keys and secret size, and full store/resolve/delete round-trips
 * over the in-memory [SecureStorage] backend.
 */
class SecretVaultTest {

    private val storage = InMemorySecureStorage()
    private val vault = SecretVault(storage)

    @Test
    fun `store and resolve round-trip through a namespaced key`() = runBlocking {
        vault.store("api_key", "s3cret")
        assertEquals("s3cret", vault.resolve("api_key"))
        // The resolved value is only reachable through the vault API.
        assertNull(storage.get("api_key"))
        assertEquals("s3cret", storage.get("vault:api_key"))
    }

    @Test
    fun `delete removes the secret`() = runBlocking {
        vault.store("api_key", "s3cret")
        vault.delete("api_key")
        assertNull(vault.resolve("api_key"))
    }

    @Test
    fun `invalid reference keys are rejected on every operation`() {
        listOf("", " key", "k y", "ключ").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { vault.store(key, "v") }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { vault.resolve(key) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { vault.delete(key) }
            }
        }
    }

    @Test
    fun `oversized secret is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.store("api_key", "x".repeat(16_385)) }
        }
    }

    @Test
    fun `max-size secret is accepted`() = runBlocking {
        val value = "x".repeat(16_384)
        vault.store("api_key", value)
        assertEquals(value, vault.resolve("api_key"))
    }
}
