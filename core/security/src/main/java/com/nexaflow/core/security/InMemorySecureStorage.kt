package com.nexaflow.core.security

import java.util.concurrent.ConcurrentHashMap

/** In-memory [SecureStorage] — used by unit tests and as a safe fallback. */
class InMemorySecureStorage : SecureStorage {
    private val map = ConcurrentHashMap<String, String>()

    override suspend fun get(key: String): String? = map[key]

    override suspend fun put(key: String, value: String) {
        map[key] = value
    }

    override suspend fun remove(key: String) {
        map.remove(key)
    }

    override suspend fun clear() {
        map.clear()
    }
}
