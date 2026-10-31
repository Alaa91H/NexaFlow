package com.nexaflow.core.execution.handler

/**
 * What an automation may do when the agent asks for a tool approval.
 *
 * There is deliberately no "wait" option: an automation run can execute with
 * no screen attached, so waiting for a dialog would hang the task until its
 * deadline. [FAIL] stops the action without side effects; [SKIP] lets the agent
 * continue after the approval-gated tool is refused.
 */
enum class AgentApprovalPolicy { FAIL, SKIP }

/** Bounded, secret-free input for one agent execution requested by a task. */
data class AgentRunRequest(
    val agentId: String,
    /** Already resolved by the engine; `%variables` are expanded before this point. */
    val prompt: String,
    val maxDurationMillis: Long,
    val onApprovalRequired: AgentApprovalPolicy,
    /**
     * Stable for every retry of the same node in the same execution, so the
     * agent runtime can deduplicate instead of running the agent twice.
     */
    val idempotencyKey: String,
)

sealed interface AgentRunOutcome {
    /** Bounded, already-redacted summary of the final assistant answer. */
    data class Completed(val summary: String) : AgentRunOutcome

    /** Stable machine-readable failure code from the agent runtime. */
    data class Failed(val safeCode: String) : AgentRunOutcome
}

/**
 * Seam between `:core:execution` and the app-layer agent runtime.
 *
 * Keeping this a plain interface lets the executor stay free of Hilt, Room and
 * provider code: the app binds the real implementation (which runs the agent
 * through [com.nexaflow.core.agentruntime.ManagedAgentRunUseCase]) and tests
 * bind a deterministic fake.
 */
fun interface AgentRunGateway {
    suspend fun run(request: AgentRunRequest): AgentRunOutcome
}
