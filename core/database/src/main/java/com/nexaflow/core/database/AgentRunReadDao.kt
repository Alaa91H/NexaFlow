package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunReadDao {
    @Query("SELECT * FROM agent_runs WHERE agentId=:agentId AND idempotencyKeyHash=:keyHash LIMIT 1")
    suspend fun findByIdempotency(agentId: String, keyHash: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE id=:runId LIMIT 1")
    suspend fun findById(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE agentId=:agentId ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeForAgent(agentId: String, limit: Int = 100): Flow<List<AgentRunEntity>>

    @Query("SELECT * FROM agent_run_events WHERE runId=:runId ORDER BY sequence ASC")
    fun observeEvents(runId: String): Flow<List<AgentRunEventEntity>>

    @Query("SELECT * FROM agent_run_events WHERE runId=:runId ORDER BY sequence ASC")
    suspend fun events(runId: String): List<AgentRunEventEntity>

    @Query("SELECT * FROM agent_approvals WHERE id=:approvalId LIMIT 1")
    suspend fun findApproval(approvalId: String): AgentApprovalEntity?

    @Query("SELECT * FROM agent_approvals WHERE id=:approvalId LIMIT 1")
    fun observeApproval(approvalId: String): Flow<AgentApprovalEntity?>

    @Query("SELECT * FROM agent_approvals WHERE runId=:runId AND resolvedAtMillis IS NULL ORDER BY createdAtMillis ASC")
    fun observePendingApprovals(runId: String): Flow<List<AgentApprovalEntity>>

    @Query("SELECT * FROM agent_runs WHERE status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun inFlight(): List<AgentRunEntity>
}
