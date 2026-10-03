package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentBudget
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentPolicy
import com.nexaflow.core.database.AgentDefinitionDao
import com.nexaflow.core.database.AgentDefinitionEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface AgentDefinitionWriteResult {
    data class Saved(val definition: AgentDefinition) : AgentDefinitionWriteResult
    data object Conflict : AgentDefinitionWriteResult
    data object InvalidRevision : AgentDefinitionWriteResult
}

/** Version-aware local storage. Only provider-profile references are stored, never secrets. */
class AgentDefinitionRepository @Inject constructor(
    private val dao: AgentDefinitionDao
) {
    fun observeAll(): Flow<List<AgentDefinition>> = dao.observeAll().map { rows ->
        rows.map { it.toDefinition() }
    }

    suspend fun find(id: String): AgentDefinition? = dao.findById(id)?.toDefinition()

    suspend fun create(definition: AgentDefinition): AgentDefinitionWriteResult {
        if (definition.revision != INITIAL_REVISION) {
            return AgentDefinitionWriteResult.InvalidRevision
        }
        val inserted = dao.insertIfAbsent(definition.toEntity())
        return if (inserted != INSERT_IGNORED) {
            AgentDefinitionWriteResult.Saved(definition)
        } else {
            AgentDefinitionWriteResult.Conflict
        }
    }

    suspend fun update(
        definition: AgentDefinition,
        expectedRevision: Long
    ): AgentDefinitionWriteResult {
        if (expectedRevision < INITIAL_REVISION || definition.revision != expectedRevision + 1L) {
            return AgentDefinitionWriteResult.InvalidRevision
        }
        val updated = dao.updateIfRevisionMatches(
            id = definition.id,
            name = definition.name,
            description = definition.description,
            systemInstructions = definition.systemInstructions,
            providerProfileId = definition.providerProfileId,
            modelId = definition.modelId,
            policyJson = json.encodeToString(definition.policy),
            budgetJson = json.encodeToString(definition.budget),
            enabled = definition.enabled,
            newRevision = definition.revision,
            updatedAtMillis = definition.updatedAtMillis,
            expectedRevision = expectedRevision
        )
        return if (updated == 1) {
            AgentDefinitionWriteResult.Saved(definition)
        } else {
            AgentDefinitionWriteResult.Conflict
        }
    }

    suspend fun delete(id: String, expectedRevision: Long): Boolean =
        expectedRevision >= INITIAL_REVISION &&
            dao.deleteIfRevisionMatches(id, expectedRevision) == 1

    private fun AgentDefinition.toEntity() = AgentDefinitionEntity(
        id = id,
        name = name,
        description = description,
        systemInstructions = systemInstructions,
        providerProfileId = providerProfileId,
        modelId = modelId,
        policyJson = json.encodeToString(policy),
        budgetJson = json.encodeToString(budget),
        enabled = enabled,
        revision = revision,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    private fun AgentDefinitionEntity.toDefinition(): AgentDefinition = try {
        AgentDefinition(
            id = id,
            name = name,
            description = description,
            systemInstructions = systemInstructions,
            providerProfileId = providerProfileId,
            modelId = modelId,
            policy = json.decodeFromString<AgentPolicy>(policyJson),
            budget = json.decodeFromString<AgentBudget>(budgetJson),
            enabled = enabled,
            revision = revision,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    } catch (failure: SerializationException) {
        throw AgentDefinitionStorageException(id, failure)
    } catch (failure: IllegalArgumentException) {
        throw AgentDefinitionStorageException(id, failure)
    }

    private companion object {
        const val INITIAL_REVISION = 1L
        const val INSERT_IGNORED = -1L
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

class AgentDefinitionStorageException(
    agentId: String,
    cause: Throwable
) : IllegalStateException("Stored agent definition is invalid: $agentId", cause)
