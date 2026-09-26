package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "automation_api_metadata",
    indices = [
        Index(name = "index_automation_api_metadata_origin", value = ["origin"]),
        Index(name = "index_automation_api_metadata_lastAgentId", value = ["lastAgentId"]),
        Index(name = "index_automation_api_metadata_requestId", value = ["requestId"])
    ]
)
data class AutomationApiMetadataEntity(
    @androidx.room.PrimaryKey val automationId: String,
    /** Creation provenance. This never changes when another actor later edits the task. */
    val origin: String,
    val creatorActorId: String,
    val creatorAgentId: String? = null,
    val lastActorId: String,
    val lastAgentId: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val transport: String? = null,
    val requestId: String? = null,
    val conversationId: String? = null,
    val riskLevel: String? = null,
    /** API revision used for optimistic concurrency. Starts at one. */
    val revision: Long,
    /** Mirrors Automation.updatedAt so out-of-band first-party edits can be detected. */
    val definitionUpdatedAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "agent_audit",
    indices = [
        Index(name = "index_agent_audit_createdAt", value = ["createdAt"]),
        Index(name = "index_agent_audit_agentId", value = ["agentId"]),
        Index(name = "index_agent_audit_automationId", value = ["automationId"]),
        Index(name = "index_agent_audit_requestId", value = ["requestId"])
    ]
)
data class AgentAuditEntity(
    @androidx.room.PrimaryKey val id: String,
    val eventType: String,
    val outcome: String,
    val actorId: String,
    val agentId: String? = null,
    val automationId: String? = null,
    val requestId: String? = null,
    val transport: String? = null,
    /** Redacted, bounded JSON only. Never store credentials, tokens or secret values. */
    val detailsJson: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "agent_idempotency",
    primaryKeys = ["actorId", "keyHash"],
    indices = [
        Index(name = "index_agent_idempotency_expiresAt", value = ["expiresAt"]),
        Index(name = "index_agent_idempotency_automationId", value = ["automationId"])
    ]
)
data class AgentIdempotencyEntity(
    val actorId: String,
    /** SHA-256 of the caller-provided idempotency key; the raw key is never persisted. */
    val keyHash: String,
    val requestFingerprint: String,
    val operation: String,
    val automationId: String? = null,
    val resultRevision: Long? = null,
    val createdAt: Long,
    val expiresAt: Long
)
