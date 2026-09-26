package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentAuthorizationResult
import com.nexaflow.core.agentsecurity.AgentIdentityBinding
import com.nexaflow.core.agentsecurity.AgentOperation
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.agentsecurity.AgentSessionIssueResult
import com.nexaflow.core.automationcontrol.AutomationMutationContext
import com.nexaflow.core.automationcontrol.AutomationMutationOrigin
import com.nexaflow.core.automationcontrol.AutomationMutationResult
import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.api.AgentTaskMapper
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewRequestV1
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewResult
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schema.AutomationSchemaRegistry
import com.nexaflow.core.automationcontrol.simulation.AgentSimulationRequestV1
import com.nexaflow.core.automationcontrol.simulation.AgentSimulationService
import com.nexaflow.domain.repositories.AutomationRepository
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AgentApiController(
    private val repository: AutomationRepository,
    private val commandService: AutomationCommandService,
    private val simulationService: AgentSimulationService,
    private val schedulePreviewService: AgentSchedulePreviewService,
    private val schemaRegistry: AutomationSchemaRegistry,
    private val accessManager: AgentAccessManager,
    private val authorizer: AgentRequestAuthorizer,
    private val runtime: AgentApiRuntime,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }
) {
    suspend fun handle(request: AgentHttpRequest): AgentHttpResponse {
        val uri = runCatching { URI(request.target) }.getOrNull()
            ?: return error(400, "invalid_target", "Request target is invalid")
        val path = uri.path ?: "/"

        if (request.header("origin") != null) {
            return error(403, "browser_origin_rejected", "Browser-originated requests are not accepted")
        }
        if (!validHost(request.header("host"))) {
            return error(400, "invalid_host", "Host must be loopback")
        }

        if (request.method == "GET" && path == "/api/v1/openapi.json") {
            return rawJson(200, AgentApiDocuments.openApiJson)
        }
        if (request.method == "GET" && path == "/api/v1/schemas/task-v1.json") {
            return rawJson(200, AgentApiDocuments.taskSchemaJson)
        }
        if (request.method == "POST" && path == "/api/v1/auth/session") {
            return exchangeSession(request)
        }

        val operation = operationFor(request.method, path)
            ?: return error(404, "not_found", "API route was not found")
        val principal = authorize(request, operation)
        if (principal.response != null) return principal.response

        return try {
            dispatch(
                request = request,
                uri = uri,
                path = path,
                operation = operation,
                agentId = checkNotNull(principal.agentId)
            )
        } catch (_: SerializationException) {
            error(400, "invalid_json", "Request JSON does not match the API schema")
        } catch (_: IllegalArgumentException) {
            error(400, "invalid_request", "Request is invalid")
        }
    }

    private suspend fun dispatch(
        request: AgentHttpRequest,
        uri: URI,
        path: String,
        operation: AgentOperation,
        agentId: String
    ): AgentHttpResponse {
        val taskId = taskId(path)
        return when {
            request.method == "GET" && path == "/api/v1/status" -> {
                val status = accessManager.status()
                respond(
                    200,
                    AgentApiStatusV1(
                        accessEnabled = status.accessEnabled,
                        activeAgentCount = status.activeAgentCount,
                        activeSessionCount = status.activeSessionCount,
                        pendingPairingCount = status.pendingPairingCount,
                        port = AgentApiServer.currentPort
                    )
                )
            }
            request.method == "GET" && path == "/api/v1/capabilities" ->
                respond(200, runtime.capabilities())
            request.method == "GET" && path == "/api/v1/catalog" ->
                respond(200, schemaRegistry.snapshot())
            request.method == "GET" && path == "/api/v1/tasks" -> listTasks()
            request.method == "GET" && taskId != null && path == "/api/v1/tasks/$taskId" ->
                getTask(taskId)
            request.method == "POST" && path == "/api/v1/tasks" ->
                createTask(request, agentId)
            request.method in setOf("PATCH", "PUT") &&
                taskId != null && path == "/api/v1/tasks/$taskId" ->
                updateTask(taskId, request, agentId)
            request.method == "DELETE" &&
                taskId != null && path == "/api/v1/tasks/$taskId" ->
                deleteTask(taskId, request, agentId)
            request.method == "POST" && taskId != null &&
                path == "/api/v1/tasks/$taskId/enable" ->
                setEnabled(taskId, true, request, agentId)
            request.method == "POST" && taskId != null &&
                path == "/api/v1/tasks/$taskId/disable" ->
                setEnabled(taskId, false, request, agentId)
            request.method == "POST" && taskId != null &&
                path == "/api/v1/tasks/$taskId/run" ->
                runTask(taskId, request, agentId)
            request.method == "POST" && path == "/api/v1/validate" ->
                validate(request)
            request.method == "POST" && path == "/api/v1/simulate" ->
                respond(200, simulationService.simulate(decode(request)))
            request.method == "POST" && path == "/api/v1/schedules/preview" ->
                previewSchedule(request)
            request.method == "GET" && path == "/api/v1/history" ->
                respond(200, runtime.latestHistory(limitFrom(uri)).map { it.toAgentApiModel() })
            request.method == "GET" && path == "/api/v1/audit" ->
                respond(200, runtime.latestAudit(limitFrom(uri)))
            else -> error(404, "not_found", "API route was not found")
        }
    }

    private suspend fun exchangeSession(request: AgentHttpRequest): AgentHttpResponse {
        val body = try {
            decode<AgentApiSessionRequestV1>(request)
        } catch (_: Exception) {
            return error(400, "invalid_json", "Session request is invalid")
        }
        if (body.refreshToken.isBlank()) {
            return error(400, "invalid_refresh_token", "Refresh token must not be blank")
        }
        val binding = AgentIdentityBinding(
            transportKeyFingerprint = body.transportKeyFingerprint?.takeIf(String::isNotBlank)
        )
        return when (
            val result = accessManager.exchangeRefreshToken(body.refreshToken, binding)
        ) {
            is AgentSessionIssueResult.Issued -> respond(
                200,
                AgentApiSessionV1(
                    agentId = result.credential.agentId,
                    accessToken = result.credential.accessToken,
                    rotatedRefreshToken = result.credential.rotatedRefreshToken,
                    expiresAt = result.credential.expiresAt
                )
            )
            AgentSessionIssueResult.Disabled ->
                error(403, "agent_access_disabled", "AI Agent Access is disabled")
            AgentSessionIssueResult.BindingMismatch ->
                error(401, "binding_mismatch", "Agent transport identity does not match")
            AgentSessionIssueResult.Revoked ->
                error(401, "agent_revoked", "Agent grant was revoked")
            AgentSessionIssueResult.InvalidToken ->
                error(401, "invalid_refresh_token", "Refresh token is invalid")
        }
    }

    private suspend fun authorize(
        request: AgentHttpRequest,
        operation: AgentOperation
    ): AuthorizedRequest {
        val authorization = request.header("authorization").orEmpty()
        val token = authorization
            .takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            .orEmpty()
        if (token.isBlank()) {
            return AuthorizedRequest(
                response = error(401, "missing_bearer_token", "Bearer access token is required")
            )
        }
        val binding = AgentIdentityBinding(
            transportKeyFingerprint = request.header("x-nexaflow-transport-key")
                ?.takeIf(String::isNotBlank)
        )
        return when (
            val result = authorizer.authorize(
                accessToken = token,
                operation = operation,
                presentedBinding = binding,
                payloadBytes = request.body.size
            )
        ) {
            is AgentAuthorizationResult.Authorized ->
                AuthorizedRequest(agentId = result.agentId)
            AgentAuthorizationResult.Disabled ->
                AuthorizedRequest(response = error(403, "agent_access_disabled", "AI Agent Access is disabled"))
            AgentAuthorizationResult.InvalidToken ->
                AuthorizedRequest(response = error(401, "invalid_access_token", "Access token is invalid"))
            AgentAuthorizationResult.Expired ->
                AuthorizedRequest(response = error(401, "access_token_expired", "Access token expired"))
            AgentAuthorizationResult.Revoked ->
                AuthorizedRequest(response = error(401, "agent_revoked", "Agent grant was revoked"))
            AgentAuthorizationResult.BindingMismatch ->
                AuthorizedRequest(response = error(401, "binding_mismatch", "Agent transport identity does not match"))
            AgentAuthorizationResult.ScopeDenied ->
                AuthorizedRequest(response = error(403, "scope_denied", "Agent scope does not allow this operation"))
            AgentAuthorizationResult.PayloadTooLarge ->
                AuthorizedRequest(response = error(413, "payload_too_large", "Request payload is too large"))
            AgentAuthorizationResult.RateLimited ->
                AuthorizedRequest(response = error(429, "rate_limited", "Agent request rate limit exceeded"))
        }
    }

    private suspend fun listTasks(): AgentHttpResponse {
        val tasks = repository.getAutomations().first().map { automation ->
            AgentApiTaskRecordV1(
                id = automation.id,
                revision = runtime.effectiveRevision(automation.id) ?: 1L,
                task = AgentTaskMapper.fromAutomation(automation)
            )
        }
        return respond(200, tasks)
    }

    private suspend fun getTask(id: String): AgentHttpResponse {
        val automation = repository.getAutomationById(id)
            ?: return error(404, "task_not_found", "Task was not found")
        val revision = runtime.effectiveRevision(id) ?: 1L
        return respond(
            200,
            AgentApiTaskRecordV1(id, revision, AgentTaskMapper.fromAutomation(automation)),
            mapOf("ETag" to quoteRevision(revision))
        )
    }

    private suspend fun createTask(
        request: AgentHttpRequest,
        agentId: String
    ): AgentHttpResponse {
        val idempotency = requiredIdempotency(request) ?: return missingIdempotency()
        val body = decode<AgentApiTaskMutationRequestV1>(request)
        val result = commandService.create(
            body.task,
            body.context(agentId, idempotency, expectedRevision = null)
        )
        return mutationResponse(result, created = true)
    }

    private suspend fun updateTask(
        id: String,
        request: AgentHttpRequest,
        agentId: String
    ): AgentHttpResponse {
        val idempotency = requiredIdempotency(request) ?: return missingIdempotency()
        val revision = requiredRevision(request) ?: return missingRevision()
        val body = decode<AgentApiTaskMutationRequestV1>(request)
        val result = commandService.update(
            id,
            body.task,
            body.context(agentId, idempotency, revision)
        )
        return mutationResponse(result)
    }

    private suspend fun deleteTask(
        id: String,
        request: AgentHttpRequest,
        agentId: String
    ): AgentHttpResponse {
        val idempotency = requiredIdempotency(request) ?: return missingIdempotency()
        val revision = requiredRevision(request) ?: return missingRevision()
        return mutationResponse(
            commandService.delete(
                id,
                AutomationMutationContext(
                    actorId = actorId(agentId),
                    origin = AutomationMutationOrigin.AGENT,
                    agentId = agentId,
                    expectedRevision = revision,
                    transport = TRANSPORT,
                    requestId = request.header("x-request-id"),
                    idempotencyKey = idempotency
                )
            )
        )
    }

    private suspend fun setEnabled(
        id: String,
        enabled: Boolean,
        request: AgentHttpRequest,
        agentId: String
    ): AgentHttpResponse {
        val idempotency = requiredIdempotency(request) ?: return missingIdempotency()
        val revision = requiredRevision(request) ?: return missingRevision()
        return mutationResponse(
            commandService.setEnabled(
                id,
                enabled,
                AutomationMutationContext(
                    actorId = actorId(agentId),
                    origin = AutomationMutationOrigin.AGENT,
                    agentId = agentId,
                    expectedRevision = revision,
                    transport = TRANSPORT,
                    requestId = request.header("x-request-id"),
                    idempotencyKey = idempotency
                )
            )
        )
    }

    private suspend fun runTask(
        id: String,
        request: AgentHttpRequest,
        agentId: String
    ): AgentHttpResponse {
        val automation = repository.getAutomationById(id)
            ?: return error(404, "task_not_found", "Task was not found")
        val record = runtime.run(
            automation,
            AgentApiRunContext(
                actorId = actorId(agentId),
                agentId = agentId,
                requestId = request.header("x-request-id")
            )
        )
        return respond(200, record.toAgentApiModel())
    }

    private suspend fun validate(request: AgentHttpRequest): AgentHttpResponse {
        val body = decode<AgentApiValidateRequestV1>(request)
        val report = commandService.validateDraft(body.task, body.existingAutomationId)
        val issues = buildList {
            report.mappingError?.let {
                add(AgentApiValidationIssueV1(it.code, it.path, "Task mapping failed"))
            }
            report.validation?.workflowIssues?.forEach {
                add(AgentApiValidationIssueV1(it.code.name, it.location, it.message))
            }
            report.validation?.configIssues?.forEach {
                val path = if (it.key == "*") it.owner else "${it.owner}.config.${it.key}"
                add(AgentApiValidationIssueV1(it.code, path, "Node configuration is invalid"))
            }
        }
        return respond(
            200,
            AgentApiValidationV1(
                valid = report.isStructurallyValid,
                executable = report.executable,
                summary = report.dryRun?.summary ?: if (report.isStructurallyValid) {
                    "Task is structurally valid"
                } else {
                    "Task validation failed"
                },
                issues = issues
            )
        )
    }

    private fun previewSchedule(request: AgentHttpRequest): AgentHttpResponse =
        when (val result = schedulePreviewService.preview(decode(request))) {
            is AgentSchedulePreviewResult.Success -> respond(200, result.preview)
            is AgentSchedulePreviewResult.Rejected -> error(
                422,
                result.code,
                result.message,
                mapOf("path" to result.path)
            )
        }

    private suspend fun mutationResponse(
        result: AutomationMutationResult,
        created: Boolean = false
    ): AgentHttpResponse = when (result) {
        is AutomationMutationResult.Success -> respond(
            if (created) 201 else 200,
            AgentApiTaskRecordV1(
                id = result.automation.id,
                revision = result.revision,
                task = AgentTaskMapper.fromAutomation(result.automation)
            ),
            mapOf("ETag" to quoteRevision(result.revision))
        )
        is AutomationMutationResult.IdempotentReplay -> {
            val id = result.automationId
            val automation = id?.let { repository.getAutomationById(it) }
            if (id != null && automation != null) {
                val revision = result.revision ?: runtime.effectiveRevision(id) ?: 1L
                respond(
                    200,
                    AgentApiTaskRecordV1(id, revision, AgentTaskMapper.fromAutomation(automation)),
                    mapOf(
                        "ETag" to quoteRevision(revision),
                        "X-Idempotent-Replay" to "true"
                    )
                )
            } else {
                respond(
                    200,
                    mapOf("idempotentReplay" to "true"),
                    mapOf("X-Idempotent-Replay" to "true")
                )
            }
        }
        AutomationMutationResult.IdempotencyConflict ->
            error(409, "idempotency_conflict", "Idempotency key was already used for another request")
        is AutomationMutationResult.Rejected -> error(
            422,
            "preflight_rejected",
            result.report.dryRun?.summary ?: "Task preflight was rejected"
        )
        is AutomationMutationResult.Conflict -> error(
            409,
            "revision_conflict",
            "Task revision changed",
            buildMap {
                result.expectedRevision?.let { put("expectedRevision", it.toString()) }
                result.currentRevision?.let { put("currentRevision", it.toString()) }
            }
        )
        is AutomationMutationResult.DeleteBlocked -> error(
            409,
            "delete_blocked",
            "Task is still referenced by another automation",
            mapOf("issueCount" to result.dependencyIssues.size.toString())
        )
        is AutomationMutationResult.DependencyConflict -> error(
            409,
            "dependency_conflict",
            "Mutation would introduce an invalid automation dependency",
            mapOf("issueCount" to result.dependencyIssues.size.toString())
        )
        is AutomationMutationResult.NotFound ->
            error(404, "task_not_found", "Task was not found")
    }

    private fun AgentApiTaskMutationRequestV1.context(
        agentId: String,
        idempotencyKey: String,
        expectedRevision: Long?
    ) = AutomationMutationContext(
        actorId = actorId(agentId),
        origin = AutomationMutationOrigin.AGENT,
        expectedRevision = expectedRevision,
        requireExecutable = requireExecutable,
        agentId = agentId,
        providerId = providerId,
        modelId = modelId,
        transport = TRANSPORT,
        requestId = requestId,
        conversationId = conversationId,
        idempotencyKey = idempotencyKey,
        riskLevel = riskLevel
    )

    private fun operationFor(method: String, path: String): AgentOperation? {
        val id = taskId(path)
        return when {
            method == "GET" && path == "/api/v1/status" -> AgentOperation.STATUS
            method == "GET" && path == "/api/v1/capabilities" -> AgentOperation.CATALOG_READ
            method == "GET" && path == "/api/v1/catalog" -> AgentOperation.CATALOG_READ
            method == "GET" && path == "/api/v1/tasks" -> AgentOperation.TASK_LIST
            method == "GET" && id != null && path == "/api/v1/tasks/$id" -> AgentOperation.TASK_GET
            method == "POST" && path == "/api/v1/tasks" -> AgentOperation.TASK_CREATE
            method in setOf("PATCH", "PUT") && id != null &&
                path == "/api/v1/tasks/$id" -> AgentOperation.TASK_UPDATE
            method == "DELETE" && id != null &&
                path == "/api/v1/tasks/$id" -> AgentOperation.TASK_DELETE
            method == "POST" && id != null &&
                path == "/api/v1/tasks/$id/enable" -> AgentOperation.TASK_ENABLE
            method == "POST" && id != null &&
                path == "/api/v1/tasks/$id/disable" -> AgentOperation.TASK_DISABLE
            method == "POST" && id != null &&
                path == "/api/v1/tasks/$id/run" -> AgentOperation.TASK_RUN
            method == "POST" && path == "/api/v1/validate" -> AgentOperation.TASK_CREATE
            method == "POST" && path == "/api/v1/simulate" -> AgentOperation.TASK_CREATE
            method == "POST" && path == "/api/v1/schedules/preview" -> AgentOperation.CATALOG_READ
            method == "GET" && path == "/api/v1/history" -> AgentOperation.HISTORY_READ
            method == "GET" && path == "/api/v1/audit" -> AgentOperation.HISTORY_READ
            else -> null
        }
    }

    private fun taskId(path: String): String? {
        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.size < 4 || segments[0] != "api" || segments[1] != "v1" ||
            segments[2] != "tasks"
        ) return null
        return segments[3].takeIf { it.isNotBlank() && it.length <= 128 }
    }

    private fun limitFrom(uri: URI): Int {
        val raw = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.substringBefore('=') == "limit" }
            ?.substringAfter('=', "")
        return raw?.toIntOrNull()?.coerceIn(1, MAX_READ_LIMIT) ?: DEFAULT_READ_LIMIT
    }

    private fun validHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.substringBefore(':').lowercase()
        return normalized == "127.0.0.1" || normalized == "localhost"
    }

    private fun requiredIdempotency(request: AgentHttpRequest): String? =
        request.header("idempotency-key")
            ?.trim()
            ?.takeIf { it.length in 8..256 }

    private fun requiredRevision(request: AgentHttpRequest): Long? {
        val raw = request.header("if-match")?.trim()?.removePrefix("W/")?.trim()
            ?.removeSurrounding("\"")
        return raw?.toLongOrNull()?.takeIf { it >= 1L }
    }

    private fun missingIdempotency() = error(
        428,
        "idempotency_key_required",
        "Mutation requests require Idempotency-Key"
    )

    private fun missingRevision() = error(
        428,
        "if_match_required",
        "Mutation of an existing task requires If-Match with the current revision"
    )

    private inline fun <reified T> decode(request: AgentHttpRequest): T =
        json.decodeFromString(request.body.toString(StandardCharsets.UTF_8))

    private inline fun <reified T> respond(
        status: Int,
        value: T,
        headers: Map<String, String> = emptyMap()
    ): AgentHttpResponse = rawJson(status, json.encodeToString(value), headers)

    private fun rawJson(
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap()
    ) = AgentHttpResponse(
        status = status,
        body = body.toByteArray(StandardCharsets.UTF_8),
        headers = headers + mapOf("Content-Type" to "application/json; charset=utf-8")
    )

    private fun error(
        status: Int,
        code: String,
        message: String,
        details: Map<String, String> = emptyMap()
    ): AgentHttpResponse = respond(
        status,
        AgentApiErrorEnvelopeV1(AgentApiErrorV1(code, message, details))
    )

    private fun quoteRevision(revision: Long) = "\"$revision\""

    private fun actorId(agentId: String) = "agent:$agentId"

    private data class AuthorizedRequest(
        val agentId: String? = null,
        val response: AgentHttpResponse? = null
    )

    private companion object {
        const val TRANSPORT = "LOCAL_REST"
        const val DEFAULT_READ_LIMIT = 50
        const val MAX_READ_LIMIT = 200
    }
}
