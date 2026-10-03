package com.nexaflow.app.agent

import android.content.Context
import android.provider.Settings
import com.nexaflow.core.agentruntime.AgentApproval
import com.nexaflow.core.agentruntime.AgentApprovalDecision
import com.nexaflow.core.agentruntime.AgentBudgetDecision
import com.nexaflow.core.agentruntime.AgentBudgetLedger
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.agentruntime.AgentRunStatus
import com.nexaflow.core.agentruntime.AgentToolApprovalGate
import com.nexaflow.core.agentruntime.AgentToolExecutionGuard
import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.agentruntime.ManagedAgentRunUseCase
import com.nexaflow.core.agentruntime.PolicyFilteredAgentToolExecutor
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiConversationEvent
import com.nexaflow.core.airuntime.AiConversationMessage
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiRoutingRequirements
import com.nexaflow.core.airuntime.AiRole
import com.nexaflow.core.airuntime.AiToolCall
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.AiToolResult
import com.nexaflow.core.airuntime.AiTraceRedactor
import com.nexaflow.data.agents.AgentDefinitionRepository
import com.nexaflow.data.agents.AgentRunRepository
import com.nexaflow.data.agents.AgentRunStartResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import com.nexaflow.core.agentruntime.AgentRun

@Singleton
class ManagedAgentRunCoordinator @Inject constructor(
    private val definitions: AgentDefinitionRepository,
    private val runs: AgentRunRepository,
    private val registry: AiProviderRegistry,
    private val tools: AiToolExecutor,
    @ApplicationContext private val context: Context
) : ManagedAgentRunUseCase {

    override fun observeRuns(agentId: String): Flow<List<AgentRun>> = runs.observeForAgent(agentId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun runAgent(
        agentId: String,
        prompt: String,
        idempotencyKey: String
    ): Flow<ManagedAgentRunEvent> = channelFlow {
        if (prompt.isBlank() || prompt.length > AiConversationEngine.MAX_MESSAGE_CHARACTERS) {
            send(ManagedAgentRunEvent.Rejected("invalid_request"))
            return@channelFlow
        }
        val definition = definitions.find(agentId)
        if (definition == null || !definition.enabled) {
            send(ManagedAgentRunEvent.Rejected("agent_unavailable"))
            return@channelFlow
        }

        var runId: String? = null
        try {
            val now = System.currentTimeMillis()
            val start = runs.start(
                agentId = definition.id,
                idempotencyKey = idempotencyKey,
                requestFingerprint = prompt,
                definitionRevision = definition.revision,
                maxConcurrentRuns = definition.policy.maxConcurrentRuns,
                deadlineAtMillis = now + definition.budget.maxDurationMillis
            )
            val run = when (start) {
                is AgentRunStartResult.Created -> start.run
                is AgentRunStartResult.Existing -> {
                    send(ManagedAgentRunEvent.Existing(start.run.id))
                    return@channelFlow
                }
                AgentRunStartResult.IdempotencyConflict -> {
                    send(ManagedAgentRunEvent.Rejected("idempotency_conflict"))
                    return@channelFlow
                }
                AgentRunStartResult.ConcurrentRunLimit -> {
                    send(ManagedAgentRunEvent.Rejected("concurrent_run_limit"))
                    return@channelFlow
                }
            }
            runId = run.id
            send(ManagedAgentRunEvent.Queued(run.id))
            if (!runs.markRunning(run.id)) {
                runs.finish(run.id, AgentRunStatus.FAILED, "run_transition_conflict")
                send(ManagedAgentRunEvent.Failed(run.id, "run_transition_conflict"))
                return@channelFlow
            }

            if (definition.budget.maxCostMicros != null) {
                runs.finish(run.id, AgentRunStatus.FAILED, "cost_unknown")
                send(ManagedAgentRunEvent.Failed(run.id, "cost_unknown"))
                return@channelFlow
            }

            val allowedTools = tools.tools.value
                .filter { it.name in definition.policy.allowedToolNames }
            val selectedProvider = registry.routeProvider(
                requirements = AiRoutingRequirements(requireTools = allowedTools.isNotEmpty()),
                preferredProviderId = definition.providerProfileId,
                allowCloudProvider = definition.policy.allowCloudData
            )
            if (selectedProvider == null) {
                runs.finish(run.id, AgentRunStatus.FAILED, "provider_unavailable")
                send(ManagedAgentRunEvent.Failed(run.id, "provider_unavailable"))
                return@channelFlow
            }
            val providerDescriptor = selectedProvider.descriptor.value
            if (definition.modelId != null && definition.modelId != providerDescriptor.modelId) {
                runs.finish(run.id, AgentRunStatus.FAILED, "provider_model_mismatch")
                send(ManagedAgentRunEvent.Failed(run.id, "provider_model_mismatch"))
                return@channelFlow
            }

            val ledger = AgentBudgetLedger(definition.budget)
            val approvalGate = AgentToolApprovalGate { agent, call, onRequested ->
                requestApproval(definition, run.id, agent, call, onRequested)
            }
            val policyExecutor = PolicyFilteredAgentToolExecutor(
                agentId = definition.id,
                source = tools,
                policy = definition.policy,
                approvalGate = approvalGate,
                onApprovalRequested = { approvalId, call ->
                    val redactedArguments = AiTraceRedactor.redactElement(call.arguments).toString()
                        .take(AiTraceRedactor.MAX_PREVIEW_CHARS)
                    send(ManagedAgentRunEvent.ApprovalRequired(approvalId, call.name, redactedArguments))
                },
                executionGuard = AgentToolExecutionGuard { call ->
                    val live = definitions.find(definition.id)
                    when {
                        live == null || !live.enabled -> "agent_disabled"
                        live.revision != definition.revision -> "agent_definition_changed"
                        call.name !in live.policy.allowedToolNames -> "tool_not_allowed"
                        else -> null
                    }
                }
            )
            val budgetExecutor = BudgetedToolExecutor(policyExecutor, ledger)
            val engine = AiConversationEngine(
                registry = registry,
                toolExecutor = budgetExecutor,
                turnTimeoutMillis = minOf(definition.budget.maxDurationMillis, AiConversationEngine.DEFAULT_TURN_TIMEOUT_MS),
                maxToolIterations = definition.budget.maxTurns + 1,
                allowTurn = { ledger.beginTurn() == AgentBudgetDecision.ALLOWED }
            )

            var terminalStatus = AgentRunStatus.FAILED
            var terminalCode = "engine_incomplete"
            var fatalToolFailure: String? = null
            withTimeout(definition.budget.maxDurationMillis) {
                engine.stream(
                    conversationId = run.id,
                    messages = listOf(
                        AiConversationMessage(AiRole.SYSTEM, definition.systemInstructions),
                        AiConversationMessage(AiRole.USER, prompt)
                    ),
                    providerIdOverride = providerDescriptor.id,
                    allowCloudProvider = definition.policy.allowCloudData,
                    outputCharacterLimit = definition.budget.maxOutputCharacters,
                    toolCallLimitPerTurn = minOf(definition.budget.maxToolCalls, AiConversationEngine.MAX_TOOL_CALLS_PER_TURN)
                ).collect { event ->
                    when (event) {
                        is AiConversationEvent.AssistantDelta -> {
                            if (ledger.reserveOutput(event.text.length) != AgentBudgetDecision.ALLOWED) {
                                terminalCode = "output_limit"
                            } else {
                                send(ManagedAgentRunEvent.AssistantDelta(event.text))
                            }
                        }
                        is AiConversationEvent.ToolStarted -> runs.recordToolActivity(run.id, started = true)
                        is AiConversationEvent.ToolFinished -> {
                            runs.recordToolActivity(run.id, started = false)
                            val toolError = (event.result.output as? JsonObject)
                                ?.get("error")?.jsonPrimitive?.contentOrNull
                            if (toolError in TERMINAL_TOOL_FAILURES) fatalToolFailure = toolError
                        }
                        is AiConversationEvent.Completed -> {
                            if (fatalToolFailure == null) {
                                terminalStatus = AgentRunStatus.COMPLETED
                                terminalCode = "completed"
                            }
                        }
                        is AiConversationEvent.Unavailable -> terminalCode = "provider_unavailable"
                        is AiConversationEvent.Failed -> {
                            terminalStatus = AgentRunStatus.FAILED
                            terminalCode = event.code.takeIf(SAFE_FAILURE_CODES::contains) ?: "run_failed"
                        }
                    }
                }
            }
            fatalToolFailure?.let { code ->
                terminalStatus = AgentRunStatus.FAILED
                terminalCode = code
            }
            val usage = ledger.usage()
            runs.recordUsage(
                run.id, usage.turns, usage.toolCalls, usage.outputCharacters,
                usage.costMicros, providerDescriptor.id, providerDescriptor.modelId
            )
            runs.finish(run.id, terminalStatus, terminalCode)
            if (terminalStatus == AgentRunStatus.COMPLETED) {
                send(ManagedAgentRunEvent.Completed(run.id))
            } else {
                send(ManagedAgentRunEvent.Failed(run.id, terminalCode))
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            runId?.let { runs.finish(it, AgentRunStatus.FAILED, "deadline_exceeded") }
            send(ManagedAgentRunEvent.Failed(runId, "deadline_exceeded"))
        } catch (cancelled: CancellationException) {
            runId?.let { withContext(NonCancellable) { runs.finish(it, AgentRunStatus.CANCELLED, "cancelled") } }
            throw cancelled
        } catch (_: Exception) {
            runId?.let { runs.finish(it, AgentRunStatus.FAILED, "run_failed") }
            send(ManagedAgentRunEvent.Failed(runId, "run_failed"))
        }
    }

    override suspend fun resolveApproval(approvalId: String, approved: Boolean): Boolean {
        val approval = runs.findApproval(approvalId) ?: return false
        val currentDefinition = definitions.find(approval.agentId) ?: return false
        if (!currentDefinition.enabled) return false
        return runs.resolveApproval(
            approvalId = approvalId,
            decision = if (approved) AgentApprovalDecision.APPROVED else AgentApprovalDecision.DENIED,
            currentDefinitionRevision = currentDefinition.revision,
            currentDeviceBindingHash = deviceBindingHash()
        )
    }

    private suspend fun requestApproval(
        definition: AgentDefinition,
        runId: String,
        agentId: String,
        call: AiToolCall,
        onRequested: suspend (String, AiToolCall) -> Unit
    ): Boolean {
        if (agentId != definition.id) return false
        val now = System.currentTimeMillis()
        val approvalId = UUID.randomUUID().toString()
        val fingerprint = sha256("${call.id}\u0000${call.name}\u0000${call.arguments}")
        val deadline = runs.find(runId)?.deadlineAtMillis ?: return false
        val expiresAt = minOf(now + APPROVAL_WINDOW_MILLIS, deadline)
        val created = runs.createApproval(
            AgentApproval(
                id = approvalId,
                runId = runId,
                agentId = definition.id,
                definitionRevision = definition.revision,
                toolName = call.name,
                callFingerprint = fingerprint,
                deviceBindingHash = deviceBindingHash(),
                expiresAtMillis = expiresAt
            )
        )
        if (!created) return false
        val redactedArguments = AiTraceRedactor.redactElement(call.arguments).toString()
            .take(AiTraceRedactor.MAX_PREVIEW_CHARS)
        onRequested(approvalId, call)
        return runs.awaitApprovalDecision(approvalId) == AgentApprovalDecision.APPROVED
    }

    private fun deviceBindingHash(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf(String::isNotBlank) ?: context.packageName
        return sha256("${context.packageName}\u0000$androidId")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private class BudgetedToolExecutor(
        private val delegate: AiToolExecutor,
        private val ledger: AgentBudgetLedger
    ) : AiToolExecutor {
        override val tools = delegate.tools

        override suspend fun execute(call: AiToolCall): AiToolResult {
            val decision = ledger.beginToolCall()
            if (decision != AgentBudgetDecision.ALLOWED) {
                val code = when (decision) {
                    AgentBudgetDecision.DEADLINE -> "deadline_exceeded"
                    else -> "tool_limit"
                }
                return AiToolResult(call.id, call.name, buildJsonObject { put("error", code) }, true)
            }
            return delegate.execute(call)
        }
    }

    private companion object {
        const val APPROVAL_WINDOW_MILLIS = 5 * 60_000L
        val SAFE_FAILURE_CODES = setOf(
            "invalid_conversation", "output_limit", "tool_limit", "provider_context_limit",
            "provider_failure", "tool_iteration_limit", "turn_limit", "agent_disabled",
            "agent_definition_changed", "deadline_exceeded", "tool_not_allowed"
        )
        val TERMINAL_TOOL_FAILURES = setOf(
            "tool_limit", "deadline_exceeded", "agent_disabled",
            "agent_definition_changed", "tool_not_allowed"
        )
    }
}
