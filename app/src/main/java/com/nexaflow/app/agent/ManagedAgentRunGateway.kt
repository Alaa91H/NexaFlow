package com.nexaflow.app.agent

import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.agentruntime.ManagedAgentRunUseCase
import com.nexaflow.core.execution.handler.AgentApprovalPolicy
import com.nexaflow.core.execution.handler.AgentRunGateway
import com.nexaflow.core.execution.handler.AgentRunOutcome
import com.nexaflow.core.execution.handler.AgentRunRequest
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Bridges the executor's [AgentRunGateway] seam to the real agent runtime.
 *
 * An automation has no approval dialog, so the gateway denies every gated tool
 * outright: with [AgentApprovalPolicy.FAIL] the run is aborted before any
 * remaining tool can execute, and with [AgentApprovalPolicy.SKIP] the denial is
 * handed back to the model so it can continue with the tools it still owns.
 */
@Singleton
class ManagedAgentRunGateway @Inject constructor(
    /** Lazy: the agent runtime reaches the executor through the tool adapter. */
    private val runs: Lazy<ManagedAgentRunUseCase>,
) : AgentRunGateway {

    override suspend fun run(request: AgentRunRequest): AgentRunOutcome {
        val output = StringBuilder()
        var outcome: AgentRunOutcome? = null
        try {
            withTimeout(request.maxDurationMillis) {
                runs.get().runAgent(
                    agentId = request.agentId,
                    prompt = request.prompt,
                    idempotencyKey = request.idempotencyKey,
                ).collect { event ->
                    when (event) {
                        is ManagedAgentRunEvent.AssistantDelta -> {
                            if (output.length < MAX_OUTPUT_CHARACTERS) {
                                output.append(event.text.take(MAX_OUTPUT_CHARACTERS - output.length))
                            }
                        }
                        is ManagedAgentRunEvent.ApprovalRequired -> {
                            // Deny first: this both releases the waiting run and
                            // guarantees the gated tool never executes.
                            runs.get().resolveApproval(event.approvalId, approved = false)
                            if (request.onApprovalRequired == AgentApprovalPolicy.FAIL) {
                                outcome = AgentRunOutcome.Failed("approval_required")
                                throw ApprovalAbort()
                            }
                        }
                        is ManagedAgentRunEvent.Completed ->
                            outcome = outcome ?: AgentRunOutcome.Completed(output.toString())
                        is ManagedAgentRunEvent.Failed -> outcome = AgentRunOutcome.Failed(event.safeCode)
                        is ManagedAgentRunEvent.Rejected -> outcome = AgentRunOutcome.Failed(event.safeCode)
                        is ManagedAgentRunEvent.Existing -> outcome = AgentRunOutcome.Failed("existing_run")
                        is ManagedAgentRunEvent.Queued -> Unit
                    }
                }
            }
        } catch (_: ApprovalAbort) {
            // Expected abort path: the denied run is cancelled by the flow exit.
        } catch (_: TimeoutCancellationException) {
            return AgentRunOutcome.Failed("deadline_exceeded")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AgentRunOutcome.Failed("run_failed")
        }
        return outcome ?: AgentRunOutcome.Failed("run_incomplete")
    }

    private class ApprovalAbort : RuntimeException("agent approval required")

    private companion object {
        const val MAX_OUTPUT_CHARACTERS = 16_384
    }
}
