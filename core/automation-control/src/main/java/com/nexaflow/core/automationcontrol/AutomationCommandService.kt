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
import com.nexaflow.domain.workflow.WorkflowValidator
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

data class AutomationImportRejection(
    val automationId: String,
    val issues: List<WorkflowValidationIssue>
)

sealed interface AutomationImportResult {
    data class Success(
        val automations: List<Automation>
    ) : AutomationImportResult

    data class Rejected(
        val failures: List<AutomationImportRejection>
    ) : AutomationImportResult

    data class IdempotencyConflict(
        val automationId: String
    ) : AutomationImportResult
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
    private val mutationObserver: AutomationMutationObserver = AutomationMutationObserver.NO_OP,
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
        val occurredAt = clockMillis()
        val fingerprint = AutomationMutationFingerprint.draft(
            AutomationMutationKind.CREATE,
            automationId = null,
            draft = draft
        )
        resolveStoredReplay(
            context = context,
            kind = AutomationMutationKind.CREATE,
            automationId = null,
            fingerprint = fingerprint,
            occurredAt = occurredAt
        )?.let { return it }

        val id = generateUniqueId()
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
                requestFingerprint = fingerprint,
                occurredAt = occurredAt
            )
        )
    }

    suspend fun update(
        automationId: String,
        draft: AgentTaskDraftV1,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val occurredAt = clockMillis()
        val fingerprint = AutomationMutationFingerprint.draft(
            AutomationMutationKind.UPDATE,
            automationId = automationId,
            draft = draft
        )
        resolveStoredReplay(
            context = context,
            kind = AutomationMutationKind.UPDATE,
            automationId = automationId,
            fingerprint = fingerprint,
            occurredAt = occurredAt
        )?.let { return it }

        val existing = repository.getAutomationById(automationId)
            ?: return notFound(automationId, context)
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
                requestFingerprint = fingerprint,
                occurredAt = occurredAt
            )
        )
    }

    suspend fun setEnabled(
        automationId: String,
        enabled: Boolean,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val occurredAt = clockMillis()
        val kind = if (enabled) {
            AutomationMutationKind.ENABLE
        } else {
            AutomationMutationKind.DISABLE
        }
        val fingerprint = AutomationMutationFingerprint.state(
            kind = kind,
            automationId = automationId,
            enabled = enabled
        )
        resolveStoredReplay(
            context = context,
            kind = kind,
            automationId = automationId,
            fingerprint = fingerprint,
            occurredAt = occurredAt
        )?.let { return it }

        val existing = repository.getAutomationById(automationId)
            ?: return notFound(automationId, context)
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

        return persist(
            automation = candidate,
            report = report,
            request = AutomationMutationCommitRequest(
                kind = kind,
                automation = candidate,
                context = context,
                baseDefinitionUpdatedAt = existing.updatedAt,
                requestFingerprint = fingerprint,
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
        val occurredAt = clockMillis()
        resolveStoredReplay(
            context = context,
            kind = AutomationMutationKind.DELETE,
            automationId = automationId,
            fingerprint = deleteFingerprint,
            occurredAt = occurredAt
        )?.let { return it }

        val existing = repository.getAutomationById(automationId)
            ?: return notFound(automationId, context)

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
                occurredAt = occurredAt
            )
        )
    }

    /**
     * Bulk import boundary: validates every definition before persisting
     * anything, then commits the whole batch atomically.
     *
     * Validation scope intentionally mirrors the backup restore contract:
     * structural workflow rules plus dependency soundness against the
     * prospective graph (current catalog plus the batch itself, so intra-file
     * dependency edges validate). Node-config schemas and dry-run
     * executability are NOT gates here — imports persist disabled for human
     * review, and those layers re-evaluate at review/enable time. The caller
     * must supply policy-processed definitions (re-keyed ids, stripped
     * tokens, disabled) with an [AutomationMutationOrigin.IMPORT] context
     * carrying a batch idempotency key.
     */
    suspend fun importAll(
        automations: List<Automation>,
        context: AutomationMutationContext
    ): AutomationImportResult {
        require(context.origin == AutomationMutationOrigin.IMPORT) {
            "Bulk import requires an IMPORT mutation context"
        }
        val batchKey = context.idempotencyKey
        require(!batchKey.isNullOrBlank()) {
            "Bulk import requires an idempotency key"
        }
        if (automations.isEmpty()) return AutomationImportResult.Success(emptyList())

        val duplicateIds = automations.groupingBy { it.id }.eachCount()
            .filterValues { count -> count > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            return AutomationImportResult.Rejected(
                duplicateIds.map { AutomationImportRejection(it, emptyList()) }
            )
        }

        val occurredAt = clockMillis()
        val current = repository.getAutomations().first()

        // Idempotency first: a retried batch resolves already-committed rows
        // before the collision guard below, so legitimate retries replay
        // instead of looking like clashes with their own first attempt.
        val replayed = ArrayList<Automation>()
        val fresh = ArrayList<Automation>()
        automations.forEach { automation ->
            val draft = AgentTaskMapper.fromAutomation(automation)
            val fingerprint = AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                automationId = automation.id,
                draft = draft
            )
            val itemContext = context.copy(idempotencyKey = "$batchKey#${automation.id}")
            when (
                resolveStoredReplay(
                    context = itemContext,
                    kind = AutomationMutationKind.CREATE,
                    automationId = automation.id,
                    fingerprint = fingerprint,
                    occurredAt = occurredAt
                )
            ) {
                is AutomationMutationResult.IdempotentReplay -> {
                    val stored = repository.getAutomationById(automation.id)
                    if (stored != null) {
                        replayed += stored
                        return@forEach
                    }
                    fresh += automation
                }
                AutomationMutationResult.IdempotencyConflict ->
                    return AutomationImportResult.IdempotencyConflict(automation.id)
                else -> fresh += automation
            }
        }
        if (fresh.isEmpty()) return AutomationImportResult.Success(replayed)

        val currentIds = current.mapTo(HashSet()) { it.id }
        val clashingIds = fresh.map { it.id }.filter { it in currentIds }.toSet()
        if (clashingIds.isNotEmpty()) {
            // The importer owns ID-collision re-keying; reaching the service
            // with a collision would silently replace a local definition.
            return AutomationImportResult.Rejected(
                clashingIds.map { AutomationImportRejection(it, emptyList()) }
            )
        }

        val prospective = current + fresh
        val dependencyValidation = AutomationDependencyValidator.validate(prospective)
        val failures = ArrayList<AutomationImportRejection>()
        val prepared = ArrayList<Triple<Automation, AutomationMutationCommitRequest, AutomationMutationContext>>()
        fresh.forEach { automation ->
            val draft = AgentTaskMapper.fromAutomation(automation)
            val fingerprint = AutomationMutationFingerprint.draft(
                AutomationMutationKind.CREATE,
                automationId = automation.id,
                draft = draft
            )
            val itemContext = context.copy(idempotencyKey = "$batchKey#${automation.id}")

            val issues = WorkflowValidator.validate(automation).issues +
                dependencyValidation.issuesFor(automation.id)
            if (issues.isNotEmpty()) {
                failures += AutomationImportRejection(automation.id, issues)
                return@forEach
            }
            prepared += Triple(
                automation,
                AutomationMutationCommitRequest(
                    kind = AutomationMutationKind.CREATE,
                    automation = automation,
                    context = itemContext,
                    requestFingerprint = fingerprint,
                    occurredAt = occurredAt
                ),
                itemContext
            )
        }
        if (failures.isNotEmpty()) return AutomationImportResult.Rejected(failures)

        val ordered = orderByDependencies(prepared)
        return when (
            val batch = mutationPersistence.commitBatch(ordered.map { it.second })
        ) {
            is AutomationBatchResult.AllCommitted -> {
                ordered.forEachIndexed { index, (automation, _, itemContext) ->
                    notifyCommitted(
                        AutomationMutationKind.CREATE,
                        automation,
                        batch.commits[index].revision,
                        itemContext
                    )
                }
                AutomationImportResult.Success(ordered.map { it.first } + replayed)
            }
            is AutomationBatchResult.Aborted -> AutomationImportResult.Rejected(
                batch.failures.map { AutomationImportRejection(it.automationId, emptyList()) }
            )
        }
    }

    /**
     * Orders batch items so intra-batch dependencies commit first; the
     * persistence layer validates each item against the running snapshot.
     */
    private fun orderByDependencies(
        prepared: List<Triple<Automation, AutomationMutationCommitRequest, AutomationMutationContext>>
    ): List<Triple<Automation, AutomationMutationCommitRequest, AutomationMutationContext>> {
        val byId = prepared.associateBy { it.first.id }
        val ordered = ArrayList<Triple<Automation, AutomationMutationCommitRequest, AutomationMutationContext>>(prepared.size)
        val visited = HashSet<String>()
        fun visit(id: String) {
            val item = byId[id] ?: return
            if (!visited.add(id)) return
            item.first.maintenanceProfile?.dependencyAutomationIds?.forEach(::visit)
            ordered += item
        }
        prepared.forEach { visit(it.first.id) }
        return ordered
    }

    private suspend fun resolveStoredReplay(
        context: AutomationMutationContext,
        kind: AutomationMutationKind,
        automationId: String?,
        fingerprint: String,
        occurredAt: Long
    ): AutomationMutationResult? = when (
        val stored = mutationPersistence.resolveStoredIdempotency(
            context = context,
            kind = kind,
            automationId = automationId,
            requestFingerprint = fingerprint,
            occurredAt = occurredAt
        )
    ) {
        is AutomationPersistenceResult.IdempotentReplay ->
            AutomationMutationResult.IdempotentReplay(
                automationId = stored.automationId,
                revision = stored.revision
            )
        AutomationPersistenceResult.IdempotencyConflict ->
            AutomationMutationResult.IdempotencyConflict
        else -> null
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
    ): AutomationMutationResult {
        val result = mutationPersistence.commit(request)
        return toMutationResult(result, automation, report, request)
    }

    private suspend fun toMutationResult(
        result: AutomationPersistenceResult,
        automation: Automation,
        report: AutomationPreflightReport,
        request: AutomationMutationCommitRequest
    ): AutomationMutationResult = when (result) {
        is AutomationPersistenceResult.Committed -> {
            notifyCommitted(request.kind, automation, result.revision, request.context)
            AutomationMutationResult.Success(
                automation = automation,
                revision = result.revision,
                dryRun = report.dryRun
            )
        }

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

    private suspend fun notifyCommitted(
        kind: AutomationMutationKind,
        automation: Automation,
        revision: Long,
        context: AutomationMutationContext
    ) {
        try {
            mutationObserver.onCommitted(kind, automation, revision, context)
        } catch (_: Exception) {
            // Observer fan-out must never fail an already-committed mutation.
        }
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
