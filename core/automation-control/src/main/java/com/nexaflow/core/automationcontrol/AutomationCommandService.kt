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
    /**
     * Foundation revision backed by Automation.updatedAt. The dedicated API
     * metadata store will later provide transactional compare-and-set semantics.
     */
    val expectedRevision: Long? = null,
    /**
     * Agent/API callers normally require a runnable dry-run. Human/import flows
     * may opt out so an intentionally disabled draft can still be persisted.
     */
    val requireExecutable: Boolean = true
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

    data class NotFound(
        val automationId: String
    ) : AutomationMutationResult
}

/**
 * Single mutation boundary for future UI, MCP, REST, A2A and local-agent paths.
 *
 * This service never schedules alarms or invokes the execution engine directly.
 * Persisting through AutomationRepository lets the existing scheduler/monitors
 * observe definitions through their established repository Flow.
 */
class AutomationCommandService(
    private val repository: AutomationRepository,
    private val dryRunInspector: AutomationDryRunInspector,
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
            revision = nextRevision(existing)
        ).report
    }

    suspend fun create(
        draft: AgentTaskDraftV1,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val id = generateUniqueId()
        val prepared = prepare(
            draft = draft,
            id = id,
            existing = null,
            revision = clockMillis()
        )
        val automation = prepared.automation
        if (automation == null || !prepared.report.canPersist(context)) {
            return AutomationMutationResult.Rejected(prepared.report)
        }

        repository.saveAutomation(automation)
        return AutomationMutationResult.Success(
            automation = automation,
            revision = automation.updatedAt,
            dryRun = prepared.report.dryRun
        )
    }

    suspend fun update(
        automationId: String,
        draft: AgentTaskDraftV1,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val existing = repository.getAutomationById(automationId)
            ?: return AutomationMutationResult.NotFound(automationId)

        revisionConflict(existing, context)?.let { return it }

        val prepared = prepare(
            draft = draft,
            id = automationId,
            existing = existing,
            revision = nextRevision(existing)
        )
        val automation = prepared.automation
        if (automation == null || !prepared.report.canPersist(context)) {
            return AutomationMutationResult.Rejected(prepared.report)
        }

        if (!repository.saveAutomationIfRevisionMatches(
                automation = automation,
                expectedRevision = existing.updatedAt
            )
        ) {
            return concurrentConflict(
                automationId = automationId,
                expectedRevision = context.expectedRevision ?: existing.updatedAt
            )
        }
        return AutomationMutationResult.Success(
            automation = automation,
            revision = automation.updatedAt,
            dryRun = prepared.report.dryRun
        )
    }

    suspend fun setEnabled(
        automationId: String,
        enabled: Boolean,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val existing = repository.getAutomationById(automationId)
            ?: return AutomationMutationResult.NotFound(automationId)

        revisionConflict(existing, context)?.let { return it }

        val candidate = existing.copy(
            enabled = enabled,
            updatedAt = nextRevision(existing)
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
            return AutomationMutationResult.Rejected(report)
        }

        if (!repository.saveAutomationIfRevisionMatches(
                automation = candidate,
                expectedRevision = existing.updatedAt
            )
        ) {
            return concurrentConflict(
                automationId = automationId,
                expectedRevision = context.expectedRevision ?: existing.updatedAt
            )
        }
        return AutomationMutationResult.Success(
            automation = candidate,
            revision = candidate.updatedAt,
            dryRun = dryRun
        )
    }

    suspend fun delete(
        automationId: String,
        context: AutomationMutationContext
    ): AutomationMutationResult {
        val existing = repository.getAutomationById(automationId)
            ?: return AutomationMutationResult.NotFound(automationId)

        revisionConflict(existing, context)?.let { return it }

        val remaining = repository.getAutomations().first()
            .filterNot { it.id == automationId }
        val dependencyValidation = AutomationDependencyValidator.validate(remaining)
        val dependencyIssues = remaining.flatMap { dependencyValidation.issuesFor(it.id) }
        if (dependencyIssues.isNotEmpty()) {
            return AutomationMutationResult.DeleteBlocked(
                automationId = automationId,
                dependencyIssues = dependencyIssues
            )
        }

        repository.deleteAutomation(existing)
        return AutomationMutationResult.Success(
            automation = existing,
            revision = existing.updatedAt,
            dryRun = null
        )
    }

    private suspend fun prepare(
        draft: AgentTaskDraftV1,
        id: String,
        existing: Automation?,
        revision: Long
    ): PreparedMutation {
        val automation = try {
            AgentTaskMapper.toAutomation(
                draft = draft,
                id = id,
                nowMillis = revision,
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

    private suspend fun concurrentConflict(
        automationId: String,
        expectedRevision: Long?
    ): AutomationMutationResult.Conflict {
        val currentRevision = repository.getAutomationById(automationId)?.updatedAt
        return AutomationMutationResult.Conflict(
            automationId = automationId,
            expectedRevision = expectedRevision,
            currentRevision = currentRevision
        )
    }

    private fun revisionConflict(
        existing: Automation,
        context: AutomationMutationContext
    ): AutomationMutationResult.Conflict? {
        val expected = context.expectedRevision ?: return null
        if (expected == existing.updatedAt) return null
        return AutomationMutationResult.Conflict(
            automationId = existing.id,
            expectedRevision = expected,
            currentRevision = existing.updatedAt
        )
    }

    private fun nextRevision(existing: Automation?): Long {
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
