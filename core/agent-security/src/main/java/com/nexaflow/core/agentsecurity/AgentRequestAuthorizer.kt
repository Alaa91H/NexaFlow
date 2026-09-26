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
 * One scope/abuse-control matrix shared by every future transport adapter.
 * REST/MCP/A2A/Binder adapters must not maintain independent authorization,
 * payload-size, or request-rate policies.
 *
 * Rate state is intentionally process-local defense in depth. Long-lived
 * authority remains in [AgentAccessManager]; transport-facing adapters may add
 * stricter connection-level limits but may never relax these bounds.
 */
class AgentRequestAuthorizer(
    private val accessManager: AgentAccessManager,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    private val maxRequestsPerWindow: Int = DEFAULT_MAX_REQUESTS_PER_WINDOW,
    private val rateWindowMs: Long = DEFAULT_RATE_WINDOW_MS
) {
    private data class RateWindow(
        val startedAt: Long,
        val count: Int
    )

    private val rateLock = Any()
    private val rateWindows = LinkedHashMap<String, RateWindow>()

    init {
        require(maxPayloadBytes > 0) { "maxPayloadBytes must be positive" }
        require(maxRequestsPerWindow > 0) { "maxRequestsPerWindow must be positive" }
        require(rateWindowMs > 0L) { "rateWindowMs must be positive" }
    }

    suspend fun authorize(
        accessToken: String,
        operation: AgentOperation,
        presentedBinding: AgentIdentityBinding = AgentIdentityBinding(),
        payloadBytes: Int = 0
    ): AgentAuthorizationResult {
        if (payloadBytes < 0 || payloadBytes > maxPayloadBytes) {
            return AgentAuthorizationResult.PayloadTooLarge
        }

        val authorization = accessManager.authorize(
            accessToken = accessToken,
            requiredScopes = operation.requiredScopes,
            presentedBinding = presentedBinding
        )
        if (authorization !is AgentAuthorizationResult.Authorized) {
            return authorization
        }

        return if (consumeRateBudget(authorization.agentId)) {
            authorization
        } else {
            AgentAuthorizationResult.RateLimited
        }
    }

    private fun consumeRateBudget(agentId: String): Boolean {
        val now = clockMillis()
        return synchronized(rateLock) {
            // Keep the process-local map bounded even if grants are revoked
            // while the process stays alive.
            val staleBefore = now - rateWindowMs
            rateWindows.entries.removeAll { (_, window) ->
                window.startedAt <= staleBefore
            }

            val current = rateWindows[agentId]
            when {
                current == null || now - current.startedAt >= rateWindowMs -> {
                    rateWindows[agentId] = RateWindow(now, 1)
                    true
                }
                current.count >= maxRequestsPerWindow -> false
                else -> {
                    rateWindows[agentId] = current.copy(count = current.count + 1)
                    true
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_PAYLOAD_BYTES = 256 * 1024
        const val DEFAULT_MAX_REQUESTS_PER_WINDOW = 120
        const val DEFAULT_RATE_WINDOW_MS = 60_000L
    }
}
