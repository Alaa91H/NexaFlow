package com.nexaflow.core.agentapi

import kotlinx.serialization.Serializable

@Serializable
enum class AgentEventTypeV1 {
    AUTOMATION_CREATED,
    AUTOMATION_UPDATED,
    AUTOMATION_DELETED,
    AUTOMATION_TRIGGERED,
    AUTOMATION_COMPLETED,
    AUTOMATION_FAILED,
    CAPABILITY_CHANGED,
    DEVICE_STATE_CHANGED,
    AGENT_CONNECTED,
    AGENT_DISCONNECTED
}

@Serializable
data class AgentEventV1(
    val streamId: String,
    val sequence: Long,
    val type: AgentEventTypeV1,
    val occurredAt: Long,
    val automationId: String? = null,
    val agentId: String? = null,
    val executionId: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class AgentEventBatchV1(
    val streamId: String,
    val events: List<AgentEventV1>,
    val oldestSequence: Long,
    val latestSequence: Long,
    val reset: Boolean = false,
    val truncated: Boolean = false
)

data class AgentEventQuery(
    val streamId: String? = null,
    val afterSequence: Long = 0L,
    val limit: Int = 50,
    val waitMs: Long = 0L
)
