package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentMemoryEntry
import com.nexaflow.core.security.SecureStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemoryRepositoryTest {
    @Test
    fun exportContainsOnlyActiveNotesForTheRequestedAgent() = runTest {
        val repository = AgentMemoryRepository(RecordingStorage())
        repository.save(entry("active", "agent-one"))
        repository.save(entry("expired", "agent-one", expires = 9L))
        repository.save(entry("private", "agent-two"))

        val exported = repository.exportSnapshot("agent-one", nowMillis = 10L)
        val entries = Json.decodeFromString<List<AgentMemoryEntry>>(exported)

        assertEquals(listOf("active"), entries.map { it.id })
        assertTrue(entries.all { it.agentId == "agent-one" })
    }

    @Test
    fun memoryIsScopedPerAgentAndExpiresWithoutTouchingOtherAgents() = runTest {
        val storage = RecordingStorage()
        val repository = AgentMemoryRepository(storage)
        repository.save(entry("one", "agent-one", expires = 20L))
        repository.save(entry("two", "agent-two"))

        assertEquals(listOf("one"), repository.observeSnapshot("agent-one", nowMillis = 10L).map { it.id })
        assertTrue(repository.observeSnapshot("agent-one", nowMillis = 20L).isEmpty())
        assertEquals(listOf("two"), repository.observeSnapshot("agent-two", nowMillis = 20L).map { it.id })
        assertTrue(storage.snapshot().keys.none { it.contains("agent-one") || it.contains("agent-two") })
    }

    @Test
    fun deleteAndClearRemoveOnlyTheSelectedAgentsEncryptedPayload() = runTest {
        val repository = AgentMemoryRepository(RecordingStorage())
        repository.save(entry("one", "agent-one"))
        repository.save(entry("two", "agent-one"))
        repository.save(entry("other", "agent-two"))

        assertTrue(repository.delete("agent-one", "one"))
        assertFalse(repository.delete("agent-one", "missing"))
        assertEquals(listOf("two"), repository.observeSnapshot("agent-one").map { it.id })
        repository.clear("agent-one")
        assertTrue(repository.observeSnapshot("agent-one").isEmpty())
        assertEquals(listOf("other"), repository.observeSnapshot("agent-two").map { it.id })
    }

    @Test
    fun memorySizeAndCountAreBounded() = runTest {
        val repository = AgentMemoryRepository(RecordingStorage())
        assertTrue(
            runCatching {
                entry("large", "agent-one")
                    .copy(content = "x".repeat(AgentMemoryEntry.MAX_CONTENT_CHARACTERS + 1))
            }.isFailure,
        )

        val full = (0 until AgentMemoryEntry.MAX_ENTRIES_PER_AGENT).map { index ->
            entry("entry-$index", "agent-one", createdAt = index.toLong())
        }
        for (entry in full) repository.save(entry)
        assertTrue(runCatching { repository.save(entry("overflow", "agent-one")) }.isFailure)
    }

    private fun entry(id: String, agentId: String, expires: Long? = null, createdAt: Long = 1L) =
        AgentMemoryEntry(id, agentId, "Note $id", "Private note $id", createdAt, expires)

    private class RecordingStorage : SecureStorage {
        private val values = mutableMapOf<String, String>()
        fun snapshot(): Map<String, String> = values.toMap()
        override suspend fun get(key: String): String? = values[key]
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
        override suspend fun clear() { values.clear() }
    }
}
