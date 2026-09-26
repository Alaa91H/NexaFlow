package com.nexaflow.core.agentapi

import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.serialization.Serializable

@Serializable
data class AgentApiErrorV1(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class AgentApiErrorEnvelopeV1(
    val error: AgentApiErrorV1
)

@Serializable
data class AgentApiStatusV1(
    val apiVersion: String = "v1",
    val transport: String = "LOCAL_REST",
    val loopbackOnly: Boolean = true,
    val accessEnabled: Boolean,
    val activeAgentCount: Int,
    val activeSessionCount: Int,
    val pendingPairingCount: Int,
    val port: Int
)

@Serializable
data class AgentApiTaskRecordV1(
    val id: String,
    val revision: Long,
    val task: AgentTaskDraftV1
)

@Serializable
data class AgentApiTaskMutationRequestV1(
    val task: AgentTaskDraftV1,
    val providerId: String? = null,
    val modelId: String? = null,
    val conversationId: String? = null,
    val requestId: String? = null,
    val riskLevel: String? = null,
    val requireExecutable: Boolean = true
)

@Serializable
data class AgentApiValidateRequestV1(
    val task: AgentTaskDraftV1,
    val existingAutomationId: String? = null
)

@Serializable
data class AgentApiValidationIssueV1(
    val code: String,
    val path: String,
    val message: String
)

@Serializable
data class AgentApiValidationV1(
    val valid: Boolean,
    val executable: Boolean,
    val summary: String,
    val issues: List<AgentApiValidationIssueV1> = emptyList()
)

@Serializable
data class AgentApiPairingCompletionRequestV1(
    val challengeId: String,
    val challengeSecret: String
)

@Serializable
data class AgentApiBootstrapCredentialV1(
    val agentId: String,
    val refreshToken: String
)

@Serializable
data class AgentApiSessionRequestV1(
    val refreshToken: String,
    val transportKeyFingerprint: String? = null
)

@Serializable
data class AgentApiSessionV1(
    val agentId: String,
    val accessToken: String,
    val rotatedRefreshToken: String,
    val expiresAt: Long
)

@Serializable
data class AgentApiActionResultV1(
    val actionType: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long,
    val channel: String? = null,
    val errorCode: String? = null,
    val verificationAttempted: Boolean = false,
    val verified: Boolean? = null,
    val outcomeUncertain: Boolean = false
)

@Serializable
data class AgentApiRunReplayV1(
    val idempotentReplay: Boolean = true,
    val automationId: String,
    val revision: Long,
    val message: String = "Run was already accepted for this idempotency key"
)

@Serializable
data class AgentApiExecutionV1(
    val id: String,
    val automationId: String,
    val automationName: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long,
    val channel: String? = null,
    val actionResults: List<AgentApiActionResultV1> = emptyList()
)

@Serializable
data class AgentApiAuditEventV1(
    val id: String,
    val eventType: String,
    val outcome: String,
    val actorId: String,
    val agentId: String? = null,
    val automationId: String? = null,
    val requestId: String? = null,
    val transport: String? = null,
    val detailsJson: String? = null,
    val createdAt: Long
)

@Serializable
data class AgentApiBackendAvailabilityV1(
    val backend: String,
    val availability: String,
    val reason: String? = null
)

@Serializable
data class AgentApiCapabilityV1(
    val id: String,
    val availability: String,
    val reason: String? = null,
    val backends: List<AgentApiBackendAvailabilityV1> = emptyList()
)

@Serializable
data class AgentApiPrivilegeV1(
    val surface: String,
    val key: String,
    val state: String,
    val detailCode: String
)

@Serializable
data class AgentApiCapabilitiesV1(
    val observedAtMillis: Long,
    val neverObserved: Boolean,
    val capabilities: List<AgentApiCapabilityV1>,
    val privilegeObservedAtMillis: Long,
    val privileges: List<AgentApiPrivilegeV1>
)

internal fun ExecutionRecord.toAgentApiModel(): AgentApiExecutionV1 =
    AgentApiExecutionV1(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt,
        channel = channel,
        actionResults = actionResults.map(ActionExecutionResult::toAgentApiModel)
    )

private fun ActionExecutionResult.toAgentApiModel(): AgentApiActionResultV1 =
    AgentApiActionResultV1(
        actionType = actionType,
        success = success,
        message = message,
        durationMs = durationMs,
        channel = channel,
        errorCode = errorCode,
        verificationAttempted = verificationAttempted,
        verified = verified,
        outcomeUncertain = outcomeUncertain
    )
