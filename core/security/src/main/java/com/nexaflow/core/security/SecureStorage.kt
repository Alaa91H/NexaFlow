package com.nexaflow.core.security

/**
 * Encrypted key-value storage for secrets (ADB pairing tokens, Shizuku state,
 * plugin keys). Implementations are expected to encrypt at rest; the framework
 * only ever talks to this interface so the backing store can be swapped.
 */
interface SecureStorage {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}
