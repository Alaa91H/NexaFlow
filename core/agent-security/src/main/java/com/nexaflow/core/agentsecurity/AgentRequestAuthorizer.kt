package com.nexaflow.core.agentsecurity

enum class AgentOperation(
    val requiredScopes: Set<AgentScope>
) {
    STATUS(setOf(AgentScope.CATALOG_READ)),
    CATALOG_READ(setOf(AgentScope.CATALOG_READ)),
    TASK_LIST(setOf(AgentScope.TASKS_READ)),
    TASK_GET(setOf(AgentScope.TASKS_READ)),
    TASK_CREATE(setOf(AgentScope.TASKS_CREATE)),
    TASK_UPDATE(setOf(AgentScope.TASKS_UPDATE)),
    TASK_DELETE(setOf(AgentScope.TASKS_DELETE)),
    TASK_ENABLE(setOf(AgentScope.TASKS_ENABLE)),
    TASK_DISABLE(setOf(AgentScope.TASKS_ENABLE)),
    TASK_RUN(setOf(AgentScope.TASKS_RUN)),
    HISTORY_READ(setOf(AgentScope.HISTORY_READ)),
    NETWORK_REQUEST(setOf(AgentScope.NETWORK_REQUEST)),
    PLUGIN_USE(setOf(AgentScope.PLUGINS_USE)),
    ELEVATED_REQUEST(setOf(AgentScope.ELEVATED_REQUEST)),
    SECRET_REFERENCE(setOf(AgentScope.SECRETS_REFERENCE))
}

/**
 * One scope matrix shared by every future transport adapter. No REST/MCP/A2A
 * adapter should map its own operation-to-scope policy.
 */
class AgentRequestAuthorizer(
    private val accessManager: AgentAccessManager
) {
    suspend fun authorize(
        accessToken: String,
        operation: AgentOperation,
        presentedBinding: AgentIdentityBinding = AgentIdentityBinding()
    ): AgentAuthorizationResult = accessManager.authorize(
        accessToken = accessToken,
        requiredScopes = operation.requiredScopes,
        presentedBinding = presentedBinding
    )
}
