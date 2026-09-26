package com.nexaflow.core.agentapi

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord

/**
 * App-owned runtime bridges required by the transport layer.
 *
 * The REST controller never reaches Room or ExecutionEngine directly. Android
 * wiring supplies these operations while task mutation remains exclusively in
 * AutomationCommandService.
 */
interface AgentApiRuntime {
    suspend fun effectiveRevision(automationId: String): Long?
    suspend fun onEnabled(automation: Automation)
    suspend fun onDisabled(automation: Automation)
    suspend fun prepareForDeletion(automation: Automation): Boolean
    suspend fun run(automation: Automation, request: AgentApiRunContext): ExecutionRecord
    suspend fun latestHistory(limit: Int): List<ExecutionRecord>
    suspend fun latestAudit(limit: Int): List<AgentApiAuditEventV1>
    suspend fun capabilities(): AgentApiCapabilitiesV1
}

data class AgentApiRunContext(
    val actorId: String,
    val agentId: String,
    val requestId: String?
)
