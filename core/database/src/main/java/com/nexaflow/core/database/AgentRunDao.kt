package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunDao {
    @Query("SELECT * FROM agent_runs WHERE agentId=:agentId AND idempotencyKeyHash=:keyHash LIMIT 1")
    suspend fun findByIdempotency(agentId: String, keyHash: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE id=:runId LIMIT 1")
    suspend fun findById(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE agentId=:agentId ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeForAgent(agentId: String, limit: Int = 100): Flow<List<AgentRunEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: AgentRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: AgentRunEventEntity)

    @Query("SELECT COUNT(*) FROM agent_runs WHERE agentId=:agentId AND status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun countActiveRuns(agentId: String): Int

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM agent_run_events WHERE runId=:runId")
    suspend fun lastEventSequence(runId: String): Long

    @Query("SELECT * FROM agent_run_events WHERE runId=:runId ORDER BY sequence ASC")
    fun observeEvents(runId: String): Flow<List<AgentRunEventEntity>>

    @Query("SELECT * FROM agent_run_events WHERE runId=:runId ORDER BY sequence ASC")
    suspend fun events(runId: String): List<AgentRunEventEntity>

    @Transaction
    suspend fun appendEventWithNextSequence(event: AgentRunEventEntity) {
        insertEvent(event.copy(sequence = lastEventSequence(event.runId) + 1L))
    }

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

    @Query("SELECT * FROM agent_approvals WHERE id=:approvalId LIMIT 1")
    suspend fun findApproval(approvalId: String): AgentApprovalEntity?

    @Query("SELECT * FROM agent_approvals WHERE id=:approvalId LIMIT 1")
    fun observeApproval(approvalId: String): Flow<AgentApprovalEntity?>

    @Query("SELECT * FROM agent_approvals WHERE runId=:runId AND resolvedAtMillis IS NULL ORDER BY createdAtMillis ASC")
    fun observePendingApprovals(runId: String): Flow<List<AgentApprovalEntity>>

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

    @Query("UPDATE agent_runs SET status='INTERRUPTED', outcomeCode='process_restarted', updatedAtMillis=:now, revision=revision+1 WHERE status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun interruptInFlight(now: Long): Int

    @Query("SELECT * FROM agent_runs WHERE status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL')")
    suspend fun inFlight(): List<AgentRunEntity>

    @Transaction
    suspend fun finishExactlyOnce(
        run: AgentRunEntity,
        terminalStatus: String,
        outcome: String,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (run.status == terminalStatus || run.status in TERMINAL_STATUSES) return false
        val changed = setTerminal(run.id, terminalStatus, outcome, run.revision, now)
        if (changed != 1) return false
        invalidatePendingApprovals(run.id, now)
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    @Transaction
    suspend fun insertRunAndQueuedEvent(
        run: AgentRunEntity,
        event: AgentRunEventEntity,
        maxConcurrentRuns: Int
    ): Boolean {
        if (countActiveRuns(run.agentId) >= maxConcurrentRuns) return false
        if (insertRun(run) < 0L) return false
        insertEvent(event)
        return true
    }

    @Transaction
    suspend fun transitionAndAppendEvent(
        runId: String,
        expectedStatus: String,
        nextStatus: String,
        expectedRevision: Long,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (transition(runId, expectedStatus, nextStatus, expectedRevision, now) != 1) return false
        insertEvent(event.copy(sequence = lastEventSequence(runId) + 1L))
        return true
    }

    @Transaction
    suspend fun requestApprovalAndWait(
        run: AgentRunEntity,
        approval: AgentApprovalEntity,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (transition(run.id, "RUNNING", "WAITING_FOR_APPROVAL", run.revision, now) != 1) return false
        insertApproval(approval)
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    @Transaction
    suspend fun resolveApprovalAndAppendEvent(
        approvalId: String,
        run: AgentRunEntity,
        fingerprint: String,
        deviceHash: String,
        decision: String,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        val changed = resolveApproval(
            approvalId = approvalId,
            runId = run.id,
            agentId = run.agentId,
            definitionRevision = run.definitionRevision,
            callFingerprint = fingerprint,
            deviceBindingHash = deviceHash,
            decision = decision,
            now = now
        )
        if (changed != 1) return false
        if (transition(run.id, "WAITING_FOR_APPROVAL", "RUNNING", run.revision, now) != 1) return false
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    @Transaction
    suspend fun expireApprovalAndResume(
        approvalId: String,
        run: AgentRunEntity,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (expireApproval(approvalId, run.id, now) != 1) return false
        if (transition(run.id, "WAITING_FOR_APPROVAL", "RUNNING", run.revision, now) != 1) return false
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    private companion object {
        val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED")
    }
}
