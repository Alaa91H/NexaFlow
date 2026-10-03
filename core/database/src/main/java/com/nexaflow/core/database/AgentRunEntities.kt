package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "agent_runs",
    indices = [
        Index(value = ["agentId", "idempotencyKeyHash"], unique = true),
        Index(value = ["agentId", "createdAtMillis"]),
        Index(value = ["status", "updatedAtMillis"])
    ]
)
data class AgentRunEntity(
    @androidx.room.PrimaryKey val id: String,
    val agentId: String,
    val idempotencyKeyHash: String,
    val requestFingerprint: String,
    val definitionRevision: Long,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deadlineAtMillis: Long,
    val providerId: String?,
    val modelId: String?,
    val outcomeCode: String?,
    val turns: Int,
    val toolCalls: Int,
    val outputCharacters: Int,
    val costMicros: Long,
    val revision: Long
)

@Entity(
    tableName = "agent_run_events",
    primaryKeys = ["runId", "sequence"],
    indices = [Index(value = ["runId", "createdAtMillis"])]
)
data class AgentRunEventEntity(
    val runId: String,
    val sequence: Long,
    val id: String,
    val type: String,
    /** Low-cardinality allowlisted code only. Never persist prompts or tool payloads. */
    val safeCode: String,
    val createdAtMillis: Long
)

@Entity(
    tableName = "agent_approvals",
    indices = [
        Index(value = ["runId", "createdAtMillis"]),
        Index(value = ["agentId", "resolvedAtMillis"])
    ]
)
data class AgentApprovalEntity(
    @androidx.room.PrimaryKey val id: String,
    val runId: String,
    val agentId: String,
    val definitionRevision: Long,
    val toolName: String,
    val callFingerprint: String,
    val deviceBindingHash: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val decision: String?,
    val resolvedAtMillis: Long?
)
