package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentPlatformDao {

    @Query("SELECT * FROM agent_automation_approvals WHERE decision IS NULL AND expiresAt > :now ORDER BY expiresAt ASC")
    suspend fun pendingAutomationApprovals(now: Long): List<AgentAutomationApprovalEntity>

    @Query("UPDATE agent_automation_approvals SET decision = 'REJECTED', approvedAt = :now WHERE id = :id AND agentId = :agentId AND contentHash = :contentHash AND decision IS NULL AND expiresAt > :now")
    suspend fun rejectAutomationApproval(id: String, agentId: String, contentHash: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAutomationApproval(approval: AgentAutomationApprovalEntity)

    @Query("SELECT * FROM agent_automation_approvals WHERE id = :id LIMIT 1")
    suspend fun findAutomationApproval(id: String): AgentAutomationApprovalEntity?

    @Query("UPDATE agent_automation_approvals SET approvedAt = :now, decision = 'APPROVED' WHERE id = :id AND agentId = :agentId AND contentHash = :contentHash AND riskLevel = :riskLevel AND decision IS NULL AND expiresAt > :now")
    suspend fun approveAutomationContent(
        id: String,
        agentId: String,
        contentHash: String,
        riskLevel: String,
        now: Long
    ): Int

    @Query("UPDATE agent_automation_approvals SET approvedAt = :now, decision = 'CONSUMED' WHERE id = :id AND agentId = :agentId AND contentHash = :contentHash AND riskLevel = :riskLevel AND decision = 'APPROVED' AND expiresAt > :now")
    suspend fun consumeAutomationApproval(
        id: String,
        agentId: String,
        contentHash: String,
        riskLevel: String,
        now: Long
    ): Int

    @Query("DELETE FROM agent_automation_approvals WHERE expiresAt <= :now OR decision = 'CONSUMED'")
    suspend fun pruneAutomationApprovals(now: Long): Int

    @Query("UPDATE automation_api_metadata SET approvedContentHash = :contentHash WHERE automationId = :automationId AND origin = 'AGENT'")
    suspend fun bindApprovedAutomationContent(automationId: String, contentHash: String): Int

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

    /**
     * Atomically reserves one actor-scoped idempotency key for a side effect.
     * Room returns -1 when another request already owns the key.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun reserveIdempotency(record: AgentIdempotencyEntity): Long

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

    @Query("DELETE FROM agent_audit WHERE createdAt < :cutoffMillis")
    suspend fun pruneAuditBefore(cutoffMillis: Long): Int

    // UUID audit ids are intentionally opaque, so rowid preserves insertion
    // order when multiple audit events share the same millisecond timestamp.
    @Query(
        "DELETE FROM agent_audit WHERE id NOT IN (" +
            "SELECT id FROM agent_audit ORDER BY createdAt DESC, rowid DESC LIMIT :keepCount" +
            ")"
    )
    suspend fun pruneAuditToNewest(keepCount: Int): Int

    @Query("DELETE FROM agent_audit")
    suspend fun clearAudit()

    @Query("SELECT * FROM agent_audit ORDER BY createdAt DESC, rowid DESC LIMIT :limit")
    suspend fun latestAudit(limit: Int): List<AgentAuditEntity>

    @Query(
        "SELECT * FROM agent_audit WHERE automationId = :automationId " +
            "ORDER BY createdAt DESC, rowid DESC LIMIT :limit"
    )
    suspend fun auditForAutomation(
        automationId: String,
        limit: Int
    ): List<AgentAuditEntity>
}
