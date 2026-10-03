package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentBudget
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentPolicy
import com.nexaflow.core.database.AgentDefinitionDao
import com.nexaflow.core.database.AgentDefinitionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDefinitionRepositoryTest {

    @Test
    fun definitionsRoundTripAndWritesUseOptimisticRevisions() = runTest {
        val dao = FakeAgentDefinitionDao()
        val repository = AgentDefinitionRepository(dao)
        val initial = AgentDefinition(
            id = "home-assistant",
            name = "Home assistant",
            systemInstructions = "Only control explicitly allowed devices.",
            providerProfileId = "openai-work",
            policy = AgentPolicy(
                allowedToolNames = setOf("device.status", "device.lock"),
                approvalRequiredToolNames = setOf("device.lock")
            ),
            budget = AgentBudget(maxTurns = 4, maxCostMicros = 250_000L),
            createdAtMillis = 1,
            updatedAtMillis = 1
        )

        assertEquals(AgentDefinitionWriteResult.Saved(initial), repository.create(initial))
        assertEquals(initial, repository.find(initial.id))

        val updated = initial.copy(enabled = true, revision = 2, updatedAtMillis = 2)
        assertEquals(AgentDefinitionWriteResult.Saved(updated), repository.update(updated, 1))
        assertEquals(updated, repository.find(initial.id))
        assertEquals(AgentDefinitionWriteResult.Conflict, repository.update(updated, 1))
        assertFalse(repository.delete(initial.id, expectedRevision = 1))
        assertTrue(repository.delete(initial.id, expectedRevision = 2))
        assertEquals(null, repository.find(initial.id))
    }

    private class FakeAgentDefinitionDao : AgentDefinitionDao {
        private val rows = linkedMapOf<String, AgentDefinitionEntity>()
        private val state = MutableStateFlow<List<AgentDefinitionEntity>>(emptyList())

        override fun observeAll(): Flow<List<AgentDefinitionEntity>> = state

        override suspend fun findById(id: String): AgentDefinitionEntity? = rows[id]

        override suspend fun insertIfAbsent(entity: AgentDefinitionEntity): Long {
            if (entity.id in rows) return -1L
            rows[entity.id] = entity
            publish()
            return rows.size.toLong()
        }

        override suspend fun updateIfRevisionMatches(
            id: String,
            name: String,
            description: String,
            systemInstructions: String,
            providerProfileId: String?,
            modelId: String?,
            policyJson: String,
            budgetJson: String,
            enabled: Boolean,
            newRevision: Long,
            updatedAtMillis: Long,
            expectedRevision: Long
        ): Int {
            val current = rows[id]?.takeIf { it.revision == expectedRevision } ?: return 0
            rows[id] = current.copy(
                name = name,
                description = description,
                systemInstructions = systemInstructions,
                providerProfileId = providerProfileId,
                modelId = modelId,
                policyJson = policyJson,
                budgetJson = budgetJson,
                enabled = enabled,
                revision = newRevision,
                updatedAtMillis = updatedAtMillis
            )
            publish()
            return 1
        }

        override suspend fun deleteIfRevisionMatches(id: String, expectedRevision: Long): Int {
            if (rows[id]?.revision != expectedRevision) return 0
            rows.remove(id)
            publish()
            return 1
        }

        private fun publish() {
            state.value = rows.values.toList()
        }
    }
}
