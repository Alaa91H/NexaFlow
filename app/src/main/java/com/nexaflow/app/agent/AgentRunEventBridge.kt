package com.nexaflow.app.agent

import com.nexaflow.core.agentapi.AgentEventHub
import com.nexaflow.core.agentapi.AgentEventTypeV1
import com.nexaflow.core.execution.AutomationRunListener
import com.nexaflow.core.logging.SecretRedactor
import com.nexaflow.domain.models.ExecutionRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges run lifecycle observations into the agent event stream, so local
 * agents can wake on failures instead of polling history.
 *
 * History messages are redacted and bounded before publication; the full
 * record stays available through the authenticated history API.
 */
@Singleton
class AgentRunEventBridge @Inject constructor(
    private val eventHub: AgentEventHub
) : AutomationRunListener {

    override suspend fun onTriggered(automationId: String, runId: String) {
        eventHub.publish(
            type = AgentEventTypeV1.AUTOMATION_TRIGGERED,
            automationId = automationId,
            executionId = runId
        )
    }

    override suspend fun onRecord(record: ExecutionRecord) {
        eventHub.publish(
            type = if (record.success) {
                AgentEventTypeV1.AUTOMATION_COMPLETED
            } else {
                AgentEventTypeV1.AUTOMATION_FAILED
            },
            automationId = record.automationId,
            executionId = record.id,
            attributes = mapOf(
                "success" to record.success.toString(),
                "reason" to reasonOf(record.message)
            )
        )
    }

    private fun reasonOf(message: String): String {
        val singleLine = message.replace('\n', ' ').replace('\r', ' ').trim()
        val redacted = SecretRedactor.redact(singleLine).orEmpty()
        return redacted.take(MAX_REASON_CHARS)
    }

    private companion object {
        const val MAX_REASON_CHARS = 256
    }
}
