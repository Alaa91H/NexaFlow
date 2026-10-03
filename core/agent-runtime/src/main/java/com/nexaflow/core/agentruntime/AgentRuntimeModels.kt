package com.nexaflow.core.agentruntime

import com.nexaflow.core.airuntime.AiConversationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Stable, user-owned identity and policy for one in-app AI agent. */
@Serializable
data class AgentDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val systemInstructions: String = "",
    /** References a credential-backed provider profile; never stores a credential. */
    val providerProfileId: String? = null,
    val modelId: String? = null,
    val policy: AgentPolicy = AgentPolicy(),
    val budget: AgentBudget = AgentBudget(),
    val enabled: Boolean = false,
    val revision: Long = 1L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = createdAtMillis
) {
    init {
        require(ID_PATTERN.matches(id)) { "Agent id must be stable and machine-readable" }
        require(name.isNotBlank() && name.length <= MAX_NAME_CHARACTERS)
        require(description.length <= MAX_DESCRIPTION_CHARACTERS)
        require(systemInstructions.length <= MAX_INSTRUCTION_CHARACTERS)
        require(providerProfileId == null || ID_PATTERN.matches(providerProfileId))
        require(modelId == null || modelId.isNotBlank() && modelId.length <= MAX_MODEL_ID_CHARACTERS)
        require(revision > 0L)
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
    }

    companion object {
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        const val MAX_NAME_CHARACTERS = 80
        const val MAX_DESCRIPTION_CHARACTERS = 512
        const val MAX_INSTRUCTION_CHARACTERS = 32_768
        const val MAX_MODEL_ID_CHARACTERS = 256
    }
}

/**
 * Least-privilege tool policy. Approval requirements must be a subset of the
 * allowlist so a confirmation can never grant a tool the agent was not given.
 */
@Serializable
data class AgentPolicy(
    val allowedToolNames: Set<String> = emptySet(),
    val approvalRequiredToolNames: Set<String> = emptySet(),
    /** Explicit, user-chosen opt-out from the default approval on write/unknown tools. */
    val approvalOptionalToolNames: Set<String> = emptySet(),
    val allowCloudData: Boolean = false,
    val maxConcurrentRuns: Int = 1
) {
    init {
        require(allowedToolNames.size <= MAX_TOOLS)
        require(allowedToolNames.all(::validToolName))
        require(approvalRequiredToolNames.size <= MAX_TOOLS)
        require(approvalRequiredToolNames.all(::validToolName))
        require(allowedToolNames.containsAll(approvalRequiredToolNames))
        require(approvalOptionalToolNames.size <= MAX_TOOLS)
        require(approvalOptionalToolNames.all(::validToolName))
        require(allowedToolNames.containsAll(approvalOptionalToolNames))
        require(approvalRequiredToolNames.intersect(approvalOptionalToolNames).isEmpty())
        require(maxConcurrentRuns in 1..MAX_CONCURRENT_RUNS)
    }

    companion object {
        private val TOOL_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        const val MAX_TOOLS = 256
        const val MAX_CONCURRENT_RUNS = 8

        private fun validToolName(value: String) = TOOL_PATTERN.matches(value)
    }
}

/** Finite per-run limits. Unknown provider cost is rejected when a cost cap is set. */
@Serializable
data class AgentBudget(
    val maxDurationMillis: Long = 60_000L,
    val maxTurns: Int = AiConversationEngine.DEFAULT_MAX_TOOL_ITERATIONS,
    val maxToolCalls: Int = 32,
    val maxOutputCharacters: Int = AiConversationEngine.MAX_OUTPUT_CHARACTERS,
    val maxCostMicros: Long? = null
) {
    init {
        require(maxDurationMillis in 1L..MAX_DURATION_MILLIS)
        require(maxTurns in 1..MAX_TURNS)
        require(maxToolCalls in 1..MAX_TOOL_CALLS)
        require(maxOutputCharacters in 1..MAX_OUTPUT_CHARACTERS)
        require(maxCostMicros == null || maxCostMicros > 0L)
    }

    companion object {
        const val MAX_DURATION_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_TURNS = 64
        const val MAX_TOOL_CALLS = 256
        const val MAX_OUTPUT_CHARACTERS = AiConversationEngine.MAX_OUTPUT_CHARACTERS
    }
}

enum class AgentBudgetDecision {
    ALLOWED,
    DEADLINE,
    TURN_LIMIT,
    TOOL_CALL_LIMIT,
    OUTPUT_LIMIT,
    COST_LIMIT,
    COST_UNKNOWN
}

data class AgentBudgetUsage(
    val turns: Int,
    val toolCalls: Int,
    val outputCharacters: Int,
    val costMicros: Long,
    val elapsedMillis: Long
)

@Serializable
enum class AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED;

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, CANCELLED, INTERRUPTED)
}

/** Durable run metadata; content and provider credentials deliberately have no fields here. */
@Serializable
data class AgentRun(
    val id: String,
    val agentId: String,
    val idempotencyKeyHash: String,
    val requestFingerprint: String,
    val definitionRevision: Long,
    val status: AgentRunStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
    val deadlineAtMillis: Long,
    val providerId: String? = null,
    val modelId: String? = null,
    val outcomeCode: String? = null,
    val turns: Int = 0,
    val toolCalls: Int = 0,
    val outputCharacters: Int = 0,
    val costMicros: Long = 0L,
    val revision: Long = 1L
) {
    init {
        require(id.isNotBlank() && agentId.isNotBlank())
        require(idempotencyKeyHash.matches(HASH_PATTERN))
        require(requestFingerprint.matches(HASH_PATTERN))
        require(definitionRevision > 0L && revision > 0L)
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require(deadlineAtMillis >= createdAtMillis)
        require(turns >= 0 && toolCalls >= 0 && outputCharacters >= 0 && costMicros >= 0L)
        require(providerId == null || providerId.length <= MAX_IDENTIFIER_CHARACTERS)
        require(modelId == null || modelId.length <= MAX_IDENTIFIER_CHARACTERS)
        require(outcomeCode == null || outcomeCode.matches(CODE_PATTERN))
    }

    companion object {
        private val HASH_PATTERN = Regex("[a-f0-9]{64}")
        private val CODE_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
        const val MAX_IDENTIFIER_CHARACTERS = 256
    }
}

