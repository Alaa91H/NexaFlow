package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentRunMutationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: AgentRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: AgentRunEventEntity)

    @Query("SELECT COUNT(*) FROM agent_runs WHERE agentId=:agentId AND status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun countActiveRuns(agentId: String): Int

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM agent_run_events WHERE runId=:runId")
    suspend fun lastEventSequence(runId: String): Long

    @Query("UPDATE agent_runs SET status=:nextStatus, updatedAtMillis=:now, revision=revision+1 WHERE id=:runId AND status=:expectedStatus AND revision=:expectedRevision")
    suspend fun transition(
        runId: String,
        expectedStatus: String,
        nextStatus: String,
        expectedRevision: Long,
        now: Long
    ): Int

    @Query("UPDATE agent_runs SET status=:terminalStatus, outcomeCode=:outcome, updatedAtMillis=:now, revision=revision+1 WHERE id=:runId AND status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL') AND revision=:expectedRevision")
    suspend fun setTerminal(
        runId: String,
        terminalStatus: String,
        outcome: String,
        expectedRevision: Long,
        now: Long
    ): Int

    @Query("UPDATE agent_runs SET turns=:turns, toolCalls=:toolCalls, outputCharacters=:outputCharacters, costMicros=:costMicros, providerId=:providerId, modelId=:modelId, updatedAtMillis=:now, revision=revision+1 WHERE id=:runId AND revision=:expectedRevision AND status IN ('RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun updateUsage(
        runId: String,
        turns: Int,
        toolCalls: Int,
        outputCharacters: Int,
        costMicros: Long,
        providerId: String?,
        modelId: String?,
        expectedRevision: Long,
        now: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApproval(approval: AgentApprovalEntity)

    @Query("UPDATE agent_approvals SET decision='INVALIDATED', resolvedAtMillis=:now WHERE runId=:runId AND resolvedAtMillis IS NULL")
    suspend fun invalidatePendingApprovals(runId: String, now: Long): Int

    @Query("UPDATE agent_approvals SET decision=:decision, resolvedAtMillis=:now WHERE id=:approvalId AND runId=:runId AND agentId=:agentId AND definitionRevision=:definitionRevision AND callFingerprint=:callFingerprint AND deviceBindingHash=:deviceBindingHash AND resolvedAtMillis IS NULL AND expiresAtMillis>:now")
    suspend fun resolveApproval(
        approvalId: String,
        runId: String,
        agentId: String,
        definitionRevision: Long,
        callFingerprint: String,
        deviceBindingHash: String,
        decision: String,
        now: Long
    ): Int

    @Query("UPDATE agent_approvals SET decision='EXPIRED', resolvedAtMillis=:now WHERE id=:approvalId AND runId=:runId AND resolvedAtMillis IS NULL AND expiresAtMillis<=:now")
    suspend fun expireApproval(approvalId: String, runId: String, now: Long): Int
}
