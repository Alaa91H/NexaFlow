package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.CancellationException

/**
 * Runs one saved in-app AI agent from a task.
 *
 * The handler owns no provider, model or secret knowledge: it validates the
 * persisted config, forwards a bounded request through [AgentRunGateway] and
 * turns the outcome into the normal execution result. A missing gateway keeps
 * the action fail-closed instead of silently doing nothing.
 */
class AgentActionsHandler : ActionHandler {

    /** Bound by the app layer; null keeps the action fail-closed in headless builds. */
    var gateway: AgentRunGateway? = null

    override val supportedTypes: Set<ActionType> = setOf(ActionType.AGENT_RUN)

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val agentId = action.config[KEY_AGENT_ID].orEmpty().trim()
        val prompt = action.config[KEY_PROMPT].orEmpty().trim()
        if (agentId.isEmpty() || prompt.isEmpty()) {
            return SystemControlResult.fail(
                message = "Agent id and prompt are required",
                errorCode = "agent_config_invalid",
            )
        }
        if (prompt.length > MAX_PROMPT_CHARACTERS) {
            return SystemControlResult.fail(
                message = "Agent prompt is too long",
                errorCode = "agent_prompt_too_long",
            )
        }
        val seconds = action.config[KEY_MAX_DURATION]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.toLongOrNull()
            ?: DEFAULT_DURATION_SECONDS
        if (seconds !in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
            return SystemControlResult.fail(
                message = "Agent duration is out of range",
                errorCode = "agent_duration_invalid",
            )
        }
        val resultVariable = action.config[KEY_RESULT_VARIABLE]?.trim()?.takeIf(String::isNotEmpty)
        if (resultVariable != null && !RESULT_VARIABLE.matches(resultVariable)) {
            return SystemControlResult.fail(
                message = "Agent result variable is invalid",
                errorCode = "agent_result_variable_invalid",
            )
        }
        val policy = when (action.config[KEY_APPROVAL_POLICY]?.trim()?.uppercase()) {
            null, "", APPROVAL_FAIL -> AgentApprovalPolicy.FAIL
            APPROVAL_SKIP -> AgentApprovalPolicy.SKIP
            else -> return SystemControlResult.fail(
                message = "Unknown agent approval policy",
                errorCode = "agent_policy_invalid",
            )
        }
        val gateway = gateway ?: return SystemControlResult.fail(
            message = "Agent runtime is unavailable",
            errorCode = "agent_runtime_unavailable",
        )

        val outcome = try {
            gateway.run(
                AgentRunRequest(
                    agentId = agentId,
                    prompt = prompt,
                    maxDurationMillis = seconds * MILLIS_PER_SECOND,
                    onApprovalRequired = policy,
                    idempotencyKey = "${ctx.executionId ?: "run"}:${ctx.nodeId ?: "action"}:$agentId",
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SystemControlResult.fail(
                message = "Agent run failed",
                errorCode = "agent_run_failed",
            )
        }

        return when (outcome) {
            is AgentRunOutcome.Completed -> {
                val summary = outcome.summary.trim().take(MAX_SUMMARY_CHARACTERS)
                resultVariable?.let { variable ->
                    ctx.runContext?.put("$.$variable", mapOf("summary" to summary))
                }
                SystemControlResult.ok(
                    message = if (summary.isEmpty()) "Agent run completed" else "Agent run completed: $summary",
                    executionChannel = "agent",
                )
            }
            is AgentRunOutcome.Failed -> SystemControlResult.fail(
                message = "Agent run failed: ${outcome.safeCode}",
                errorCode = outcome.safeCode.takeIf { SAFE_CODE.matches(it) } ?: "agent_run_failed",
            )
        }
    }

    private companion object {
        const val KEY_AGENT_ID = "agentId"
        const val KEY_PROMPT = "prompt"
        const val KEY_MAX_DURATION = "maxDurationSeconds"
        const val KEY_APPROVAL_POLICY = "onApprovalRequired"
        const val KEY_RESULT_VARIABLE = "resultVariable"
        const val APPROVAL_FAIL = "FAIL"
        const val APPROVAL_SKIP = "SKIP"
        const val MIN_DURATION_SECONDS = 1L
        const val MAX_DURATION_SECONDS = 3_600L
        const val DEFAULT_DURATION_SECONDS = 60L
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_PROMPT_CHARACTERS = 8_192
        const val MAX_SUMMARY_CHARACTERS = 2_000
        val SAFE_CODE = Regex("[a-z][a-z0-9_]{0,63}")
        val RESULT_VARIABLE = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    }
}
