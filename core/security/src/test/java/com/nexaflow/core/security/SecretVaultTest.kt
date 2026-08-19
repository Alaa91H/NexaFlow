package com.nexaflow.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretVaultTest {
    private class MemoryStorage : SecureStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = values[key]
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
        override suspend fun clear() { values.clear() }
    }

    @Test
    fun vaultStoresValuesBehindOpaquePrefixedReferences() = runBlocking {
        val storage = MemoryStorage()
        val vault = SecretVault(storage)

        vault.store("workflow.token-1", "super-secret-value")

        assertEquals("super-secret-value", vault.resolve("workflow.token-1"))
        assertEquals("super-secret-value", storage.values["vault:workflow.token-1"])
        vault.delete("workflow.token-1")
        assertNull(vault.resolve("workflow.token-1"))
    }

    @Test
    fun vaultRejectsUnsafeReferenceKey() {
        val vault = SecretVault(MemoryStorage())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.store("../../token", "value") }
        }
    }
}
