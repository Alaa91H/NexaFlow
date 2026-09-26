package com.nexaflow.core.automationcontrol

data class AutomationAuditEvent(
    val eventType: String,
    val outcome: String,
    val actorId: String,
    val agentId: String? = null,
    val automationId: String? = null,
    val requestId: String? = null,
    val transport: String? = null,
    /**
     * Machine-generated redacted detail only. Callers must never place raw
     * configs, credentials, secret values or model prompts here.
     */
    val details: Map<String, String> = emptyMap(),
    val createdAt: Long
)

fun interface AutomationAuditSink {
    suspend fun record(event: AutomationAuditEvent)

    companion object {
        val NO_OP = AutomationAuditSink { }
    }
}
