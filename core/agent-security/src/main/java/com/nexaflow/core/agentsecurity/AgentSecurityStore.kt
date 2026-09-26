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
 * SecureStorage boundary. Corrupt/unsupported state fails closed.
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
        loadUnlocked()
    }

    override suspend fun <T> mutate(
        block: (AgentSecurityStateV1) -> Pair<AgentSecurityStateV1, T>
    ): T = mutex.withLock {
        val current = loadUnlocked()
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

    private suspend fun loadUnlocked(): AgentSecurityStateV1 {
        val encoded = secureStorage.get(STORAGE_KEY) ?: return AgentSecurityStateV1()
        val decoded = runCatching {
            json.decodeFromString(AgentSecurityStateV1.serializer(), encoded)
        }.getOrNull() ?: return AgentSecurityStateV1()
        return decoded.takeIf {
            it.schemaVersion == AgentSecurityStateV1.CURRENT_SCHEMA_VERSION
        } ?: AgentSecurityStateV1()
    }

    companion object {
        internal const val STORAGE_KEY = "agent-security:state:v1"
    }
}
