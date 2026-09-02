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

/**
 * Small, bounded checkpoint record. It stores metadata and idempotency markers,
 * never action payloads, plaintext secrets, Android objects or command output.
 */
@Serializable
data class DurableExecutionCheckpoint(
    val runId: String,
    val automationId: String,
    val totalActions: Int,
    val nextActionIndex: Int,
    val completedActionIndexes: Set<Int> = emptySet(),
    val idempotencyKeys: Set<String> = emptySet(),
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
