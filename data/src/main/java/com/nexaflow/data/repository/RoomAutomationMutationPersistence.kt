package com.nexaflow.data.repository

import androidx.room.withTransaction
import com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest
import com.nexaflow.core.automationcontrol.AutomationMutationKind
import com.nexaflow.core.automationcontrol.AutomationMutationPersistence
import com.nexaflow.core.automationcontrol.AutomationPersistenceResult
import com.nexaflow.core.database.AgentAuditEntity
import com.nexaflow.core.database.AgentIdempotencyEntity
import com.nexaflow.core.database.AgentPlatformDao
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.database.AutomationApiMetadataEntity
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.workflow.AutomationDependencyValidator
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Room-backed control-plane ledger.
 *
 * Successful mutations atomically commit the automation definition, API
 * metadata, audit row and optional idempotency record. Conflict/replay audit
 * rows are also committed transactionally with any metadata synchronization.
 */
class RoomAutomationMutationPersistence(
    private val database: AppDatabase,
    private val automationDao: AutomationDao,
    private val agentPlatformDao: AgentPlatformDao,
    private val auditIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val idempotencyRetentionMs: Long = DEFAULT_IDEMPOTENCY_RETENTION_MS,
    private val auditRetentionMs: Long = DEFAULT_AUDIT_RETENTION_MS,
    private val maxAuditRows: Int = DEFAULT_MAX_AUDIT_ROWS
) : AutomationMutationPersistence {

    init {
        require(idempotencyRetentionMs > 0L) {
            "Idempotency retention must be positive"
        }
        require(auditRetentionMs > 0L) {
            "Audit retention must be positive"
        }
        require(maxAuditRows > 0) {
            "Audit row limit must be positive"
        }
    }

    override suspend fun commit(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult = database.withTransaction {
        commitOne(request)
    }

    /**
     * Commits the whole batch in one transaction: the first non-committed
     * item rolls everything back and surfaces as
     * [com.nexaflow.core.automationcontrol.AutomationBatchResult.Aborted].
     */
    override suspend fun commitBatch(
        requests: List<com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest>
    ): com.nexaflow.core.automationcontrol.AutomationBatchResult = try {
        database.withTransaction {
            val commits = ArrayList<AutomationPersistenceResult.Committed>(requests.size)
            val failures = ArrayList<com.nexaflow.core.automationcontrol.AutomationBatchItemFailure>()
            requests.forEachIndexed { index, request ->
                when (val result = commitOne(request)) {
                    is AutomationPersistenceResult.Committed -> commits += result
                    else -> failures += com.nexaflow.core.automationcontrol.AutomationBatchItemFailure(
                        index = index,
                        automationId = request.automation.id,
                        result = result
                    )
                }
            }
            if (failures.isNotEmpty()) {
                throw BatchRollback(failures)
            }
            com.nexaflow.core.automationcontrol.AutomationBatchResult.AllCommitted(commits)
        }
    } catch (rollback: BatchRollback) {
        com.nexaflow.core.automationcontrol.AutomationBatchResult.Aborted(rollback.failures)
    }

    private class BatchRollback(
        val failures: List<com.nexaflow.core.automationcontrol.AutomationBatchItemFailure>
    ) : RuntimeException()

    private suspend fun commitOne(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult {
        validateContext(request)
        pruneRetention(request.occurredAt)
        resolveIdempotency(request)?.let { return it }

        return when (request.kind) {
            AutomationMutationKind.CREATE -> commitCreate(request)
            AutomationMutationKind.UPDATE,
            AutomationMutationKind.ENABLE,
            AutomationMutationKind.DISABLE,
            AutomationMutationKind.DELETE -> commitExisting(request)
        }
    }

    override suspend fun resolveStoredIdempotency(
        context: com.nexaflow.core.automationcontrol.AutomationMutationContext,
        kind: AutomationMutationKind,
        automationId: String?,
        requestFingerprint: String,
        occurredAt: Long
    ): AutomationPersistenceResult? = database.withTransaction {
        validateMutationContext(context)
        pruneRetention(occurredAt)
        val rawKey = context.idempotencyKey ?: return@withTransaction null
        val existing = agentPlatformDao.getIdempotency(
            actorId = context.actorId,
            keyHash = sha256(rawKey)
        ) ?: return@withTransaction null

        val automationMatches = when (kind) {
            // CREATE fingerprints intentionally omit the server-generated id,
            // so a retry can be recognized before allocating another id.
            AutomationMutationKind.CREATE -> automationId == null || existing.automationId == automationId
            else -> existing.automationId == automationId
        }
        if (existing.requestFingerprint == requestFingerprint &&
            existing.operation == kind.name &&
            automationMatches
        ) {
            auditStoredIdempotencyResolution(
                context = context,
                eventType = "IDEMPOTENCY_REPLAY",
                outcome = "REPLAY",
                automationId = existing.automationId ?: automationId,
                kind = kind,
                revision = existing.resultRevision,
                occurredAt = occurredAt
            )
            AutomationPersistenceResult.IdempotentReplay(
                automationId = existing.automationId,
                revision = existing.resultRevision
            )
        } else {
            auditStoredIdempotencyResolution(
                context = context,
                eventType = "IDEMPOTENCY_CONFLICT",
                outcome = "CONFLICT",
                automationId = existing.automationId ?: automationId,
                kind = kind,
                revision = existing.resultRevision,
                occurredAt = occurredAt
            )
            AutomationPersistenceResult.IdempotencyConflict
        }
    }

    private suspend fun commitCreate(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult {
        val automationId = request.automation.id
        val existing = automationDao.getAutomationById(automationId)
        if (existing != null) {
            val currentRevision = currentRevision(existing)
            audit(
                request = request,
                eventType = "REVISION_CONFLICT",
                outcome = "CONFLICT",
                revision = currentRevision
            )
            return AutomationPersistenceResult.RevisionConflict(currentRevision)
        }

        dependencyIssuesIntroduced(request).takeIf { it.isNotEmpty() }?.let { issues ->
            audit(
                request = request,
                eventType = "DEPENDENCY_CONFLICT",
                outcome = "CONFLICT",
                revision = null
            )
            return AutomationPersistenceResult.DependencyConflict(issues)
        }

        val revision = INITIAL_REVISION
        automationDao.insertAutomation(request.automation.toEntity())
        agentPlatformDao.upsertAutomationMetadata(
            AutomationApiMetadataEntity(
                automationId = automationId,
                origin = request.context.origin.name,
                creatorActorId = request.context.actorId,
                creatorAgentId = request.context.agentId,
                lastActorId = request.context.actorId,
                lastAgentId = request.context.agentId,
                providerId = request.context.providerId,
                modelId = request.context.modelId,
                transport = request.context.transport,
                requestId = request.context.requestId,
                conversationId = request.context.conversationId,
                riskLevel = request.context.riskLevel,
                revision = revision,
                definitionUpdatedAt = request.automation.updatedAt,
                createdAt = request.occurredAt,
                updatedAt = request.occurredAt
            )
        )
        recordIdempotency(request, automationId, revision)
        audit(
            request = request,
            eventType = "TASK_CREATED",
            outcome = "COMMITTED",
            revision = revision
        )
        return AutomationPersistenceResult.Committed(
            automationId = automationId,
            revision = revision
        )
    }

    private suspend fun commitExisting(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult {
        val automationId = request.automation.id
        val currentEntity = automationDao.getAutomationById(automationId)
            ?: run {
                audit(
                    request = request,
                    eventType = "MUTATION_NOT_FOUND",
                    outcome = "NOT_FOUND",
                    revision = null
                )
                return AutomationPersistenceResult.NotFound
            }

        var metadata = agentPlatformDao.getAutomationMetadata(automationId)
            ?: legacyMetadata(currentEntity)
        var currentRevision = metadata.revision

        // First-party code may still write through AutomationRepository until
        // all mutation callers are migrated. Detect that out-of-band change and
        // advance the API revision before evaluating the agent's CAS value.
        if (metadata.definitionUpdatedAt != currentEntity.updatedAt) {
            currentRevision += 1L
            metadata = metadata.copy(
                revision = currentRevision,
                definitionUpdatedAt = currentEntity.updatedAt,
                updatedAt = request.occurredAt
            )
            agentPlatformDao.upsertAutomationMetadata(metadata)
        }

        if (request.baseDefinitionUpdatedAt != null &&
            request.baseDefinitionUpdatedAt != currentEntity.updatedAt
        ) {
            audit(
                request = request,
                eventType = "PREFLIGHT_RACE_CONFLICT",
                outcome = "CONFLICT",
                revision = currentRevision
            )
            return AutomationPersistenceResult.RevisionConflict(currentRevision)
        }

        val expectedRevision = request.context.expectedRevision
        if (expectedRevision != null && expectedRevision != currentRevision) {
            audit(
                request = request,
                eventType = "REVISION_CONFLICT",
                outcome = "CONFLICT",
                revision = currentRevision
            )
            return AutomationPersistenceResult.RevisionConflict(currentRevision)
        }

        dependencyIssuesIntroduced(request).takeIf { it.isNotEmpty() }?.let { issues ->
            audit(
                request = request,
                eventType = "DEPENDENCY_CONFLICT",
                outcome = "CONFLICT",
                revision = currentRevision
            )
            return AutomationPersistenceResult.DependencyConflict(issues)
        }

        val newRevision = currentRevision + 1L
        if (request.kind == AutomationMutationKind.DELETE) {
            automationDao.deleteAutomation(currentEntity)
            agentPlatformDao.deleteAutomationMetadata(automationId)
        } else {
            automationDao.insertAutomation(request.automation.toEntity())
            agentPlatformDao.upsertAutomationMetadata(
                metadata.copy(
                    lastActorId = request.context.actorId,
                    lastAgentId = request.context.agentId,
                    providerId = request.context.providerId,
                    modelId = request.context.modelId,
                    transport = request.context.transport,
                    requestId = request.context.requestId,
                    conversationId = request.context.conversationId,
                    riskLevel = request.context.riskLevel,
                    revision = newRevision,
                    definitionUpdatedAt = request.automation.updatedAt,
                    updatedAt = request.occurredAt
                )
            )
        }

        recordIdempotency(request, automationId, newRevision)
        audit(
            request = request,
            eventType = eventType(request.kind),
            outcome = "COMMITTED",
            revision = newRevision
        )
        return AutomationPersistenceResult.Committed(
            automationId = automationId,
            revision = newRevision
        )
    }

    private suspend fun dependencyIssuesIntroduced(
        request: AutomationMutationCommitRequest
    ): List<WorkflowValidationIssue> {
        val current = automationDao.getAllAutomationsSnapshot().map { it.toDomain() }
        val prospective = when (request.kind) {
            AutomationMutationKind.CREATE -> current + request.automation
            AutomationMutationKind.UPDATE,
            AutomationMutationKind.ENABLE,
            AutomationMutationKind.DISABLE ->
                current.filterNot { it.id == request.automation.id } + request.automation
            AutomationMutationKind.DELETE ->
                current.filterNot { it.id == request.automation.id }
        }

        val baselineIssues = dependencyIssues(current).toSet()
        return dependencyIssues(prospective).filterNot { it in baselineIssues }
    }

    private fun dependencyIssues(
        automations: List<Automation>
    ): List<WorkflowValidationIssue> {
        val validation = AutomationDependencyValidator.validate(automations)
        return automations.flatMap { validation.issuesFor(it.id) }
    }

    private suspend fun resolveIdempotency(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult? {
        val rawKey = request.context.idempotencyKey ?: return null
        val keyHash = sha256(rawKey)
        val existing = agentPlatformDao.getIdempotency(
            actorId = request.context.actorId,
            keyHash = keyHash
        ) ?: return null

        return if (existing.requestFingerprint == request.requestFingerprint) {
            audit(
                request = request,
                eventType = "IDEMPOTENCY_REPLAY",
                outcome = "REPLAY",
                revision = existing.resultRevision
            )
            AutomationPersistenceResult.IdempotentReplay(
                automationId = existing.automationId,
                revision = existing.resultRevision
            )
        } else {
            audit(
                request = request,
                eventType = "IDEMPOTENCY_CONFLICT",
                outcome = "CONFLICT",
                revision = existing.resultRevision
            )
            AutomationPersistenceResult.IdempotencyConflict
        }
    }

    private suspend fun recordIdempotency(
        request: AutomationMutationCommitRequest,
        automationId: String?,
        revision: Long?
    ) {
        val rawKey = request.context.idempotencyKey ?: return
        agentPlatformDao.insertIdempotency(
            AgentIdempotencyEntity(
                actorId = request.context.actorId,
                keyHash = sha256(rawKey),
                requestFingerprint = request.requestFingerprint,
                operation = request.kind.name,
                automationId = automationId,
                resultRevision = revision,
                createdAt = request.occurredAt,
                expiresAt = request.occurredAt + idempotencyRetentionMs
            )
        )
    }

    private suspend fun pruneRetention(now: Long) {
        agentPlatformDao.pruneExpiredIdempotency(now)
        val cutoff = (now - auditRetentionMs).coerceAtLeast(0L)
        agentPlatformDao.pruneAuditBefore(cutoff)
        agentPlatformDao.pruneAuditToNewest(maxAuditRows)
    }

    private suspend fun currentRevision(entity: AutomationEntity): Long {
        val metadata = agentPlatformDao.getAutomationMetadata(entity.id)
            ?: return INITIAL_REVISION
        return if (metadata.definitionUpdatedAt == entity.updatedAt) {
            metadata.revision
        } else {
            metadata.revision + 1L
        }
    }

    private fun legacyMetadata(entity: AutomationEntity): AutomationApiMetadataEntity =
        AutomationApiMetadataEntity(
            automationId = entity.id,
            origin = "HUMAN",
            creatorActorId = "legacy",
            creatorAgentId = null,
            lastActorId = "legacy",
            lastAgentId = null,
            providerId = null,
            modelId = null,
            transport = "INTERNAL",
            requestId = null,
            conversationId = null,
            riskLevel = null,
            revision = INITIAL_REVISION,
            definitionUpdatedAt = entity.updatedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    private suspend fun auditStoredIdempotencyResolution(
        context: com.nexaflow.core.automationcontrol.AutomationMutationContext,
        eventType: String,
        outcome: String,
        automationId: String?,
        kind: AutomationMutationKind,
        revision: Long?,
        occurredAt: Long
    ) {
        val details = buildJsonObject {
            put("operation", kind.name)
            if (revision != null) put("revision", revision)
        }.toString()
        require(details.length <= MAX_AUDIT_DETAILS_LENGTH)
        agentPlatformDao.insertAudit(
            AgentAuditEntity(
                id = auditIdGenerator(),
                eventType = eventType,
                outcome = outcome,
                actorId = context.actorId,
                agentId = context.agentId,
                automationId = automationId,
                requestId = context.requestId,
                transport = context.transport,
                detailsJson = details,
                createdAt = occurredAt
            )
        )
        agentPlatformDao.pruneAuditToNewest(maxAuditRows)
    }

    private suspend fun audit(
        request: AutomationMutationCommitRequest,
        eventType: String,
        outcome: String,
        revision: Long?
    ) {
        val details = buildJsonObject {
            put("operation", request.kind.name)
            if (revision != null) put("revision", revision)
        }.toString()
        require(details.length <= MAX_AUDIT_DETAILS_LENGTH)

        agentPlatformDao.insertAudit(
            AgentAuditEntity(
                id = auditIdGenerator(),
                eventType = eventType,
                outcome = outcome,
                actorId = request.context.actorId,
                agentId = request.context.agentId,
                automationId = request.automation.id,
                requestId = request.context.requestId,
                transport = request.context.transport,
                detailsJson = details,
                createdAt = request.occurredAt
            )
        )
        agentPlatformDao.pruneAuditToNewest(maxAuditRows)
    }

    private fun validateContext(request: AutomationMutationCommitRequest) {
        validateMutationContext(request.context)
        require(request.requestFingerprint.isNotBlank()) {
            "Mutation request fingerprint must not be blank"
        }
    }

    private fun validateMutationContext(
        context: com.nexaflow.core.automationcontrol.AutomationMutationContext
    ) {
        require(context.actorId.matches(ACTOR_ID_PATTERN)) {
            "Mutation actor id has an invalid format"
        }
        require(context.transport.length <= MAX_METADATA_VALUE_LENGTH) {
            "Mutation transport metadata is too long"
        }
        requireOptionalBound(context.agentId)
        requireOptionalBound(context.providerId)
        requireOptionalBound(context.modelId)
        requireOptionalBound(context.requestId)
        requireOptionalBound(context.conversationId)
        requireOptionalBound(context.riskLevel)
        context.idempotencyKey?.let { rawKey ->
            require(rawKey.isNotBlank() && rawKey.length <= MAX_IDEMPOTENCY_KEY_LENGTH) {
                "Idempotency key has an invalid length"
            }
        }
    }

    private fun requireOptionalBound(value: String?) {
        require(value == null || value.length <= MAX_METADATA_VALUE_LENGTH)
    }

    private fun eventType(kind: AutomationMutationKind): String = when (kind) {
        AutomationMutationKind.CREATE -> "TASK_CREATED"
        AutomationMutationKind.UPDATE -> "TASK_UPDATED"
        AutomationMutationKind.ENABLE -> "TASK_ENABLED"
        AutomationMutationKind.DISABLE -> "TASK_DISABLED"
        AutomationMutationKind.DELETE -> "TASK_DELETED"
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val INITIAL_REVISION = 1L
        const val DEFAULT_IDEMPOTENCY_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
        const val DEFAULT_AUDIT_RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_AUDIT_ROWS = 10_000
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 256
        const val MAX_METADATA_VALUE_LENGTH = 256
        const val MAX_AUDIT_DETAILS_LENGTH = 2_048
        val ACTOR_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
