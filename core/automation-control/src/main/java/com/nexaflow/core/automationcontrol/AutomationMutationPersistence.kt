package com.nexaflow.core.automationcontrol

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import java.security.MessageDigest
import java.util.Base64

/** Content identity shared by pending approval, persisted metadata, and execution checks. */
object AgentAutomationApprovalHash {
    fun of(automation: Automation): String {
        val canonical = buildString {
            append(automation.id).append('|').append(automation.name).append('|')
            append(automation.enabled).append('|').append(automation.triggerMatch.name).append('|')
            append(automation.revertOnExit).append('|').append(automation.cooldownSeconds)
            automation.triggers.forEach { node ->
                append("|trigger:").append(node.type.name).append(':').append(node.config.toSortedMap())
            }
            (automation.actions + automation.exitActions).forEach { node ->
                append("|action:").append(node.type.name).append(':').append(node.config.toSortedMap())
                node.endBehavior?.let { append(':').append(it.mode.name).append(':').append(it.config.toSortedMap()) }
            }
            automation.constraints.forEach { constraint ->
                append('|').append(constraint.type.name).append(':').append(constraint.config.toSortedMap())
            }
            automation.canonicalNodes.forEach { append('|').append(it.toString()) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

fun interface AgentAutomationContentHasher {
    fun hash(automation: Automation): String
}

fun interface AgentAutomationRiskGate {
    fun requiresApproval(automation: Automation): Boolean
}

enum class AutomationMutationKind {
    CREATE,
    UPDATE,
    ENABLE,
    DISABLE,
    DELETE
}

/** Stable, content-bound approval identity shared by mutation and execution boundaries. */
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

data class AutomationBatchItemFailure(
    val index: Int,
    val automationId: String,
    val result: AutomationPersistenceResult
)

sealed interface AutomationBatchResult {
    data class AllCommitted(
        val commits: List<AutomationPersistenceResult.Committed>
    ) : AutomationBatchResult

    /**
     * At least one item could not commit. Transactional implementations roll
     * the whole batch back; [failures] carries every non-committed item.
     */
    data class Aborted(
        val failures: List<AutomationBatchItemFailure>
    ) : AutomationBatchResult
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
     * Persists an import batch. The default implementation commits
     * sequentially and stops at the first failure (earlier items stay
     * committed); transactional implementations override this to commit
     * atomically and roll everything back on [AutomationBatchResult.Aborted].
     */
    suspend fun commitBatch(
        requests: List<AutomationMutationCommitRequest>
    ): AutomationBatchResult {
        val commits = ArrayList<AutomationPersistenceResult.Committed>(requests.size)
        requests.forEachIndexed { index, request ->
            when (val result = commit(request)) {
                is AutomationPersistenceResult.Committed -> commits += result
                else -> return AutomationBatchResult.Aborted(
                    listOf(
                        AutomationBatchItemFailure(
                            index = index,
                            automationId = request.automation.id,
                            result = result
                        )
                    )
                )
            }
        }
        return AutomationBatchResult.AllCommitted(commits)
    }

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
