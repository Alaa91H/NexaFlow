package com.nexaflow.domain.variables

/**
 * Domain-level contract for securely storing and resolving secrets.
 *
 * This decouples the Workflow Engine and AI layer from the Android Keystore
 * (implemented in the `core.security` module).
 *
 * Secrets are never exported, logged, or returned to the UI; they are resolved
 * strictly at execution time just before an action is dispatched to a backend.
 */
interface SecretStore {
    /**
     * Stores a secret value securely.
     * @param referenceKey The unique identifier for this secret (e.g., "api_key").
     * @param value The plaintext secret.
     */
    suspend fun store(referenceKey: String, value: String)

    /**
     * Resolves a secret by its reference key.
     * @param referenceKey The unique identifier.
     * @return The plaintext secret, or null if not found.
     */
    suspend fun resolve(referenceKey: String): String?

    /**
     * Deletes a secret from secure storage.
     * @param referenceKey The unique identifier.
     */
    suspend fun delete(referenceKey: String)
}

/**
 * Extends the basic [VariableResolver] to support asynchronous secret resolution.
 *
 * If a variable placeholder refers to a known secret (e.g., via a special naming
 * convention like `%SECRET_api_key` or similar mapped variables), this resolver
 * fetches it from the [SecretStore] at runtime.
 */
class SecureVariableResolver(private val secretStore: SecretStore) {

    /**
     * Resolves standard variables synchronously, and fetches secrets asynchronously.
     *
     * In NexaFlow, any variable mapped to a [SecretReference] must be resolved
     * through this method before backend execution.
     *
     * @param text The text containing placeholders (e.g., `%api_key`).
     * @param standardVariables Regular variables mapped to string values.
     * @param secretVariables Variables mapped to [SecretReference]s.
     */
    suspend fun resolveWithSecrets(
        text: String,
        standardVariables: Map<String, String>,
        secretVariables: Map<String, SecretReference>
    ): String {
        // First resolve standard variables
        val partiallyResolved = VariableResolver.resolve(text, standardVariables)

        // Then resolve secret variables
        val referencedPlaceholders = VariableResolver.referencedPlaceholders(partiallyResolved)
        
        var fullyResolved = partiallyResolved
        for (placeholder in referencedPlaceholders) {
            val secretRef = secretVariables[placeholder] ?: secretVariables[placeholder.lowercase()]
            if (secretRef != null) {
                val secretValue = secretStore.resolve(secretRef.key)
                if (secretValue != null) {
                    // VariableResolver replaces placeholders in a single pass, so
                    // secret values are inserted literally and are never re-parsed.
                    fullyResolved = VariableResolver.resolve(fullyResolved, mapOf(placeholder to secretValue))
                }
            }
        }

        return fullyResolved
    }
}
