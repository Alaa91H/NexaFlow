package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentApproval
import com.nexaflow.core.agentruntime.AgentApprovalDecision
import com.nexaflow.core.agentruntime.AgentRun
import com.nexaflow.core.agentruntime.AgentRunEvent
import com.nexaflow.core.agentruntime.AgentRunEventType
import com.nexaflow.core.agentruntime.AgentRunStatus
import com.nexaflow.core.database.AgentApprovalEntity
import com.nexaflow.core.database.AgentRunDao
import com.nexaflow.core.database.AgentRunEntity
import com.nexaflow.core.database.AgentRunEventEntity
import com.nexaflow.core.security.InMemorySecureStorage
import com.nexaflow.core.security.SecureStorage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AgentRunStartResult {
    data class Created(val run: AgentRun) : AgentRunStartResult
    data class Existing(val run: AgentRun) : AgentRunStartResult
    data object IdempotencyConflict : AgentRunStartResult
    data object ConcurrentRunLimit : AgentRunStartResult
}

/** Durable run ledger. Event codes are constrained to low-cardinality, payload-free facts. */
@Singleton
class AgentRunRepository private constructor(
    private val dao: AgentRunDao,
    private val secureStorage: SecureStorage,
    private val nowMillis: () -> Long,
    private val idGenerator: () -> String
) {
    @Inject
    constructor(dao: AgentRunDao, secureStorage: SecureStorage) :
        this(dao, secureStorage, System::currentTimeMillis, { UUID.randomUUID().toString() })

    constructor(dao: AgentRunDao) :
        this(dao, InMemorySecureStorage(), System::currentTimeMillis, { UUID.randomUUID().toString() })

    internal constructor(dao: AgentRunDao, nowMillis: () -> Long, idGenerator: () -> String, testOnly: Unit = Unit) :
        this(dao, InMemorySecureStorage(), nowMillis, idGenerator)

    private val fingerprintKeyMutex = Mutex()
    @Volatile private var cachedFingerprintKey: ByteArray? = null

    suspend fun start(
        agentId: String,
        idempotencyKey: String,
        requestFingerprint: String,
        definitionRevision: Long,
        maxConcurrentRuns: Int,
        deadlineAtMillis: Long
    ): AgentRunStartResult {
        require(agentId.isNotBlank() && idempotencyKey.length in 1..256)
        require(requestFingerprint.isNotBlank() && requestFingerprint.length <= 256)
        require(definitionRevision > 0L && maxConcurrentRuns in 1..8)
        val idempotencyHash = sha256("$agentId\u0000$idempotencyKey")
        val fingerprintHash = hmac(awaitFingerprintKey(), requestFingerprint)
        dao.findByIdempotency(agentId, idempotencyHash)?.let { existing ->
            val run = existing.toRun()
            return if (run.requestFingerprint == fingerprintHash) {
                AgentRunStartResult.Existing(run)
            } else {
                AgentRunStartResult.IdempotencyConflict
            }
        }
        val now = nowMillis().coerceAtLeast(0L)
        require(deadlineAtMillis >= now)
        val run = AgentRun(
            id = idGenerator(),
            agentId = agentId,
            idempotencyKeyHash = idempotencyHash,
            requestFingerprint = fingerprintHash,
            definitionRevision = definitionRevision,
            status = AgentRunStatus.QUEUED,
            createdAtMillis = now,
            deadlineAtMillis = deadlineAtMillis
        )
        val inserted = dao.insertRunAndQueuedEvent(
            run.toEntity(),
            run.event(AgentRunEventType.QUEUED, "run_queued", sequence = 1L),
            maxConcurrentRuns
        )
        if (inserted) return AgentRunStartResult.Created(run)
        val winner = dao.findByIdempotency(agentId, idempotencyHash)
            ?: return AgentRunStartResult.ConcurrentRunLimit
        if (winner.requestFingerprint == fingerprintHash) return AgentRunStartResult.Existing(winner.toRun())
        return if (dao.countActiveRuns(agentId) >= maxConcurrentRuns) {
            AgentRunStartResult.ConcurrentRunLimit
        } else AgentRunStartResult.IdempotencyConflict
    }

    suspend fun find(runId: String): AgentRun? = dao.findById(runId)?.toRun()

    fun observeForAgent(agentId: String, limit: Int = MAX_RUN_HISTORY): Flow<List<AgentRun>> =
        dao.observeForAgent(agentId, limit.coerceIn(1, MAX_RUN_HISTORY)).map { rows -> rows.map { it.toRun() } }

    fun observeEvents(runId: String): Flow<List<AgentRunEvent>> = dao.observeEvents(runId).map { rows ->
        rows.map { row ->
            AgentRunEvent(row.id, row.runId, row.sequence, AgentRunEventType.valueOf(row.type), row.safeCode, row.createdAtMillis)
        }
    }

    suspend fun markRunning(runId: String): Boolean {
        val run = dao.findById(runId)?.toRun() ?: return false
        if (run.status != AgentRunStatus.QUEUED) return false
        val now = nowMillis()
        return dao.transitionAndAppendEvent(
            runId, run.status.name, AgentRunStatus.RUNNING.name, run.revision,
            run.event(AgentRunEventType.STARTED, "run_started", 0L, now), now
        )
    }

    suspend fun finish(runId: String, status: AgentRunStatus, safeOutcomeCode: String): Boolean {
        require(status.isTerminal)
        require(SAFE_CODE.matches(safeOutcomeCode))
        val run = dao.findById(runId)?.toRun() ?: return false
        val now = nowMillis()
        return dao.finishExactlyOnce(
            run = run.toEntity(),
            terminalStatus = status.name,
            outcome = safeOutcomeCode,
            event = run.event(
                AgentRunEventType.TERMINAL,
                safeOutcomeCode,
                sequence = 0L,
                createdAt = now
            ),
            now = now
        )
    }

    suspend fun recordUsage(
        runId: String,
        turns: Int,
        toolCalls: Int,
        outputCharacters: Int,
        costMicros: Long,
        providerId: String?,
        modelId: String?
    ): Boolean {
        require(turns >= 0 && toolCalls >= 0 && outputCharacters >= 0 && costMicros >= 0L)
        val run = dao.findById(runId) ?: return false
        if (run.status !in setOf(AgentRunStatus.RUNNING.name, AgentRunStatus.WAITING_FOR_APPROVAL.name)) return false
        return dao.updateUsage(runId, turns, toolCalls, outputCharacters, costMicros,
            providerId?.take(AgentRun.MAX_IDENTIFIER_CHARACTERS),
            modelId?.take(AgentRun.MAX_IDENTIFIER_CHARACTERS), run.revision, nowMillis()) == 1
    }

    suspend fun createApproval(approval: AgentApproval): Boolean {
        val run = dao.findById(approval.runId)?.toRun() ?: return false
        if (run.agentId != approval.agentId || run.definitionRevision != approval.definitionRevision ||
            run.status != AgentRunStatus.RUNNING) return false
        val now = nowMillis()
        if (approval.expiresAtMillis <= now) return false
        val keyedApproval = approval.copy(
            callFingerprint = hmac(awaitFingerprintKey(), approval.callFingerprint)
        )
        return dao.requestApprovalAndWait(
            run.toEntity(),
            keyedApproval.toEntity(now),
            run.event(AgentRunEventType.APPROVAL_REQUESTED, "approval_requested", 0L, now),
            now
        )
    }

    suspend fun resolveApproval(
        approvalId: String,
        decision: AgentApprovalDecision,
        currentDefinitionRevision: Long,
        currentDeviceBindingHash: String
    ): Boolean {
        val row = dao.findApproval(approvalId) ?: return false
        val run = dao.findById(row.runId)?.toRun() ?: return false
        if (run.status != AgentRunStatus.WAITING_FOR_APPROVAL ||
            currentDefinitionRevision != row.definitionRevision || run.definitionRevision != row.definitionRevision) return false
        val expectedDecision = if (row.expiresAtMillis <= nowMillis()) AgentApprovalDecision.EXPIRED else decision
        if (expectedDecision == AgentApprovalDecision.EXPIRED && decision == AgentApprovalDecision.APPROVED) return false
        val now = nowMillis()
        val event = run.event(
            AgentRunEventType.APPROVAL_RESOLVED,
            if (expectedDecision == AgentApprovalDecision.APPROVED) "approval_approved" else "approval_denied",
            0L,
            now
        )
        return dao.resolveApprovalAndAppendEvent(
            approvalId = approvalId,
            run = run.toEntity(),
            fingerprint = row.callFingerprint,
            deviceHash = currentDeviceBindingHash,
            decision = expectedDecision.name,
            event = event,
            now = now
        )
    }

    suspend fun awaitApprovalDecision(approvalId: String): AgentApprovalDecision? {
        val initial = dao.findApproval(approvalId) ?: return null
        initial.decision?.let { return runCatching { AgentApprovalDecision.valueOf(it) }.getOrNull() }
        val remaining = (initial.expiresAtMillis - nowMillis()).coerceAtLeast(1L)
        val settled = withTimeoutOrNull(remaining) {
            dao.observeApproval(approvalId).first { it?.decision != null }
        }
        settled?.decision?.let { decision ->
            return runCatching { AgentApprovalDecision.valueOf(decision) }.getOrNull()
        }
        val run = dao.findById(initial.runId)?.toRun() ?: return AgentApprovalDecision.EXPIRED
        val now = nowMillis()
        dao.expireApprovalAndResume(
            approvalId,
            run.toEntity(),
            run.event(AgentRunEventType.APPROVAL_RESOLVED, "approval_expired", 0L, now),
            now
        )
        return AgentApprovalDecision.EXPIRED
    }

    suspend fun findApproval(approvalId: String): AgentApproval? = dao.findApproval(approvalId)?.let { row ->
        AgentApproval(
            id = row.id,
            runId = row.runId,
            agentId = row.agentId,
            definitionRevision = row.definitionRevision,
            toolName = row.toolName,
            callFingerprint = row.callFingerprint,
            deviceBindingHash = row.deviceBindingHash,
            expiresAtMillis = row.expiresAtMillis,
            decision = row.decision?.let { runCatching { AgentApprovalDecision.valueOf(it) }.getOrNull() },
            resolvedAtMillis = row.resolvedAtMillis
        )
    }

    suspend fun recordToolActivity(runId: String, started: Boolean) {
        val code = if (started) "tool_started" else "tool_finished"
        require(SAFE_CODE.matches(code))
        dao.appendEventWithNextSequence(
            AgentRunEventEntity(
                runId = runId,
                sequence = 0L,
                id = idGenerator(),
                type = (if (started) AgentRunEventType.TOOL_STARTED else AgentRunEventType.TOOL_FINISHED).name,
                safeCode = code,
                createdAtMillis = nowMillis()
            )
        )
    }

    /** On process death, any in-flight call is interrupted and never replayed automatically. */
    suspend fun recoverInFlight(): Int {
        val active = dao.inFlight()
        var interrupted = 0
        for (entity in active) {
            if (finish(entity.id, AgentRunStatus.INTERRUPTED, "process_restarted")) interrupted++
        }
        return interrupted
    }

    private fun AgentRun.event(
        type: AgentRunEventType,
        code: String,
        sequence: Long,
        createdAt: Long = createdAtMillis
    ) = AgentRunEventEntity(runId = id, sequence = sequence, id = idGenerator(), type = type.name, safeCode = code, createdAtMillis = createdAt)

    private fun AgentRun.toEntity() = AgentRunEntity(
        id, agentId, idempotencyKeyHash, requestFingerprint, definitionRevision, status.name,
        createdAtMillis, updatedAtMillis, deadlineAtMillis, providerId, modelId, outcomeCode,
        turns, toolCalls, outputCharacters, costMicros, revision
    )

    private fun AgentRunEntity.toRun() = AgentRun(
        id, agentId, idempotencyKeyHash, requestFingerprint, definitionRevision,
        AgentRunStatus.valueOf(status), createdAtMillis, updatedAtMillis, deadlineAtMillis,
        providerId, modelId, outcomeCode, turns, toolCalls, outputCharacters, costMicros, revision
    )

    private fun AgentApproval.toEntity(now: Long) = AgentApprovalEntity(
        id, runId, agentId, definitionRevision, toolName, callFingerprint,
        deviceBindingHash, now, expiresAtMillis, decision?.name, resolvedAtMillis
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private suspend fun awaitFingerprintKey(): ByteArray = fingerprintKeyMutex.withLock {
        cachedFingerprintKey?.let { return@withLock it }
        val stored = secureStorage.get(FINGERPRINT_KEY_NAME)
        val key = stored?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == FINGERPRINT_KEY_BYTES }
            ?: ByteArray(FINGERPRINT_KEY_BYTES).also { generated ->
                SecureRandom().nextBytes(generated)
                secureStorage.put(FINGERPRINT_KEY_NAME, Base64.getEncoder().encodeToString(generated))
            }
        cachedFingerprintKey = key
        key
    }

    private fun hmac(key: ByteArray, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_RUN_HISTORY = 500
        private const val FINGERPRINT_KEY_NAME = "managed-agent-run-fingerprint-v1"
        private const val FINGERPRINT_KEY_BYTES = 32
        private val SAFE_CODE = Regex("[a-z][a-z0-9_]{0,63}")
    }
}
