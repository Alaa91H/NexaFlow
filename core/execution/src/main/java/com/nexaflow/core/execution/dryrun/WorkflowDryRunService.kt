package com.nexaflow.core.execution.dryrun

import com.nexaflow.core.execution.capability.CapabilityResolution
import com.nexaflow.core.execution.capability.CapabilityResolver
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.workflow.WorkflowValidationResult
import com.nexaflow.domain.workflow.WorkflowValidator

/** Input is intentionally explicit: action-to-capability mapping remains in the existing action registry. */
data class WorkflowDryRunInput(
    val automation: Automation,
    val capabilityRequests: List<CapabilityRequest> = emptyList()
)

data class WorkflowDryRunReport(
    val workflowValidation: WorkflowValidationResult,
    val capabilityResolutions: List<CapabilityResolution>,
    val executable: Boolean,
    val summary: String
)

/**
 * Read-only preflight. It calls the same validator/policy/resolver used by
 * execution but never invokes CapabilityBackend.execute or ActionHandler.
 */
class WorkflowDryRunService(
    private val capabilityResolver: CapabilityResolver,
    private val deviceStateProvider: suspend () -> CapabilityDeviceState
) {
    suspend fun inspect(input: WorkflowDryRunInput): WorkflowDryRunReport {
        val workflowValidation = WorkflowValidator.validate(input.automation)
        if (!workflowValidation.isValid) {
            return WorkflowDryRunReport(
                workflowValidation = workflowValidation,
                capabilityResolutions = emptyList(),
                executable = false,
                summary = "Workflow validation failed"
            )
        }
        val state = deviceStateProvider()
        val resolutions = input.capabilityRequests.map { request ->
            capabilityResolver.resolve(request, state)
        }
        val executable = resolutions.all { it.isResolved }
        return WorkflowDryRunReport(
            workflowValidation = workflowValidation,
            capabilityResolutions = resolutions,
            executable = executable,
            summary = when {
                !executable -> "One or more capabilities are unavailable or blocked by policy"
                resolutions.isEmpty() -> "Workflow is structurally valid; no capability-mapped actions were supplied"
                else -> "Workflow and requested capabilities passed dry-run"
            }
        )
    }
}
