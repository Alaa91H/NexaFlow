package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.Index

/** Agent prompt/policy metadata only; provider credentials remain in credential storage. */
@Entity(
    tableName = "agent_definitions",
    indices = [
        Index(name = "index_agent_definitions_enabled_updatedAtMillis", value = ["enabled", "updatedAtMillis"])
    ]
)
data class AgentDefinitionEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemInstructions: String,
    val providerProfileId: String?,
    val modelId: String?,
    val policyJson: String,
    val budgetJson: String,
    val enabled: Boolean,
    val revision: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
