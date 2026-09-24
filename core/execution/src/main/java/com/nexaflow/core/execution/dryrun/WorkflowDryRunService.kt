package com.nexaflow.core.execution.dryrun

import com.nexaflow.core.execution.capability.CapabilityResolution
import com.nexaflow.core.execution.capability.CapabilityResolver
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidationResult
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowExecutionPlan
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowPlanner
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.models.Automation
import android.os.Build
import com.nexaflow.domain.workflow.AutomationDependencyValidator
import com.nexaflow.domain.workflow.WorkflowValidationResult
import com.nexaflow.domain.workflow.WorkflowValidator

/** Input is intentionally explicit: action-to-capability mapping remains in the existing action registry. */
data class WorkflowDryRunInput(
    val automation: Automation,
    val capabilityRequests: List<CapabilityRequest> = emptyList(),
    /** Existing definitions relevant to dependency validation; the inspected automation always wins. */
    val automationCatalog: List<Automation> = emptyList()
)

data class WorkflowDryRunReport(
    val workflowValidation: WorkflowValidationResult,
    val capabilityResolutions: List<CapabilityResolution>,
    val executable: Boolean,
    val summary: String,
    val requirementValidation: WorkflowCapabilityValidationResult? = null,
    val semanticPlan: SemanticWorkflowExecutionPlan? = null
)

/**
 * Read-only preflight. It calls the same validator/policy/resolver used by
 * execution but never invokes CapabilityBackend.execute or ActionHandler.
 */
class WorkflowDryRunService(
    private val capabilityResolver: CapabilityResolver,
    private val capabilitySnapshotProvider: (() -> CapabilitySnapshot)? = null,
    private val privilegeSnapshotProvider: (() -> PrivilegeSnapshot)? = null,
    private val semanticWorkflowPlanner: SemanticWorkflowPlanner? = null,
    private val sdkProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val deviceStateProvider: suspend () -> CapabilityDeviceState
) {
    suspend fun inspect(input: WorkflowDryRunInput): WorkflowDryRunReport {
        val catalog = input.automationCatalog.filterNot { it.id == input.automation.id } + input.automation
        val workflowValidation = WorkflowValidationResult(
            WorkflowValidator.validate(input.automation).issues +
                AutomationDependencyValidator.validate(catalog).issuesFor(input.automation.id)
        )
        if (!workflowValidation.isValid) {
            return WorkflowDryRunReport(
                workflowValidation = workflowValidation,
                capabilityResolutions = emptyList(),
                executable = false,
                summary = "Workflow validation failed"
            )
        }
        val requirementValidation = capabilitySnapshotProvider?.invoke()?.let { snapshot ->
            WorkflowCapabilityValidator.validate(
                automation = input.automation,
                capabilitySnapshot = snapshot,
                privilegeSnapshot = privilegeSnapshotProvider?.invoke() ?: PrivilegeSnapshot(),
                sdk = sdkProvider()
            )
        }
        val semanticPlan = semanticWorkflowPlanner?.plan(input.automation)
        val state = deviceStateProvider()
        val resolutions = input.capabilityRequests.map { request ->
            capabilityResolver.resolve(request, state)
        }
        val requestsExecutable = resolutions.all { it.isResolved }
        val requirementsExecutable = requirementValidation?.admissible != false
        val semanticExecutable = semanticPlan?.executable != false
        val executable = requestsExecutable && requirementsExecutable && semanticExecutable
        return WorkflowDryRunReport(
            workflowValidation = workflowValidation,
            capabilityResolutions = resolutions,
            executable = executable,
            summary = when {
                requirementValidation?.admissible == false ->
                    "Workflow execution requirements are unavailable"
                semanticPlan?.pendingUserActionOwners?.isNotEmpty() == true ->
                    "Workflow has semantic actions that require user interaction"
                semanticPlan?.unavailableOwners?.isNotEmpty() == true ->
                    "Workflow has semantic actions without an automatic execution route"
                !requestsExecutable ->
                    "One or more capabilities are unavailable or blocked by policy"
                requirementValidation?.state ==
                    com.nexaflow.domain.capability.ExecutionRequirementState.UNKNOWN ->
                    "Workflow is valid; one or more execution requirements await a fresh observation"
                resolutions.isEmpty() ->
                    "Workflow and observed execution routes passed dry-run"
                else -> "Workflow and requested capabilities passed dry-run"
            },
            requirementValidation = requirementValidation,
            semanticPlan = semanticPlan
        )
    }
}
