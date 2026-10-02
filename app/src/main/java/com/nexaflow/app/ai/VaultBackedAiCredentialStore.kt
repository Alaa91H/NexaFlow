package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiCredentialReference
import com.nexaflow.core.airuntime.AiCredentialStore
import com.nexaflow.core.security.SecretVault
import com.nexaflow.core.security.SecureStorage

/**
 * SecretVault-backed AI credential store with one-way lazy migration from the
 * pre-vault SecureStorage key that used the same opaque reference.
 */
class VaultBackedAiCredentialStore(
    private val vault: SecretVault,
    private val legacyStorage: SecureStorage
) : AiCredentialStore {

    override suspend fun resolve(reference: AiCredentialReference): String? {
        vault.resolve(reference.value)?.let { return it }
        val legacy = legacyStorage.get(reference.value)?.takeIf(String::isNotBlank)
            ?: return null
        vault.store(reference.value, legacy)
        legacyStorage.remove(reference.value)
        return legacy
    }

    override suspend fun store(reference: AiCredentialReference, value: String) {
        require(value.isNotBlank()) { "Credential must not be blank" }
        vault.store(reference.value, value)
        legacyStorage.remove(reference.value)
    }

    override suspend fun delete(reference: AiCredentialReference) {
        vault.delete(reference.value)
        legacyStorage.remove(reference.value)
    }
}
