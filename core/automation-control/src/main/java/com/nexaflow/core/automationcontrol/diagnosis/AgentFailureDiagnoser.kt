package com.nexaflow.core.automationcontrol.diagnosis

import com.nexaflow.core.automationcontrol.AutomationDryRunInspector
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskMapper
import com.nexaflow.core.automationcontrol.validation.AgentWorkflowValidator
import com.nexaflow.core.execution.dryrun.WorkflowDryRunInput
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.first

/**
 * Phase 16 - event-driven failure diagnosis with bounded self-healing.
 *
 * Consumes failure signals (typically derived from `AUTOMATION_FAILED`
 * events) and answers two questions without side effects:
 *
 * 1. Why did this automation likely fail? ([AgentFailureCause] + evidence)
 * 2. Is there a safe, reversible correction? (an `enabled=false` draft)
 *
 * The diagnoser never writes the repository, never schedules work and never
 * executes actions. Applying [AgentFailureDiagnosisV1.proposedSafeDraft]
 * always goes through [com.nexaflow.core.automationcontrol.AutomationCommandService]
 * with optimistic concurrency, so a concurrent human edit wins over the
 * proposed correction.
 */
data class AgentFailureSignal(
    val automationId: String,
    val executionId: String? = null,
    /** Diagnostic code from execution history, e.g. `UNKNOWN_ERROR`. */
    val errorCode: String? = null,
    /** Already-redacted detail from history/audit. */
    val detail: String = ""
)

enum class AgentFailureCause {
    /** The automation no longer exists (deleted after the failure). */
    NOT_FOUND,

    /** The persisted definition no longer passes structural validation. */
    VALIDATION_CHANGED,

    /** Validation passes but the dry-run reports the task non-executable. */
    NOT_EXECUTABLE,

    /** The run failed with a capability-classified error while currently executable. */
    CAPABILITY_CHANGED,

    /** No structural or capability evidence; needs human/agent inspection. */
    UNKNOWN
}

enum class AgentProposedSafeAction {
    NONE,
    /** Re-persist the same definition with `enabled=false`. */
    DISABLE
}

data class AgentFailureDiagnosisV1(
    val automationId: String,
    val causes: List<AgentFailureCause>,
    val evidence: List<String>,
    val proposedSafeDraft: AgentTaskDraftV1? = null,
    val proposedAction: AgentProposedSafeAction = AgentProposedSafeAction.NONE
)

class AgentFailureDiagnoser(
    private val repository: AutomationRepository,
    private val dryRunInspector: AutomationDryRunInspector
) {
    suspend fun diagnose(signal: AgentFailureSignal): AgentFailureDiagnosisV1 {
        if (signal.automationId.isBlank()) {
            return AgentFailureDiagnosisV1(
                automationId = signal.automationId,
                causes = listOf(AgentFailureCause.NOT_FOUND),
                evidence = listOf("empty automation id")
            )
        }
        val automation = repository.getAutomationById(signal.automationId)
            ?: return AgentFailureDiagnosisV1(
                automationId = signal.automationId,
                causes = listOf(AgentFailureCause.NOT_FOUND),
                evidence = listOf("automation no longer persisted")
            )
        val catalog = repository.getAutomations().first()

        val validation = AgentWorkflowValidator.validate(automation, catalog)
        if (!validation.isValid) {
            val evidence = buildList {
                validation.workflowIssues.take(MAX_EVIDENCE).forEach { issue ->
                    add("workflow:${sanitize(issue.code.name)}@${sanitize(issue.location)}")
                }
                validation.configIssues.take(MAX_EVIDENCE).forEach { issue ->
                    add("config:${sanitize(issue.code)}@${sanitize(issue.owner)}")
                }
                signal.errorCode?.takeIf { it.isNotBlank() }?.let { code ->
                    add("history:${sanitize(code)}")
                }
            }.take(MAX_EVIDENCE)
            return AgentFailureDiagnosisV1(
                automationId = automation.id,
                causes = listOf(AgentFailureCause.VALIDATION_CHANGED),
                evidence = evidence,
                proposedSafeDraft = disableDraftIfEnabled(automation),
                proposedAction = if (automation.enabled) {
                    AgentProposedSafeAction.DISABLE
                } else {
                    AgentProposedSafeAction.NONE
                }
            )
        }

        val dryRun = dryRunInspector.inspect(
            WorkflowDryRunInput(
                automation = automation,
                automationCatalog = catalog
            )
        )
        if (!dryRun.executable) {
            val evidence = buildList {
                add("dry_run:${sanitize(dryRun.summary)}")
                signal.errorCode?.takeIf { it.isNotBlank() }?.let { code ->
                    add("history:${sanitize(code)}")
                }
            }
            return AgentFailureDiagnosisV1(
                automationId = automation.id,
                causes = listOf(AgentFailureCause.NOT_EXECUTABLE),
                evidence = evidence,
                proposedSafeDraft = disableDraftIfEnabled(automation),
                proposedAction = if (automation.enabled) {
                    AgentProposedSafeAction.DISABLE
                } else {
                    AgentProposedSafeAction.NONE
                }
            )
        }

        val capabilityCode = signal.errorCode?.takeIf { isCapabilityCode(it) }
        if (capabilityCode != null) {
            return AgentFailureDiagnosisV1(
                automationId = automation.id,
                causes = listOf(AgentFailureCause.CAPABILITY_CHANGED),
                evidence = listOf(
                    "history:${sanitize(capabilityCode)}",
                    "currently_executable:true"
                )
            )
        }

        return AgentFailureDiagnosisV1(
            automationId = automation.id,
            causes = listOf(AgentFailureCause.UNKNOWN),
            evidence = signal.errorCode?.takeIf { it.isNotBlank() }?.let { code ->
                listOf("history:${sanitize(code)}")
            } ?: listOf("no_discriminating_evidence")
        )
    }

    private fun disableDraftIfEnabled(
        automation: com.nexaflow.domain.models.Automation
    ): AgentTaskDraftV1? = if (automation.enabled) {
        AgentTaskMapper.fromAutomation(automation).copy(enabled = false)
    } else {
        null
    }

    private fun isCapabilityCode(code: String): Boolean {
        val normalized = code.uppercase()
        return "CAPABILITY" in normalized ||
            "SHIZUKU" in normalized ||
            "UNKNOWN" in normalized ||
            "PRIVILEGE" in normalized ||
            "ROOT" in normalized
    }

    /** Single-line, bounded evidence; the audit layer redacts values further. */
    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_EVIDENCE_CHARS)

    private companion object {
        const val MAX_EVIDENCE = 8
        const val MAX_EVIDENCE_CHARS = 256
    }
}
