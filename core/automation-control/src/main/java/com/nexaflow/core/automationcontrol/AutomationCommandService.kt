package com.nexaflow.core.automationcontrol

import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskMapper
import com.nexaflow.core.automationcontrol.api.AgentTaskMappingError
import com.nexaflow.core.automationcontrol.api.AgentTaskMappingException
import com.nexaflow.core.automationcontrol.validation.AgentWorkflowValidationReport
import com.nexaflow.core.automationcontrol.validation.AgentWorkflowValidator
import com.nexaflow.core.execution.dryrun.WorkflowDryRunInput
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.core.execution.dryrun.WorkflowDryRunService
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.AutomationDependencyValidator
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.flow.first

enum class AutomationMutationOrigin {
    HUMAN,
    AGENT,
    IMPORT,
    PLUGIN,
    INTERNAL
}

data class AutomationMutationContext(
    val actorId: String,
    val origin: AutomationMutationOrigin,
    /** API metadata revision, not a wall-clock timestamp. */
    val expectedRevision: Long? = null,
    /**
     * Agent/API callers normally require a runnable dry-run. Human/import flows
     * may opt out so an intentionally disabled draft can still be persisted.
     */
    val requireExecutable: Boolean = true,
    val agentId: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val transport: String = "INTERNAL",
    val requestId: String? = null,
    val conversationId: String? = null,
    /**
     * Opaque caller key. Implementations persist only a hash and scope it to
     * actorId. Null means the caller explicitly opted out of idempotency.
     */
    val idempotencyKey: String? = null,
    val riskLevel: String? = null
)

fun interface AutomationDryRunInspector {
    suspend fun inspect(input: WorkflowDryRunInput): WorkflowDryRunReport
}

class WorkflowDryRunInspector(
    private val service: WorkflowDryRunService
) : AutomationDryRunInspector {
    override suspend fun inspect(input: WorkflowDryRunInput): WorkflowDryRunReport =
        service.inspect(input)
}

data class AutomationPreflightReport(
    val mappingError: AgentTaskMappingError? = null,
    val validation: AgentWorkflowValidationReport? = null,
    val dryRun: WorkflowDryRunReport? = null
) {
    val isStructurallyValid: Boolean
        get() = mappingError == null && validation?.isValid == true

    val executable: Boolean
        get() = isStructurallyValid && dryRun?.executable == true
}

sealed interface AutomationMutationResult {
    data class Success(
        val automation: Automation,
        val revision: Long,
        val dryRun: WorkflowDryRunReport?
    ) : AutomationMutationResult

    data class IdempotentReplay(
        val automationId: String?,
        val revision: Long?
    ) : AutomationMutationResult

    data object IdempotencyConflict : AutomationMutationResult

    data class Rejected(
        val report: AutomationPreflightReport
    ) : AutomationMutationResult

    data class Conflict(
        val automationId: String,
        val expectedRevision: Long?,
        val currentRevision: Long?
    ) : AutomationMutationResult

    data class DeleteBlocked(
        val automationId: String,
        val dependencyIssues: List<WorkflowValidationIssue>
    ) : AutomationMutationResult

    data class DependencyConflict(
        val automationId: String,
        val dependencyIssues: List<WorkflowValidationIssue>
    ) : AutomationMutationResult

    data class NotFound(
        val automationId: String
    ) : AutomationMutationResult
}

/**
 * Single mutation boundary for future UI, MCP, REST, A2A and local-agent paths.
 *
 * Validation/dry-run remain side-effect free. Successful writes are delegated
 * to AutomationMutationPersistence, whose Room implementation atomically
 * commits the definition, metadata, audit event and idempotency record.
 */
