package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentMemoryEntry
import com.nexaflow.core.security.SecureStorage
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores user-authored memory as one encrypted per-agent blob. The production
 * SecureStorage binding uses Android Keystore AES-GCM; no memory text reaches
 * Room, run events, backups or logs.
 */
@Singleton
class AgentMemoryRepository @Inject constructor(
    private val secureStorage: SecureStorage,
) {
    private val mutex = Mutex()

    suspend fun observeSnapshot(agentId: String, nowMillis: Long = System.currentTimeMillis()): List<AgentMemoryEntry> =
        mutex.withLock {
            val entries = read(agentId)
            val active = entries.filter { it.isActiveAt(nowMillis) }.sortedByDescending { it.createdAtMillis }
            if (active.size != entries.size) write(agentId, active)
            active
        }

    /** Plain JSON is returned only for an explicit user-selected export destination. */
    suspend fun exportSnapshot(agentId: String, nowMillis: Long = System.currentTimeMillis()): String =
        json.encodeToString(observeSnapshot(agentId, nowMillis))

    suspend fun save(entry: AgentMemoryEntry) = mutex.withLock {
        val entries = read(entry.agentId).filter { it.isActiveAt(System.currentTimeMillis()) }
        val next = (entries.filterNot { it.id == entry.id } + entry)
            .sortedByDescending { it.createdAtMillis }
        require(next.size <= AgentMemoryEntry.MAX_ENTRIES_PER_AGENT) { "Agent memory entry limit reached" }
        require(next.sumOf { it.content.length } <= AgentMemoryEntry.MAX_TOTAL_CONTENT_CHARACTERS) {
            "Agent memory size limit reached"
        }
        write(entry.agentId, next)
    }

    suspend fun delete(agentId: String, entryId: String): Boolean = mutex.withLock {
        val entries = read(agentId)
        val next = entries.filterNot { it.id == entryId }
        if (next.size == entries.size) return@withLock false
        write(agentId, next)
        true
    }

    suspend fun clear(agentId: String) = mutex.withLock {
        secureStorage.remove(storageKey(agentId))
    }

    private suspend fun read(agentId: String): List<AgentMemoryEntry> {
        require(agentId.isNotBlank() && agentId.length <= 128)
        val encoded = secureStorage.get(storageKey(agentId)) ?: return emptyList()
        return try {
            json.decodeFromString<List<AgentMemoryEntry>>(encoded).also { entries ->
                require(entries.all { it.agentId == agentId }) { "Agent memory scope mismatch" }
                require(entries.size <= AgentMemoryEntry.MAX_ENTRIES_PER_AGENT)
                require(entries.sumOf { it.content.length } <= AgentMemoryEntry.MAX_TOTAL_CONTENT_CHARACTERS)
                require(entries.map { it.id }.distinct().size == entries.size)
            }
        } catch (failure: SerializationException) {
            throw AgentMemoryStorageException(agentId, failure)
        } catch (failure: IllegalArgumentException) {
            throw AgentMemoryStorageException(agentId, failure)
        }
    }

    private suspend fun write(agentId: String, entries: List<AgentMemoryEntry>) {
        val key = storageKey(agentId)
        if (entries.isEmpty()) secureStorage.remove(key)
        else secureStorage.put(key, json.encodeToString(entries))
    }

    private fun storageKey(agentId: String): String =
        STORAGE_PREFIX + sha256(agentId)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val STORAGE_PREFIX = "managed-agent-memory-v1-"
        val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    }
}

class AgentMemoryStorageException(
    agentId: String,
    cause: Throwable,
) : IllegalStateException("Stored memory for agent is invalid: $agentId", cause)
