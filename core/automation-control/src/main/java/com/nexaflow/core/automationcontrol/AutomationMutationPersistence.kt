package com.nexaflow.core.automationcontrol

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.workflow.WorkflowValidationIssue

enum class AutomationMutationKind {
    CREATE,
    UPDATE,
    ENABLE,
    DISABLE,
    DELETE
}

data class AutomationMutationCommitRequest(
    val kind: AutomationMutationKind,
    /**
     * Candidate definition. DELETE carries the pre-delete definition so audit
     * and callers can still identify the removed automation.
     */
    val automation: Automation,
    val context: AutomationMutationContext,
    /**
     * Automation.updatedAt observed before validation. Persistence rejects the
     * commit if the definition changed between preflight and the transaction.
     */
    val baseDefinitionUpdatedAt: Long? = null,
    val requestFingerprint: String,
    val occurredAt: Long
)

sealed interface AutomationPersistenceResult {
    data class Committed(
        val automationId: String,
        val revision: Long
    ) : AutomationPersistenceResult

    /** Same actor + idempotency key + same request fingerprint. */
    data class IdempotentReplay(
        val automationId: String?,
        val revision: Long?
    ) : AutomationPersistenceResult

    /** Same actor + idempotency key was previously used for a different request. */
    data object IdempotencyConflict : AutomationPersistenceResult

    data class RevisionConflict(
        val currentRevision: Long?
    ) : AutomationPersistenceResult

    data class DependencyConflict(
        val issues: List<WorkflowValidationIssue>
    ) : AutomationPersistenceResult

    data object NotFound : AutomationPersistenceResult
}

/**
 * Atomic persistence boundary. Implementations must commit the automation
 * definition, provenance metadata, audit event and idempotency record in one
 * transaction for successful mutations.
 */
fun interface AutomationMutationPersistence {
    suspend fun commit(
        request: AutomationMutationCommitRequest
    ): AutomationPersistenceResult

    /**
     * Looks up a previously committed idempotency key before a definition read.
     *
     * DELETE retries need this path because the successful first request has
     * already removed the automation row. Implementations that do not persist
     * idempotency may keep the default null result.
     */
    suspend fun resolveStoredIdempotency(
        context: AutomationMutationContext,
        kind: AutomationMutationKind,
        automationId: String?,
        requestFingerprint: String,
        occurredAt: Long
    ): AutomationPersistenceResult? = null
}
