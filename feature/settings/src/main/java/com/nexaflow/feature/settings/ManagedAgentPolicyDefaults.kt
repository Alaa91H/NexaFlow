package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiToolDefinition

/** Selects tools using least privilege: every non-read-only or unknown tool asks first. */
internal fun ManagedAgentDraft.toggleTool(tool: AiToolDefinition): ManagedAgentDraft {
    val allowed = allowedToolNames.toggle(tool.name)
    val wasSelected = tool.name in allowedToolNames
    val approvalRequired = when {
        wasSelected -> approvalRequiredToolNames - tool.name
        tool.readOnly || tool.name in approvalOptionalToolNames -> approvalRequiredToolNames - tool.name
        else -> approvalRequiredToolNames + tool.name
    }
    val approvalOptional = when {
        tool.readOnly -> approvalOptionalToolNames - tool.name
        else -> approvalOptionalToolNames
    }
    return copy(
        allowedToolNames = allowed,
        approvalRequiredToolNames = approvalRequired intersect allowed,
        // Keep explicit opt-outs when a tool is temporarily removed so a later
        // re-selection cannot silently revert the user's approval preference.
        approvalOptionalToolNames = approvalOptional
    )
}

internal fun ManagedAgentDraft.toggleApprovalRequirement(toolName: String): ManagedAgentDraft {
    require(toolName in allowedToolNames)
    val requireApproval = toolName !in approvalRequiredToolNames
    return copy(
        approvalRequiredToolNames = if (requireApproval) approvalRequiredToolNames + toolName else approvalRequiredToolNames - toolName,
        approvalOptionalToolNames = if (requireApproval) approvalOptionalToolNames - toolName else approvalOptionalToolNames + toolName
    )
}

private fun Set<String>.toggle(name: String): Set<String> =
    if (name in this) this - name else this + name
