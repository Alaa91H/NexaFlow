package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentAuthorizationResult
import com.nexaflow.core.agentsecurity.AgentIdentityBinding
import com.nexaflow.core.agentsecurity.AgentOperation
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.agentsecurity.AgentScope
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Phase 14 - minimal A2A JSON-RPC adapter.
 *
 * Supported methods:
 * - `message/send`: execute one or more NexaFlow skills (skill ids are exactly
 *   the MCP tool names in [AgentMcpToolRegistry], guaranteeing REST/MCP/A2A
 *   parity) through [AgentMcpToolExecutor], which itself forwards to
 *   `AgentApiController` and therefore to `AutomationCommandService`.
 * - `tasks/get`: read-only status probe backed by the same executor.
 *
 * `message/stream` and `tasks/cancel` are rejected with explicit JSON-RPC
 * errors: NexaFlow executes skills synchronously and automation runs are not
 * remotely cancellable through this surface.
 *
 * Authentication mirrors MCP/REST: the agent card is public, every RPC call
 * requires a Bearer access session with the union of the invoked skill scopes.
 */
class AgentA2AController(
    private val accessManager: AgentAccessManager,
    private val authorizer: AgentRequestAuthorizer,
    private val skillExecutor: AgentMcpToolExecutor,
    private val hostPolicy: AgentApiHostPolicy = AgentApiHostPolicy(),
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }
) {
    suspend fun handle(request: AgentHttpRequest): AgentHttpResponse {
        if (request.method != "POST") {
            return httpError(405, null, METHOD_NOT_FOUND, "A2A only accepts HTTP POST")
        }
        if (!hostPolicy.isAllowed(request.header("host")) || request.header("origin") != null) {
            return httpError(403, null, -32600, "A2A request origin is not allowed")
        }
        if (!request.header("content-type").orEmpty().startsWith("application/json")) {
            return httpError(400, null, -32700, "A2A requires application/json")
        }

        val root = try {
            json.parseToJsonElement(request.body.toString(StandardCharsets.UTF_8))
        } catch (_: SerializationException) {
            return httpError(400, null, -32700, "Parse error")
        }
        if (root is JsonArray) {
            return httpError(400, null, -32600, "JSON-RPC batching is not supported")
        }
        val message = root as? JsonObject
            ?: return httpError(400, null, -32600, "Invalid JSON-RPC request")
        val id = message["id"]
        if (message["jsonrpc"]?.jsonPrimitive?.contentOrNull != JSON_RPC) {
            return httpError(400, id, -32600, "Invalid JSON-RPC version")
        }
        val method = message["method"]?.jsonPrimitive?.contentOrNull
            ?: return httpError(400, id, -32600, "Missing JSON-RPC method")
        val params = message["params"] as? JsonObject ?: buildJsonObject {}

        if (id == null) {
            // A2A notifications are acknowledged without execution.
            return AgentHttpResponse(202, ByteArray(0), emptyMap())
        }

        return when (method) {
            "message/send" -> sendMessage(id, params, request)
            "tasks/get" -> getTask(id, params, request)
            "message/stream" -> rpcError(
                id,
                -32601,
                "message/stream is not supported; use message/send for synchronous skill execution"
            )
            "tasks/cancel" -> rpcError(
                id,
                -32601,
                "tasks/cancel is not supported; automation runs are not remotely cancellable"
            )
            else -> rpcError(id, -32601, "Method not found")
        }
    }

    fun card(baseUrl: String): AgentHttpResponse {
        val body = AgentA2ACard.build(baseUrl).toString()
            .toByteArray(StandardCharsets.UTF_8)
        return AgentHttpResponse(
            200,
            body,
            mapOf("Content-Type" to "application/json; charset=utf-8")
        )
    }

    private suspend fun sendMessage(
        id: JsonElement,
        params: JsonObject,
        request: AgentHttpRequest
    ): AgentHttpResponse {
        if (bearerToken(request).isBlank()) {
            return AuthFailure(401, "missing_bearer_token").toRpc(id)
        }
        val message = params["message"] as? JsonObject
            ?: return rpcError(id, -32602, "message/send requires params.message")
        val parts = message["parts"] as? JsonArray
            ?: return rpcError(id, -32602, "message/send requires message.parts")
        if (parts.isEmpty() || parts.size > MAX_PARTS) {
            return rpcError(id, -32602, "message.parts must contain 1..$MAX_PARTS entries")
        }

        val skills = ArrayList<SkillCall>(parts.size)
        for (part in parts) {
            val parsed = parseSkillPart(part)
                ?: return rpcError(id, -32602, "Each part must be {kind,data:{skill,input}} or {kind:text,text:JSON}")
            val definition = AgentMcpToolRegistry.find(parsed.skill)
                ?: return taskFailure(
                    id = id,
                    taskId = newTaskId(),
                    contextId = contextId(params, message),
                    code = "unknown_skill",
                    text = "A2A skill was not found: ${parsed.skill}"
                )
            skills += SkillCall(definition.name, definition.requiredScopes, parsed.input)
        }

        val authError = authorize(request, skills.flatMapTo(LinkedHashSet()) { it.scopes })
        if (authError != null) return authError.toRpc(id)

        val contextId = contextId(params, message)
        val taskId = newTaskId()
        val collected = ArrayList<JsonObject>(skills.size)
        for (skill in skills) {
            val response = skillExecutor.execute(skill.name, skill.input, request)
            val payload = runCatching {
                json.parseToJsonElement(response.body.toString(StandardCharsets.UTF_8))
            }.getOrElse {
                JsonPrimitive(response.body.toString(StandardCharsets.UTF_8))
            }
            collected += buildJsonObject {
                put("kind", "data")
                put("data", buildJsonObject {
                    put("skill", skill.name)
                    put("status", response.status)
                    put("result", payload)
                })
            }
            if (response.status !in 200..299) {
                return taskFailure(
                    id = id,
                    taskId = taskId,
                    contextId = contextId,
                    code = "skill_failed",
                    text = "Skill ${skill.name} failed with HTTP ${response.status}",
                    artifactParts = buildJsonArray { collected.forEach { add(it) } },
                    skill = skill.name,
                    httpStatus = response.status
                )
            }
        }
        val artifactParts = buildJsonArray { collected.forEach { add(it) } }

        return rpcResult(
            id,
            buildJsonObject {
                put("id", taskId)
                put("contextId", contextId)
                putJsonObject("status") {
                    put("state", "completed")
                    put("message", buildJsonObject {
                        put("role", "agent")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("kind", "text")
                                put("text", "All ${skills.size} NexaFlow skill(s) completed.")
                            })
                        })
                    })
                }
                putJsonArray("artifacts") {
                    add(buildJsonObject {
                        put("artifactId", "nexaflow-result-1")
                        put("parts", artifactParts)
                    })
                }
            }
        )
    }

    private suspend fun getTask(
        id: JsonElement,
        params: JsonObject,
        request: AgentHttpRequest
    ): AgentHttpResponse {
        if (bearerToken(request).isBlank()) {
            return AuthFailure(401, "missing_bearer_token").toRpc(id)
        }
        val taskId = params["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { TASK_ID.matches(it) }
            ?: return rpcError(id, -32602, "tasks/get requires params.id")
        // A2A task ids for completed message/send calls are not durable server
        // tasks; automation state itself is readable through the get_task skill.
        // As a read probe, delegate to list_tasks semantics via get_task skill
        // when the id looks like an automation id, otherwise report not found.
        val authError = authorize(request, AgentOperation.TASK_GET.requiredScopes)
        if (authError != null) return authError.toRpc(id)

        val response = skillExecutor.execute(
            "nexaflow.get_task",
            buildJsonObject { put("id", taskId) },
            request
        )
        return if (response.status in 200..299) {
            val payload = runCatching {
                json.parseToJsonElement(response.body.toString(StandardCharsets.UTF_8))
            }.getOrElse { JsonNull }
            rpcResult(
                id,
                buildJsonObject {
                    put("id", taskId)
                    putJsonObject("status") { put("state", "completed") }
                    putJsonArray("artifacts") {
                        add(buildJsonObject {
                            put("artifactId", "nexaflow-task")
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("kind", "data")
                                    put("data", payload)
                                })
                            })
                        })
                    }
                }
            )
        } else {
            rpcError(id, -32004, "Task not found")
        }
    }

    private fun parseSkillPart(part: JsonElement): ParsedSkill? {
        val obj = part as? JsonObject ?: return null
        return when (obj["kind"]?.jsonPrimitive?.contentOrNull) {
            "data" -> {
                val data = obj["data"] as? JsonObject ?: return null
                val skill = data["skill"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.length in 1..128 } ?: return null
                ParsedSkill(skill, data["input"] as? JsonObject ?: buildJsonObject {})
            }
            "text" -> {
                val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                val decoded = runCatching {
                    json.parseToJsonElement(text).jsonObject
                }.getOrNull() ?: return null
                val skill = decoded["skill"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.length in 1..128 } ?: return null
                ParsedSkill(skill, decoded["input"] as? JsonObject ?: buildJsonObject {})
            }
            else -> null
        }
    }

    private fun bearerToken(request: AgentHttpRequest): String = request.header("authorization")
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ')
        ?.trim()
        .orEmpty()

    private suspend fun authorize(
        request: AgentHttpRequest,
        scopes: Set<AgentScope>
    ): AuthFailure? {
        val token = bearerToken(request)
        if (token.isBlank()) return AuthFailure(401, "missing_bearer_token")
        val binding = AgentIdentityBinding(
            transportKeyFingerprint = request.header("x-nexaflow-transport-key")
                ?.takeIf(String::isNotBlank)
        )
        val result = accessManager.authorize(token, scopes, binding)
        if (result !is AgentAuthorizationResult.Authorized) {
            // Shared abuse controls (payload/rate limits) use the same gate as REST.
            val rate = authorizer.authorize(
                token,
                AgentOperation.CATALOG_READ,
                binding,
                request.body.size
            )
            if (rate is AgentAuthorizationResult.PayloadTooLarge) {
                return AuthFailure(413, "payload_too_large")
            }
            if (rate is AgentAuthorizationResult.RateLimited) {
                return AuthFailure(429, "rate_limited")
            }
        }
        return when (result) {
            is AgentAuthorizationResult.Authorized -> null
            AgentAuthorizationResult.Disabled -> AuthFailure(403, "agent_access_disabled")
            AgentAuthorizationResult.InvalidToken -> AuthFailure(401, "invalid_access_token")
            AgentAuthorizationResult.Expired -> AuthFailure(401, "access_token_expired")
            AgentAuthorizationResult.Revoked -> AuthFailure(401, "agent_revoked")
            AgentAuthorizationResult.BindingMismatch -> AuthFailure(401, "binding_mismatch")
            AgentAuthorizationResult.ScopeDenied -> AuthFailure(403, "scope_denied")
            AgentAuthorizationResult.PayloadTooLarge -> AuthFailure(413, "payload_too_large")
            AgentAuthorizationResult.RateLimited -> AuthFailure(429, "rate_limited")
        }
    }

    private fun taskFailure(
        id: JsonElement,
        taskId: String,
        contextId: String,
        code: String,
        text: String,
        artifactParts: JsonArray = buildJsonArray {},
        skill: String? = null,
        httpStatus: Int? = null
    ): AgentHttpResponse = rpcResult(
        id,
        buildJsonObject {
            put("id", taskId)
            put("contextId", contextId)
            putJsonObject("status") {
                put("state", "failed")
                put("message", buildJsonObject {
                    put("role", "agent")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("kind", "data")
                            put("data", buildJsonObject {
                                put("code", code)
                                put("message", text)
                                skill?.let { put("skill", it) }
                                httpStatus?.let { put("httpStatus", it) }
                            })
                        })
                    })
                })
            }
            putJsonArray("artifacts") {
                if (artifactParts.isNotEmpty()) {
                    add(buildJsonObject {
                        put("artifactId", "nexaflow-partial-result")
                        put("parts", artifactParts)
                    })
                }
            }
        }
    )

    private fun contextId(params: JsonObject, message: JsonObject): String {
        val candidate = params["contextId"]?.jsonPrimitive?.contentOrNull
            ?: message["contextId"]?.jsonPrimitive?.contentOrNull
            ?: message["contextIds"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
        return candidate?.takeIf { CONTEXT_ID.matches(it) } ?: newTaskId()
    }

    private fun newTaskId(): String = UUID.randomUUID().toString()

    private fun rpcResult(id: JsonElement, result: JsonObject) = jsonRpc(200, buildJsonObject {
        put("jsonrpc", JSON_RPC)
        put("id", id)
        put("result", result)
    })

    private fun rpcError(id: JsonElement?, code: Int, message: String) = jsonRpc(
        200,
        buildJsonObject {
            put("jsonrpc", JSON_RPC)
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
    )

    private fun httpError(status: Int, id: JsonElement?, code: Int, message: String) =
        AgentHttpResponse(
            status,
            buildJsonObject {
                put("jsonrpc", JSON_RPC)
                put("id", id ?: JsonNull)
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            }.toString().toByteArray(StandardCharsets.UTF_8),
            mapOf("Content-Type" to "application/json; charset=utf-8")
        )

    private fun jsonRpc(status: Int, body: JsonObject) = AgentHttpResponse(
        status,
        body.toString().toByteArray(StandardCharsets.UTF_8),
        mapOf("Content-Type" to "application/json; charset=utf-8")
    )

    private data class ParsedSkill(val skill: String, val input: JsonObject)
    private data class SkillCall(val name: String, val scopes: Set<AgentScope>, val input: JsonObject)
    private data class AuthFailure(val httpStatus: Int, val code: String) {
        fun toRpc(id: JsonElement): AgentHttpResponse = AgentHttpResponse(
            httpStatus,
            buildJsonObject {
                put("jsonrpc", JSON_RPC)
                put("id", id)
                putJsonObject("error") {
                    put("code", code)
                    put("message", code)
                }
            }.toString().toByteArray(StandardCharsets.UTF_8),
            mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "WWW-Authenticate" to "Bearer realm=\"NexaFlow A2A\""
            )
        )
    }

    companion object {
        const val METHOD_NOT_FOUND = -32601
        private const val JSON_RPC = "2.0"
        private const val MAX_PARTS = 8
        private val TASK_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
        private val CONTEXT_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
