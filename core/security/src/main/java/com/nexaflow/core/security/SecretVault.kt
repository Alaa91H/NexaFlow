package com.nexaflow.core.security

/**
 * Minimal opaque-secret facade over encrypted [SecureStorage]. The public API
 * accepts a reference key and never provides enumeration, export or logging of
 * stored values. Callers must avoid retaining a resolved value past an action.
 */
class SecretVault(private val secureStorage: SecureStorage) {

    suspend fun store(referenceKey: String, value: String) {
        validateReference(referenceKey)
        require(value.length <= MAX_SECRET_LENGTH) { "Secret exceeds maximum allowed length" }
        secureStorage.put(storageKey(referenceKey), value)
    }

    suspend fun resolve(referenceKey: String): String? {
        validateReference(referenceKey)
        return secureStorage.get(storageKey(referenceKey))
    }

    suspend fun delete(referenceKey: String) {
        validateReference(referenceKey)
        secureStorage.remove(storageKey(referenceKey))
    }

    private fun storageKey(referenceKey: String): String = "$PREFIX$referenceKey"

    private fun validateReference(referenceKey: String) {
        require(referenceKey.matches(REFERENCE_PATTERN)) { "Secret reference key has an invalid format" }
    }

    private companion object {
        const val PREFIX = "vault:"
        const val MAX_SECRET_LENGTH = 16_384
        val REFERENCE_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
