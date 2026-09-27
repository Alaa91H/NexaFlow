package com.nexaflow.app.agent

import com.nexaflow.core.agentapi.AgentEventHub
import com.nexaflow.core.agentapi.AgentEventTypeV1
import com.nexaflow.core.automationcontrol.AutomationMutationContext
import com.nexaflow.core.automationcontrol.AutomationMutationKind
import com.nexaflow.core.automationcontrol.AutomationMutationObserver
import com.nexaflow.domain.models.Automation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges committed definition mutations into the agent event stream.
 *
 * Every transport (UI, MCP, REST, A2A, Binder, relay, import) funnels
 * through [com.nexaflow.core.automationcontrol.AutomationCommandService],
 * so this single observer gives subscribers lifecycle coverage without any
 * producer scattered across call sites. [AgentEventHub] already bounds and
 * sanitizes everything published.
 */
@Singleton
class AgentAutomationEventBridge @Inject constructor(
    private val eventHub: AgentEventHub
) : AutomationMutationObserver {

    override suspend fun onCommitted(
        kind: AutomationMutationKind,
        automation: Automation,
        revision: Long,
        context: AutomationMutationContext
    ) {
        val type = when (kind) {
            AutomationMutationKind.CREATE -> AgentEventTypeV1.AUTOMATION_CREATED
            AutomationMutationKind.UPDATE,
            AutomationMutationKind.ENABLE,
            AutomationMutationKind.DISABLE -> AgentEventTypeV1.AUTOMATION_UPDATED
            AutomationMutationKind.DELETE -> AgentEventTypeV1.AUTOMATION_DELETED
        }
        eventHub.publish(
            type = type,
            automationId = automation.id,
            agentId = context.agentId,
            attributes = mapOf(
                "revision" to revision.toString(),
                "origin" to context.origin.name,
                "transport" to context.transport
            )
        )
    }
}
