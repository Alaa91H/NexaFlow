package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentPlatformDao {

    @Query("SELECT * FROM automation_api_metadata WHERE automationId = :automationId")
    suspend fun getAutomationMetadata(automationId: String): AutomationApiMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationMetadata(metadata: AutomationApiMetadataEntity)

    @Query("DELETE FROM automation_api_metadata WHERE automationId = :automationId")
    suspend fun deleteAutomationMetadata(automationId: String)

    @Query(
        "SELECT * FROM agent_idempotency " +
            "WHERE actorId = :actorId AND keyHash = :keyHash LIMIT 1"
    )
    suspend fun getIdempotency(
        actorId: String,
        keyHash: String
    ): AgentIdempotencyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdempotency(record: AgentIdempotencyEntity)

    @Query(
        "SELECT * FROM agent_idempotency WHERE actorId = :actorId " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun idempotencyForActor(
        actorId: String,
        limit: Int
    ): List<AgentIdempotencyEntity>

    @Query("DELETE FROM agent_idempotency WHERE expiresAt <= :nowMillis")
    suspend fun pruneExpiredIdempotency(nowMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(event: AgentAuditEntity)

    @Query("SELECT * FROM agent_audit ORDER BY createdAt DESC LIMIT :limit")
    suspend fun latestAudit(limit: Int): List<AgentAuditEntity>

    @Query(
        "SELECT * FROM agent_audit WHERE automationId = :automationId " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun auditForAutomation(
        automationId: String,
        limit: Int
    ): List<AgentAuditEntity>
}
