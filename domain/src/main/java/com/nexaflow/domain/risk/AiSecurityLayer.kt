package com.nexaflow.domain.risk

import com.nexaflow.domain.capability.CapabilityId

/**
 * Defines the strict guardrails imposed on AI-generated automation routines.
 * 
 * NexaFlow allows AI to generate workflows, but these workflows are considered
 * inherently untrusted. This policy dictates which capabilities the AI can use,
 * whether human approval is forced, and maximum loop boundaries.
 */
data class AiRoutinePolicy(
    /** 
     * Capabilities the AI is absolutely forbidden to generate.
     * Attempting to import an AI workflow with these capabilities will fail validation.
     */
    val forbiddenCapabilities: Set<CapabilityId> = setOf(
        CapabilityId.SYSTEM_SETTING_WRITE,
        CapabilityId.PACKAGE_INSTALL,
        CapabilityId.PACKAGE_UNINSTALL,
        CapabilityId.FILE_COPY
    ),

    /** 
     * Capabilities that the AI is allowed to use ONLY IF a [HumanApprovalNode]
     * is inserted immediately prior in the workflow execution graph.
     */
    val requireHumanApproval: Set<CapabilityId> = setOf(
        CapabilityId.PACKAGE_FORCE_STOP,
        CapabilityId.PACKAGE_SET_ENABLED,
        CapabilityId.NETWORK_HTTP_REQUEST,
        CapabilityId.ACCESSIBILITY_INPUT_TEXT,
        CapabilityId.ACCESSIBILITY_CLICK,
        CapabilityId.ACCESSIBILITY_GESTURE,
        CapabilityId.PLUGIN_ACTION
    ),

    /** Maximum allowed iterations for loops in AI-generated routines to prevent infinite resource drain. */
    val maxLoopIterations: Int = 10,

    /** Maximum allowed execution time for the entire AI-generated routine. */
    val maxExecutionTimeMs: Long = 60_000L
)

/**
 * Validates AI-generated workflows against the [AiRoutinePolicy].
 * 
 * This layer sits between the AI Planner/Importer and the Workflow Engine.
 * It is invoked whenever an AI prompt translates into a new workflow definition.
 */
class AiSecurityLayer(private val policy: AiRoutinePolicy = AiRoutinePolicy()) {

    /**
     * Scans the requested capabilities from an AI-generated workflow.
     * @return [AiValidationResult] indicating if the workflow is safe to import.
     */
    fun validateAiCapabilities(requestedCapabilities: Set<CapabilityId>): AiValidationResult {
        val violations = mutableListOf<String>()

        val forbiddenUsed = requestedCapabilities.intersect(policy.forbiddenCapabilities)
        if (forbiddenUsed.isNotEmpty()) {
            violations.add("AI generated routine used forbidden capabilities: ${forbiddenUsed.joinToString()}")
        }

        val requiresApproval = requestedCapabilities.intersect(policy.requireHumanApproval)

        return AiValidationResult(
            isSafe = violations.isEmpty(),
            violations = violations,
            requiresForcedHumanApproval = requiresApproval.isNotEmpty(),
            approvalRequiredFor = requiresApproval
        )
    }
}

data class AiValidationResult(
    val isSafe: Boolean,
    val violations: List<String>,
    val requiresForcedHumanApproval: Boolean,
    val approvalRequiredFor: Set<CapabilityId>
)
