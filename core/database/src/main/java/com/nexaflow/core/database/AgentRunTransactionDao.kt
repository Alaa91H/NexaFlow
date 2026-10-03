package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Transaction

/** Room transactions that keep run state, approval state, and its event ledger atomic. */
@Dao
abstract class AgentRunDao : AgentRunReadDao, AgentRunMutationDao {

    @Transaction
    open suspend fun appendEventWithNextSequence(event: AgentRunEventEntity) {
        insertEvent(event.copy(sequence = lastEventSequence(event.runId) + 1L))
    }

    @Transaction
    open suspend fun finishExactlyOnce(
        run: AgentRunEntity,
        terminalStatus: String,
        outcome: String,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (run.status == terminalStatus || run.status in TERMINAL_STATUSES) return false
        if (setTerminal(run.id, terminalStatus, outcome, run.revision, now) != 1) return false
        invalidatePendingApprovals(run.id, now)
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    @Transaction
    open suspend fun insertRunAndQueuedEvent(
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
    open suspend fun transitionAndAppendEvent(
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
    open suspend fun requestApprovalAndWait(
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
    open suspend fun resolveApprovalAndAppendEvent(
        approvalId: String,
        run: AgentRunEntity,
        fingerprint: String,
        deviceHash: String,
        decision: String,
        event: AgentRunEventEntity,
        now: Long
    ): Boolean {
        if (resolveApproval(
                approvalId = approvalId,
                runId = run.id,
                agentId = run.agentId,
                definitionRevision = run.definitionRevision,
                callFingerprint = fingerprint,
                deviceBindingHash = deviceHash,
                decision = decision,
                now = now
            ) != 1
        ) return false
        if (transition(run.id, "WAITING_FOR_APPROVAL", "RUNNING", run.revision, now) != 1) return false
        insertEvent(event.copy(sequence = lastEventSequence(run.id) + 1L))
        return true
    }

    @Transaction
    open suspend fun expireApprovalAndResume(
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
