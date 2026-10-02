package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiCredentialReferences
import com.nexaflow.core.security.InMemorySecureStorage
import com.nexaflow.core.security.SecretVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultBackedAiCredentialStoreTest {

    @Test
    fun `legacy raw key is migrated into the secret vault on first resolve`() = runBlocking {
        val storage = InMemorySecureStorage()
        val vault = SecretVault(storage)
        val store = VaultBackedAiCredentialStore(vault, storage)
        val reference = AiCredentialReferences.forProfile("openai-main")
        storage.put(reference.value, "legacy-secret")

        assertEquals("legacy-secret", store.resolve(reference))
        assertNull(storage.get(reference.value))
        assertEquals("legacy-secret", vault.resolve(reference.value))
    }

    @Test
    fun `store and delete never leave the legacy raw key behind`() = runBlocking {
        val storage = InMemorySecureStorage()
        val vault = SecretVault(storage)
        val store = VaultBackedAiCredentialStore(vault, storage)
        val reference = AiCredentialReferences.forProfile("claude-main")
        storage.put(reference.value, "stale-secret")

        store.store(reference, "fresh-secret")

        assertNull(storage.get(reference.value))
        assertEquals("fresh-secret", store.resolve(reference))

        store.delete(reference)

        assertNull(store.resolve(reference))
    }
}