@Serializable
enum class AgentRunEventType {
    QUEUED,
    STARTED,
    TOOL_STARTED,
    TOOL_FINISHED,
    APPROVAL_REQUESTED,
    APPROVAL_RESOLVED,
    CANCEL_REQUESTED,
    TERMINAL
}

/** Allowlisted diagnostic facts only; prompt, user, tool arguments and raw output are never stored. */
@Serializable
data class AgentRunEvent(
    val id: String,
    val runId: String,
    val sequence: Long,
    val type: AgentRunEventType,
    val safeCode: String,
    val createdAtMillis: Long
) {
    init {
        require(id.isNotBlank() && runId.isNotBlank())
        require(sequence > 0L && createdAtMillis >= 0L)
        require(safeCode.matches(Regex("[a-z][a-z0-9_]{0,63}")))
    }
}

@Serializable
enum class AgentApprovalDecision { APPROVED, DENIED, EXPIRED, INVALIDATED }

/** One-time, revision- and device-bound permission for a single opaque tool call. */
@Serializable
data class AgentApproval(
    val id: String,
    val runId: String,
    val agentId: String,
    val definitionRevision: Long,
    val toolName: String,
    val callFingerprint: String,
    val deviceBindingHash: String,
    val expiresAtMillis: Long,
    val decision: AgentApprovalDecision? = null,
    val resolvedAtMillis: Long? = null
) {
    init {
        require(id.isNotBlank() && runId.isNotBlank() && agentId.isNotBlank())
        require(definitionRevision > 0L)
        require(toolName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
        require(callFingerprint.matches(Regex("[a-f0-9]{64}")))
        require(deviceBindingHash.matches(Regex("[a-f0-9]{64}")))
        require(expiresAtMillis >= 0L)
        require((decision == null) == (resolvedAtMillis == null))
    }
}

/** Thread-safe, monotonic per-run accounting. A rejected reservation never consumes budget. */
class AgentBudgetLedger(
    private val budget: AgentBudget,
    private val monotonicClock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val startedAt = monotonicClock()
    private var turns = 0
    private var toolCalls = 0
    private var outputCharacters = 0
    private var costMicros = 0L

    @Synchronized
    fun beginTurn(): AgentBudgetDecision = when {
        deadlineExceeded() -> AgentBudgetDecision.DEADLINE
        turns >= budget.maxTurns -> AgentBudgetDecision.TURN_LIMIT
        else -> {
            turns += 1
            AgentBudgetDecision.ALLOWED
        }
    }

    @Synchronized
    fun beginToolCall(): AgentBudgetDecision = when {
        deadlineExceeded() -> AgentBudgetDecision.DEADLINE
        toolCalls >= budget.maxToolCalls -> AgentBudgetDecision.TOOL_CALL_LIMIT
        else -> {
            toolCalls += 1
            AgentBudgetDecision.ALLOWED
        }
    }

    @Synchronized
    fun reserveOutput(characters: Int): AgentBudgetDecision {
        require(characters >= 0)
        if (deadlineExceeded()) return AgentBudgetDecision.DEADLINE
        if (characters > budget.maxOutputCharacters - outputCharacters) {
            return AgentBudgetDecision.OUTPUT_LIMIT
        }
        outputCharacters += characters
        return AgentBudgetDecision.ALLOWED
    }

    @Synchronized
    fun recordCost(actualMicros: Long?): AgentBudgetDecision {
        val cap = budget.maxCostMicros ?: return AgentBudgetDecision.ALLOWED
        if (actualMicros == null) return AgentBudgetDecision.COST_UNKNOWN
        require(actualMicros >= 0L)
        if (actualMicros > cap - costMicros) return AgentBudgetDecision.COST_LIMIT
        costMicros += actualMicros
        return AgentBudgetDecision.ALLOWED
    }

    @Synchronized
    fun usage(): AgentBudgetUsage = AgentBudgetUsage(
        turns = turns,
        toolCalls = toolCalls,
        outputCharacters = outputCharacters,
        costMicros = costMicros,
        elapsedMillis = (monotonicClock() - startedAt).coerceAtLeast(0L)
    )

    private fun deadlineExceeded(): Boolean =
        (monotonicClock() - startedAt).coerceAtLeast(0L) >= budget.maxDurationMillis
}

sealed interface ManagedAgentRunEvent {
    data class Queued(val runId: String) : ManagedAgentRunEvent
    data class Existing(val runId: String) : ManagedAgentRunEvent
    data class AssistantDelta(val text: String) : ManagedAgentRunEvent
    data class ApprovalRequired(
        val approvalId: String,
        val toolName: String,
        val redactedArguments: String
    ) : ManagedAgentRunEvent
    data class Completed(val runId: String) : ManagedAgentRunEvent
    data class Failed(val runId: String?, val safeCode: String) : ManagedAgentRunEvent
    data class Rejected(val safeCode: String) : ManagedAgentRunEvent
}

interface ManagedAgentRunUseCase {
    fun observeRuns(agentId: String): Flow<List<AgentRun>>
    fun runAgent(agentId: String, prompt: String, idempotencyKey: String): Flow<ManagedAgentRunEvent>
    suspend fun resolveApproval(approvalId: String, approved: Boolean): Boolean
}
