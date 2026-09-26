package com.nexaflow.core.agentsecurity

import com.nexaflow.core.security.SecureStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface AgentSecurityStore {
    suspend fun read(): AgentSecurityStateV1

    suspend fun <T> mutate(
        block: (AgentSecurityStateV1) -> Pair<AgentSecurityStateV1, T>
    ): T
}

/**
 * Stores one versioned state document inside the existing Keystore-backed
 * SecureStorage boundary. Corrupt/unsupported state reads fail closed, while
 * mutations are rejected so damaged authority state is never silently reset.
 */
class EncryptedAgentSecurityStore(
    private val secureStorage: SecureStorage,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : AgentSecurityStore {

    private val mutex = Mutex()

    override suspend fun read(): AgentSecurityStateV1 = mutex.withLock {
        loadUnlocked(failOnCorruption = false)
    }

    override suspend fun <T> mutate(
        block: (AgentSecurityStateV1) -> Pair<AgentSecurityStateV1, T>
    ): T = mutex.withLock {
        val current = loadUnlocked(failOnCorruption = true)
        val (next, result) = block(current)
        require(next.schemaVersion == AgentSecurityStateV1.CURRENT_SCHEMA_VERSION) {
            "Unsupported agent security state version"
        }
        secureStorage.put(
            STORAGE_KEY,
            json.encodeToString(AgentSecurityStateV1.serializer(), next)
        )
        result
    }

    private suspend fun loadUnlocked(
        failOnCorruption: Boolean
    ): AgentSecurityStateV1 {
        val encoded = secureStorage.get(STORAGE_KEY) ?: return AgentSecurityStateV1()
        val decoded = runCatching {
            json.decodeFromString(AgentSecurityStateV1.serializer(), encoded)
        }.getOrNull()
        if (decoded == null ||
            decoded.schemaVersion != AgentSecurityStateV1.CURRENT_SCHEMA_VERSION
        ) {
            if (failOnCorruption) {
                error("Agent security state is unreadable")
            }
            return AgentSecurityStateV1()
        }
        return decoded
    }

    companion object {
        internal const val STORAGE_KEY = "agent-security:state:v1"
    }
}
