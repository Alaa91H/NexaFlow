package com.nexaflow.core.execution.security

import com.nexaflow.core.security.SecretVault
import com.nexaflow.domain.variables.SecretStore

/**
 * Adapter that connects the domain-layer [SecretStore] interface
 * with the implementation-layer [SecretVault] from the core:security module.
 */
class SecretVaultAdapter(private val vault: SecretVault) : SecretStore {
    
    override suspend fun store(referenceKey: String, value: String) {
        vault.store(referenceKey, value)
    }

    override suspend fun resolve(referenceKey: String): String? {
        return vault.resolve(referenceKey)
    }

    override suspend fun delete(referenceKey: String) {
        vault.delete(referenceKey)
    }
}