class AutomationCommandService(
    private val repository: AutomationRepository,
    private val dryRunInspector: AutomationDryRunInspector,
    private val mutationPersistence: AutomationMutationPersistence,
    private val auditSink: AutomationAuditSink = AutomationAuditSink.NO_OP,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {

    suspend fun validateDraft(
        draft: AgentTaskDraftV1,
        existingId: String? = null
    ): AutomationPreflightReport {
        val existing = existingId?.let { repository.getAutomationById(it) }
        val id = existing?.id ?: existingId ?: idGenerator()
        return prepare(
            draft = draft,
            id = id,
            existing = existing,
            definitionUpdatedAt = nextDefinitionUpdatedAt(existing)
        ).report
    }

    suspend fun create(
        draft: AgentTaskDraftV1,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val id = generateUniqueId()
        val occurredAt = clockMillis()
        val prepared = prepare(
            draft = draft,
            id = id,
            existing = null,
            definitionUpdatedAt = occurredAt
        )
        val automation = prepared.automation
        if (automation == null || !prepared.report.canPersist(context)) {
            return rejected(
                report = prepared.report,
                context = context,
                automationId = automation?.id
            )
        }

        return persist(
            automation = automation,
            report = prepared.report,
            request = AutomationMutationCommitRequest(
                kind = AutomationMutationKind.CREATE,
                automation = automation,
                context = context,
                requestFingerprint = AutomationMutationFingerprint.draft(
                    AutomationMutationKind.CREATE,
                    automationId = null,
                    draft = draft
                ),
                occurredAt = occurredAt
            )
        )
    }

    suspend fun update(
        automationId: String,
        draft: AgentTaskDraftV1,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val existing = repository.getAutomationById(automationId)
            ?: return notFound(automationId, context)
        val occurredAt = clockMillis()
        val prepared = prepare(
            draft = draft,
            id = automationId,
            existing = existing,
            definitionUpdatedAt = max(occurredAt, existing.updatedAt + 1L)
        )
        val automation = prepared.automation
        if (automation == null || !prepared.report.canPersist(context)) {
            return rejected(
                report = prepared.report,
                context = context,
                automationId = automationId
            )
        }

        return persist(
            automation = automation,
            report = prepared.report,
            request = AutomationMutationCommitRequest(
                kind = AutomationMutationKind.UPDATE,
                automation = automation,
                context = context,
                baseDefinitionUpdatedAt = existing.updatedAt,
                requestFingerprint = AutomationMutationFingerprint.draft(
                    AutomationMutationKind.UPDATE,
                    automationId = automationId,
                    draft = draft
                ),
                occurredAt = occurredAt
            )
        )
    }

    suspend fun setEnabled(
        automationId: String,
        enabled: Boolean,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val existing = repository.getAutomationById(automationId)
            ?: return notFound(automationId, context)
        val occurredAt = clockMillis()
        val candidate = existing.copy(
            enabled = enabled,
            updatedAt = max(occurredAt, existing.updatedAt + 1L)
        )
        val catalog = repository.getAutomations().first()
        val validation = AgentWorkflowValidator.validate(candidate, catalog)
        val dryRun = if (validation.isValid && enabled) {
            dryRunInspector.inspect(
                WorkflowDryRunInput(
                    automation = candidate,
                    automationCatalog = catalog
                )
            )
        } else {
            null
        }
        val report = AutomationPreflightReport(
            validation = validation,
            dryRun = dryRun
        )

        if (!validation.isValid ||
            (enabled && context.requireExecutable && dryRun?.executable != true)
        ) {
            return rejected(
                report = report,
                context = context,
                automationId = automationId
            )
        }

        val kind = if (enabled) {
            AutomationMutationKind.ENABLE
        } else {
            AutomationMutationKind.DISABLE
        }
        return persist(
            automation = candidate,
            report = report,
            request = AutomationMutationCommitRequest(
                kind = kind,
                automation = candidate,
                context = context,
                baseDefinitionUpdatedAt = existing.updatedAt,
                requestFingerprint = AutomationMutationFingerprint.state(
                    kind = kind,
                    automationId = automationId,
                    enabled = enabled
                ),
                occurredAt = occurredAt
            )
        )
    }

    suspend fun delete(
        automationId: String,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val deleteFingerprint = AutomationMutationFingerprint.state(
            kind = AutomationMutationKind.DELETE,
            automationId = automationId
        )
        val existing = repository.getAutomationById(automationId)
            ?: return when (
                val replay = mutationPersistence.resolveStoredIdempotency(
                    context = context,
                    kind = AutomationMutationKind.DELETE,
                    automationId = automationId,
                    requestFingerprint = deleteFingerprint,
                    occurredAt = clockMillis()
                )
            ) {
                is AutomationPersistenceResult.IdempotentReplay ->
                    AutomationMutationResult.IdempotentReplay(
                        automationId = replay.automationId,
                        revision = replay.revision
                    )
                AutomationPersistenceResult.IdempotencyConflict ->
                    AutomationMutationResult.IdempotencyConflict
                else -> notFound(automationId, context)
            }

        val remaining = repository.getAutomations().first()
            .filterNot { it.id == automationId }
        val dependencyValidation = AutomationDependencyValidator.validate(remaining)
        val dependencyIssues = remaining.flatMap { dependencyValidation.issuesFor(it.id) }
        if (dependencyIssues.isNotEmpty()) {
            auditSink.record(
                AutomationAuditEvent(
                    eventType = "TASK_DELETE_BLOCKED",
                    outcome = "REJECTED",
                    actorId = context.actorId,
                    agentId = context.agentId,
                    automationId = automationId,
                    requestId = context.requestId,
                    transport = context.transport,
                    details = mapOf(
                        "dependencyIssueCount" to dependencyIssues.size.toString()
                    ),
                    createdAt = clockMillis()
                )
            )
            return AutomationMutationResult.DeleteBlocked(
                automationId = automationId,
                dependencyIssues = dependencyIssues
            )
        }

        return persist(
            automation = existing,
            report = AutomationPreflightReport(),
            request = AutomationMutationCommitRequest(
                kind = AutomationMutationKind.DELETE,
                automation = existing,
                context = context,
                baseDefinitionUpdatedAt = existing.updatedAt,
                requestFingerprint = deleteFingerprint,
                occurredAt = clockMillis()
            )
        )
    }

    private suspend fun rejected(
        report: AutomationPreflightReport,
        context: AutomationMutationContext,
        automationId: String?
    ): AutomationMutationResult.Rejected {
        auditSink.record(
            AutomationAuditEvent(
                eventType = "MUTATION_REJECTED",
                outcome = "REJECTED",
                actorId = context.actorId,
                agentId = context.agentId,
                automationId = automationId,
                requestId = context.requestId,
                transport = context.transport,
                details = buildMap {
                    report.mappingError?.let { put("mappingError", it.code) }
                    report.validation?.let {
                        put("workflowIssueCount", it.workflowIssues.size.toString())
                        put("configIssueCount", it.configIssues.size.toString())
                    }
                    put("executable", report.executable.toString())
                },
                createdAt = clockMillis()
            )
        )
        return AutomationMutationResult.Rejected(report)
    }

    private suspend fun notFound(
        automationId: String,
        context: AutomationMutationContext
    ): AutomationMutationResult.NotFound {
        auditSink.record(
            AutomationAuditEvent(
                eventType = "MUTATION_NOT_FOUND",
                outcome = "NOT_FOUND",
                actorId = context.actorId,
                agentId = context.agentId,
                automationId = automationId,
                requestId = context.requestId,
                transport = context.transport,
                createdAt = clockMillis()
            )
        )
        return AutomationMutationResult.NotFound(automationId)
    }

    private suspend fun prepare(
        draft: AgentTaskDraftV1,
        id: String,
        existing: Automation?,
        definitionUpdatedAt: Long
    ): PreparedMutation {
        val automation = try {
            AgentTaskMapper.toAutomation(
                draft = draft,
                id = id,
                nowMillis = definitionUpdatedAt,
                existing = existing
            )
        } catch (exception: AgentTaskMappingException) {
            return PreparedMutation(
                automation = null,
                report = AutomationPreflightReport(mappingError = exception.error)
            )
        }

        val catalog = repository.getAutomations().first()
        val validation = AgentWorkflowValidator.validate(automation, catalog)
        val dryRun = if (validation.isValid) {
            dryRunInspector.inspect(
                WorkflowDryRunInput(
                    automation = automation,
                    automationCatalog = catalog
                )
            )
        } else {
            null
        }

        return PreparedMutation(
            automation = automation,
            report = AutomationPreflightReport(
                validation = validation,
                dryRun = dryRun
            )
        )
    }

    private suspend fun persist(
        automation: Automation,
        report: AutomationPreflightReport,
        request: AutomationMutationCommitRequest
    ): AutomationMutationResult = when (val result = mutationPersistence.commit(request)) {
        is AutomationPersistenceResult.Committed -> AutomationMutationResult.Success(
            automation = automation,
            revision = result.revision,
            dryRun = report.dryRun
        )

        is AutomationPersistenceResult.IdempotentReplay ->
            AutomationMutationResult.IdempotentReplay(
                automationId = result.automationId,
                revision = result.revision
            )

        AutomationPersistenceResult.IdempotencyConflict ->
            AutomationMutationResult.IdempotencyConflict

        is AutomationPersistenceResult.RevisionConflict ->
            AutomationMutationResult.Conflict(
                automationId = automation.id,
                expectedRevision = request.context.expectedRevision,
                currentRevision = result.currentRevision
            )

        is AutomationPersistenceResult.DependencyConflict ->
            AutomationMutationResult.DependencyConflict(
                automationId = automation.id,
                dependencyIssues = result.issues
            )

        AutomationPersistenceResult.NotFound ->
            AutomationMutationResult.NotFound(automation.id)
    }

    private fun AutomationPreflightReport.canPersist(
        context: AutomationMutationContext
    ): Boolean {
        if (!isStructurallyValid) return false
        return !context.requireExecutable || dryRun?.executable == true
    }

    private suspend fun generateUniqueId(): String {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = idGenerator()
            if (candidate.isNotBlank() && repository.getAutomationById(candidate) == null) {
                return candidate
            }
        }
        error("Unable to allocate a unique automation id")
    }

    private fun nextDefinitionUpdatedAt(existing: Automation?): Long {
        val now = clockMillis()
        return existing?.let { max(now, it.updatedAt + 1L) } ?: now
    }

    private data class PreparedMutation(
        val automation: Automation?,
        val report: AutomationPreflightReport
    )

    private companion object {
        const val MAX_ID_ATTEMPTS = 8
    }
}
