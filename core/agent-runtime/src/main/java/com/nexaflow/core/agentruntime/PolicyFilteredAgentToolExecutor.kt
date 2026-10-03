package com.nexaflow.core.agentruntime

import com.nexaflow.core.airuntime.AiToolCall
import com.nexaflow.core.airuntime.AiToolDefinition
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.AiToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface AgentToolApprovalGate {
    suspend fun approve(agentId: String, call: AiToolCall, onRequested: suspend (String, AiToolCall) -> Unit): Boolean
}

fun interface AgentToolExecutionGuard {
    /** Return a safe rejection code when the live definition no longer authorizes this call. */
    suspend fun rejectionCode(call: AiToolCall): String?
}

/** Enforces the agent's allowlist at discovery and execution time. */
class PolicyFilteredAgentToolExecutor(
    private val agentId: String,
    private val source: AiToolExecutor,
    private val policy: AgentPolicy,
    private val approvalGate: AgentToolApprovalGate,
    private val onApprovalRequested: suspend (String, AiToolCall) -> Unit = { _, _ -> },
    private val executionGuard: AgentToolExecutionGuard = AgentToolExecutionGuard { null }
) : AiToolExecutor {
    init {
        require(agentId.isNotBlank())
    }

    /** The AI engine reads this snapshot once per run; no detached collector is needed. */
    override val tools: StateFlow<List<AiToolDefinition>> = MutableStateFlow(
        source.tools.value.filter { it.name in policy.allowedToolNames }
    )

    override suspend fun execute(call: AiToolCall): AiToolResult {
        executionGuard.rejectionCode(call)?.let { return failure(call, it) }
        val definition = source.tools.value.firstOrNull { it.name == call.name }
        val failure = when {
            call.name !in policy.allowedToolNames -> "tool_not_allowed"
            definition == null -> "tool_unavailable"
            (call.name in policy.approvalRequiredToolNames ||
                call.name !in policy.approvalOptionalToolNames && !definition.readOnly) &&
                !approvalGate.approve(agentId, call, onApprovalRequested) -> "approval_denied"
            else -> null
        }
        if (failure != null) return failure(call, failure)
        executionGuard.rejectionCode(call)?.let { return failure(call, it) }
        return source.execute(call)
    }

    private fun failure(call: AiToolCall, code: String) = AiToolResult(
        callId = call.id,
        toolName = call.name,
        output = buildJsonObject { put("error", code) },
        isError = true
    )

}
