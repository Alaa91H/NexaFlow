package com.nexaflow.core.agentapi

import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface AgentMcpToolExecutor {
    suspend fun execute(
        toolName: String,
        arguments: JsonObject,
        sourceRequest: AgentHttpRequest
    ): AgentHttpResponse
}

class AgentMcpRestToolExecutor(
    private val restController: AgentApiController,
    private val trustedPrincipal: AgentTrustedPrincipal? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }
) : AgentMcpToolExecutor {

    override suspend fun execute(
        toolName: String,
        arguments: JsonObject,
        sourceRequest: AgentHttpRequest
    ): AgentHttpResponse = try {
        when (toolName) {
            "nexaflow.get_status" -> forward("GET", "/api/v1/status", sourceRequest)
            "nexaflow.get_capabilities" -> forward("GET", "/api/v1/capabilities", sourceRequest)
            "nexaflow.list_triggers" -> projectCatalog("triggers", sourceRequest)
            "nexaflow.list_actions" -> projectCatalog("actions", sourceRequest)
            "nexaflow.list_constraints" -> projectCatalog("constraints", sourceRequest)
            "nexaflow.list_tasks" -> forward("GET", "/api/v1/tasks", sourceRequest)
            "nexaflow.get_task" -> forward("GET", "/api/v1/tasks/${taskId(arguments)}", sourceRequest)
            "nexaflow.validate_task" -> forward(
                "POST",
                "/api/v1/validate",
                sourceRequest,
                buildJsonObject {
                    put("task", requiredObject(arguments, "task"))
                    arguments["existingAutomationId"]?.let {
                        put("existingAutomationId", it)
                    }
                }
            )
            "nexaflow.preview_schedule" -> forward(
                "POST",
                "/api/v1/schedules/preview",
                sourceRequest,
                arguments.without(CONTROL_KEYS)
            )
            "nexaflow.dry_run_task" -> forward(
                "POST",
                "/api/v1/simulate",
                sourceRequest,
                arguments.without(CONTROL_KEYS)
            )
            "nexaflow.create_task" -> createTask(arguments, sourceRequest)
            "nexaflow.update_task" -> updateTask(arguments, sourceRequest)
            "nexaflow.clone_task" -> cloneTask(arguments, sourceRequest)
            "nexaflow.enable_task" -> stateMutation("enable", arguments, sourceRequest)
            "nexaflow.disable_task" -> stateMutation("disable", arguments, sourceRequest)
            "nexaflow.delete_task" -> deleteTask(arguments, sourceRequest)
            "nexaflow.run_task" -> stateMutation("run", arguments, sourceRequest)
            "nexaflow.get_history" -> forward(
                "GET",
                "/api/v1/history?limit=${limit(arguments)}",
                sourceRequest
            )
            "nexaflow.wait_events" -> forward(
                "GET",
                "/api/v1/events?${eventQuery(arguments)}",
                sourceRequest
            )
            "nexaflow.get_audit" -> forward(
                "GET",
                "/api/v1/audit?limit=${limit(arguments)}",
                sourceRequest
            )
            else -> localError(404, "unknown_tool", "MCP tool was not found")
        }
    } catch (_: ToolInputException) {
        localError(422, "invalid_tool_arguments", "Tool arguments do not match the MCP schema")
    }

    private suspend fun createTask(
        arguments: JsonObject,
        source: AgentHttpRequest
    ) = forward(
        "POST",
        "/api/v1/tasks",
        source,
        mutationBody(arguments),
        mapOf("idempotency-key" to requiredString(arguments, "idempotencyKey"))
    )

    private suspend fun updateTask(
        arguments: JsonObject,
        source: AgentHttpRequest
    ): AgentHttpResponse {
        val id = taskId(arguments)
        return forward(
            "PATCH",
            "/api/v1/tasks/$id",
            source,
            mutationBody(arguments),
            mutationHeaders(arguments)
        )
    }

    private suspend fun cloneTask(
        arguments: JsonObject,
        source: AgentHttpRequest
    ): AgentHttpResponse {
        val id = taskId(arguments)
        val expectedRevision = requiredLong(arguments, "revision")
        val existing = forward("GET", "/api/v1/tasks/$id", source)
        if (existing.status !in 200..299) return existing

        val record = parseObject(existing)
        val currentRevision = record["revision"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return localError(502, "invalid_internal_response", "Task revision is missing")
        if (currentRevision != expectedRevision) {
            return localError(
                409,
                "revision_conflict",
                "Task revision changed",
                mapOf(
                    "expectedRevision" to expectedRevision.toString(),
                    "currentRevision" to currentRevision.toString()
                )
            )
        }

        val sourceTask = record["task"]?.jsonObject
            ?: return localError(502, "invalid_internal_response", "Task payload is missing")
        val clonedTask = JsonObject(sourceTask.toMutableMap().apply {
            arguments["name"]?.let { put("name", it) }
            put("enabled", arguments["enabled"] ?: JsonPrimitive(false))
        })
        return createTask(
            JsonObject(arguments.toMutableMap().apply { put("task", clonedTask) }),
            source
        )
    }

    private suspend fun stateMutation(
        operation: String,
        arguments: JsonObject,
        source: AgentHttpRequest
    ): AgentHttpResponse {
        val id = taskId(arguments)
        return forward(
            "POST",
            "/api/v1/tasks/$id/$operation",
            source,
            extraHeaders = mutationHeaders(arguments)
        )
    }

    private suspend fun deleteTask(
        arguments: JsonObject,
        source: AgentHttpRequest
    ): AgentHttpResponse {
        val id = taskId(arguments)
        return forward(
            "DELETE",
            "/api/v1/tasks/$id",
            source,
            extraHeaders = mutationHeaders(arguments)
        )
    }

    private suspend fun projectCatalog(
        field: String,
        source: AgentHttpRequest
    ): AgentHttpResponse {
        val response = forward("GET", "/api/v1/catalog", source)
        if (response.status !in 200..299) return response
        val catalog = parseObject(response)
        return jsonResponse(
            200,
            buildJsonObject {
                catalog["schemaVersion"]?.let { put("schemaVersion", it) }
                catalog[field]?.let { put(field, it) }
            }
        )
    }

    private suspend fun forward(
        method: String,
        target: String,
        source: AgentHttpRequest,
        body: JsonElement? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): AgentHttpResponse {
        val bytes = body?.toString()?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (bytes.size > AgentHttpRequestParser.MAX_BODY_BYTES) {
            return localError(413, "payload_too_large", "Forwarded tool payload is too large")
        }
        val headers = buildMap {
            put("host", "127.0.0.1")
            source.header("authorization")?.let { put("authorization", it) }
            source.header("x-nexaflow-transport-key")?.let {
                put("x-nexaflow-transport-key", it)
            }
            val requestId = extraHeaders["x-request-id"]
                ?: source.header("x-request-id")
            requestId?.let { put("x-request-id", it) }
            if (bytes.isNotEmpty()) put("content-type", "application/json")
            putAll(extraHeaders)
        }
        val forwarded = AgentHttpRequest(method, target, headers, bytes)
        return trustedPrincipal?.let { principal ->
            restController.handleTrusted(forwarded, principal)
        } ?: restController.handle(forwarded)
    }

    private fun mutationBody(arguments: JsonObject) = buildJsonObject {
        put("task", requiredObject(arguments, "task"))
        OPTIONAL_MUTATION_FIELDS.forEach { key ->
            arguments[key]?.let { put(key, it) }
        }
    }

    private fun mutationHeaders(arguments: JsonObject) = buildMap {
        put("idempotency-key", requiredString(arguments, "idempotencyKey"))
        put("if-match", "\"${requiredLong(arguments, "revision")}\"")
        arguments["requestId"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let { put("x-request-id", it) }
    }

    private fun eventQuery(arguments: JsonObject): String {
        val streamId = arguments["streamId"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { EVENT_STREAM_ID.matches(it) }
        val after = arguments["after"]?.jsonPrimitive?.longOrNull
            ?.coerceAtLeast(0L)
            ?: 0L
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull
            ?.coerceIn(1, AgentEventHub.MAX_BATCH_SIZE)
            ?: 50
        val waitMs = arguments["waitMs"]?.jsonPrimitive?.longOrNull
            ?.coerceIn(0L, AgentEventHub.MAX_WAIT_MS)
            ?: 0L
        return buildList {
            streamId?.let { add("streamId=$it") }
            add("after=$after")
            add("limit=$limit")
            add("waitMs=$waitMs")
        }.joinToString("&")
    }

    private fun taskId(arguments: JsonObject): String {
        val id = requiredString(arguments, "id")
        if (!TASK_ID.matches(id)) throw ToolInputException()
        return id
    }

    private fun limit(arguments: JsonObject): Int =
        arguments["limit"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 200) ?: 50

    private fun requiredString(arguments: JsonObject, key: String): String =
        arguments[key]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw ToolInputException()

    private fun requiredLong(arguments: JsonObject, key: String): Long =
        arguments[key]?.jsonPrimitive?.content?.toLongOrNull()
            ?.takeIf { it >= 1L }
            ?: throw ToolInputException()

    private fun requiredObject(arguments: JsonObject, key: String): JsonObject =
        arguments[key] as? JsonObject ?: throw ToolInputException()

    private fun JsonObject.without(keys: Set<String>) =
        JsonObject(filterKeys { it !in keys })

    private fun parseObject(response: AgentHttpResponse): JsonObject =
        runCatching {
            json.parseToJsonElement(
                response.body.toString(StandardCharsets.UTF_8)
            ).jsonObject
        }.getOrElse { throw ToolInputException() }

    private fun localError(
        status: Int,
        code: String,
        message: String,
        details: Map<String, String> = emptyMap()
    ) = jsonResponse(
        status,
        buildJsonObject {
            putJsonObject("error") {
                put("code", code)
                put("message", message)
                if (details.isNotEmpty()) {
                    putJsonObject("details") {
                        details.forEach { (key, value) -> put(key, value) }
                    }
                }
            }
        }
    )

    private fun jsonResponse(status: Int, value: JsonElement) =
        AgentHttpResponse(
            status,
            value.toString().toByteArray(StandardCharsets.UTF_8),
            mapOf("Content-Type" to "application/json; charset=utf-8")
        )

    private class ToolInputException : IllegalArgumentException()

    private companion object {
        val TASK_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        val EVENT_STREAM_ID = Regex("[A-Za-z0-9._:-]{1,128}")
        val CONTROL_KEYS = setOf("idempotencyKey", "id", "revision")
        val OPTIONAL_MUTATION_FIELDS = listOf(
            "providerId",
            "modelId",
            "conversationId",
            "requestId",
            "riskLevel",
            "requireExecutable"
        )
    }
}
