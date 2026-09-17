package com.nexaflow.core.datastore

import kotlinx.serialization.Serializable

/** Lifecycle persisted for a run that may outlive the app process. */
@Serializable
enum class DurableExecutionStatus {
    STARTED,
    ACTION_STARTED,
    ACTION_COMPLETED,
    ACTION_UNKNOWN,
    EXIT_PENDING,
    COMPLETED,
    RECOVERY_CLAIMED,
    RECOVERY_REQUIRED
}

/**
 * Explicit transition contract for the durable checkpoint state machine.
 * Recovery claims are intentionally one-way until the coordinator resolves
 * the uncertain run; terminal completion cannot be reopened.
 */
fun DurableExecutionStatus.canTransitionTo(next: DurableExecutionStatus): Boolean = when (this) {
    DurableExecutionStatus.STARTED -> next in setOf(
        DurableExecutionStatus.ACTION_STARTED,
        DurableExecutionStatus.EXIT_PENDING,
        DurableExecutionStatus.RECOVERY_CLAIMED
    )
    DurableExecutionStatus.ACTION_STARTED -> next in setOf(
        DurableExecutionStatus.ACTION_COMPLETED,
        DurableExecutionStatus.ACTION_UNKNOWN,
        DurableExecutionStatus.EXIT_PENDING,
        DurableExecutionStatus.RECOVERY_CLAIMED
    )
    DurableExecutionStatus.ACTION_COMPLETED -> next in setOf(
        DurableExecutionStatus.ACTION_STARTED,
        DurableExecutionStatus.COMPLETED,
        DurableExecutionStatus.EXIT_PENDING,
        DurableExecutionStatus.RECOVERY_CLAIMED
    )
    DurableExecutionStatus.ACTION_UNKNOWN -> next in setOf(
        DurableExecutionStatus.RECOVERY_CLAIMED,
        DurableExecutionStatus.RECOVERY_REQUIRED
    )
    DurableExecutionStatus.EXIT_PENDING -> next in setOf(
        DurableExecutionStatus.COMPLETED,
        DurableExecutionStatus.RECOVERY_CLAIMED,
        DurableExecutionStatus.RECOVERY_REQUIRED
    )
    DurableExecutionStatus.RECOVERY_CLAIMED -> next == DurableExecutionStatus.RECOVERY_REQUIRED
    DurableExecutionStatus.RECOVERY_REQUIRED -> next == DurableExecutionStatus.RECOVERY_REQUIRED
    DurableExecutionStatus.COMPLETED -> next == DurableExecutionStatus.COMPLETED
}

/** Verification state persisted for a node whose side effect may be uncertain. */
@Serializable
enum class DurableVerificationState {
    NOT_REQUIRED,
    PENDING,
    VERIFIED,
    FAILED,
    UNKNOWN
}

/** Immutable metadata for one durable node/action attempt. */
@Serializable
data class DurableNodeExecution(
    val nodeId: String,
    val attempt: Int,
    val state: DurableNodeExecutionState,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val backend: String? = null,
    val idempotencyKey: String? = null,
    val inputHash: String? = null,
    val outputHash: String? = null,
    val verificationState: DurableVerificationState = DurableVerificationState.UNKNOWN,
    val failureCode: String? = null
) {
    init {
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
        require(attempt > 0) { "attempt must be positive" }
        require(startedAt == null || startedAt >= 0L) { "startedAt must not be negative" }
        require(completedAt == null || completedAt >= 0L) { "completedAt must not be negative" }
    }
}

@Serializable
enum class DurableNodeExecutionState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    RECOVERY_REQUIRED
}

/**
 * Small, bounded checkpoint record. It stores metadata and idempotency markers,
 * never action payloads, plaintext secrets, Android objects or command output.
 */
@Serializable
data class DurableExecutionCheckpoint(
    val runId: String,
    val automationId: String,
    /** Stable workflow identity and revision captured at execution admission. */
    val workflowId: String = automationId,
    val workflowVersion: Int = 1,
    val parentRunId: String? = null,
    val correlationId: String? = null,
    val causationId: String? = null,
    val deadlineAt: Long? = null,
    val currentNodeId: String? = null,
    val totalActions: Int,
    val nextActionIndex: Int,
    val completedActionIndexes: Set<Int> = emptySet(),
    val idempotencyKeys: Set<String> = emptySet(),
    val nodeExecutions: List<DurableNodeExecution> = emptyList(),
    val verificationState: DurableVerificationState = DurableVerificationState.UNKNOWN,
    val checkpointVersion: Int = 1,
    val status: DurableExecutionStatus,
    /** Original state preserved when a recovery worker atomically claims this run. */
    val recoverySourceStatus: DurableExecutionStatus? = null,
    val startedAt: Long,
    val updatedAt: Long,
    val message: String? = null,
    val schemaVersion: Int = 1
) {
    init {
        require(runId.isNotBlank()) { "runId must not be blank" }
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(workflowId.isNotBlank()) { "workflowId must not be blank" }
        require(workflowVersion > 0) { "workflowVersion must be positive" }
        require(parentRunId == null || parentRunId.isNotBlank()) { "parentRunId must not be blank" }
        require(correlationId == null || correlationId.isNotBlank()) { "correlationId must not be blank" }
        require(causationId == null || causationId.isNotBlank()) { "causationId must not be blank" }
        require(deadlineAt == null || deadlineAt >= startedAt) { "deadlineAt must not precede startedAt" }
        require(nodeExecutions.size <= MAX_NODE_EXECUTIONS) {
            "nodeExecutions exceeds the bounded checkpoint limit"
        }
        require(nodeExecutions.map { it.nodeId to it.attempt }.distinct().size == nodeExecutions.size) {
            "nodeExecutions must not contain duplicate node attempts"
        }
        require(checkpointVersion > 0) { "checkpointVersion must be positive" }
        require(totalActions >= 0) { "totalActions must not be negative" }
        require(nextActionIndex in 0..totalActions) { "nextActionIndex is outside action range" }
        require(completedActionIndexes.all { it in 0 until totalActions }) {
            "completedActionIndexes contains an invalid index"
        }
        require(idempotencyKeys.all { it.isNotBlank() && it.length <= MAX_IDEMPOTENCY_KEY_LENGTH }) {
            "idempotencyKeys contains an invalid key"
        }
        require(schemaVersion == 1) { "Unsupported checkpoint schema version" }
    }

    val isTerminal: Boolean
        get() = status == DurableExecutionStatus.COMPLETED

    companion object {
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 256
        const val MAX_NODE_EXECUTIONS = 512
    }
}

/**
 * Bounded proof that one recurring-maintenance occurrence completed. It stores
 * no task payload or action output and is retained only to suppress duplicate
 * delivery of the same local maintenance window.
 */
@Serializable
data class MaintenanceOccurrenceReceipt(
    val occurrenceKey: String,
    val automationId: String,
    val completedAt: Long
) {
    init {
        require(occurrenceKey.startsWith("maintenance:")) { "Invalid maintenance occurrence key" }
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(completedAt >= 0L) { "completedAt must not be negative" }
    }
}
