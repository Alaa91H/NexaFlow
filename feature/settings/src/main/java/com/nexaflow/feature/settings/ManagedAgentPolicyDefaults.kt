package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiToolDefinition

/** Selects tools using least privilege: every non-read-only or unknown tool asks first. */
internal fun ManagedAgentDraft.toggleTool(tool: AiToolDefinition): ManagedAgentDraft {
    val allowed = allowedToolNames.toggle(tool.name)
    val approvalRequired = when {
        tool.name !in allowed -> approvalRequiredToolNames - tool.name
        !tool.readOnly -> approvalRequiredToolNames + tool.name
        else -> approvalRequiredToolNames - tool.name
    }
    return copy(
        allowedToolNames = allowed,
        approvalRequiredToolNames = approvalRequired intersect allowed
    )
}

private fun Set<String>.toggle(name: String): Set<String> =
    if (name in this) this - name else this + name
