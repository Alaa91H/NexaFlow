package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDefinitionDao {
    @Query("SELECT * FROM agent_definitions ORDER BY updatedAtMillis DESC, id ASC")
    fun observeAll(): Flow<List<AgentDefinitionEntity>>

    @Query("SELECT * FROM agent_definitions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AgentDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AgentDefinitionEntity): Long

    @Query(
        "UPDATE agent_definitions SET name=:name, description=:description, " +
            "systemInstructions=:systemInstructions, providerProfileId=:providerProfileId, " +
            "modelId=:modelId, policyJson=:policyJson, budgetJson=:budgetJson, enabled=:enabled, " +
            "revision=:newRevision, updatedAtMillis=:updatedAtMillis " +
            "WHERE id=:id AND revision=:expectedRevision"
    )
    suspend fun updateIfRevisionMatches(
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
    ): Int

    @Query("DELETE FROM agent_definitions WHERE id=:id AND revision=:expectedRevision")
    suspend fun deleteIfRevisionMatches(id: String, expectedRevision: Long): Int
}
