package com.nexaflow.core.security

/**
 * Encrypted key-value storage for secrets (ADB pairing tokens, Shizuku state,
 * plugin keys). Implementations are expected to encrypt at rest; the framework
 * only ever talks to this interface so the backing store can be swapped.
 */
interface SecureStorage {
    suspend fun get(key: String): String?

    /** Distinguishes an absent entry from an entry that exists but cannot be decrypted. */
    suspend fun read(key: String): SecureStorageReadResult =
        get(key)?.let(SecureStorageReadResult::Stored) ?: SecureStorageReadResult.Missing

    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

sealed interface SecureStorageReadResult {
    data object Missing : SecureStorageReadResult
    data class Stored(val value: String) : SecureStorageReadResult
    data object Unreadable : SecureStorageReadResult
}
