package com.nexaflow.core.agentapi

import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentAuthorizationResult
import com.nexaflow.core.agentsecurity.AgentIdentityBinding
import com.nexaflow.core.agentsecurity.AgentOperation
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.agentsecurity.AgentScope
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AgentMcpController(
    private val accessManager: AgentAccessManager,
    private val authorizer: AgentRequestAuthorizer,
    private val toolExecutor: AgentMcpToolExecutor,
    private val hostPolicy: AgentApiHostPolicy = AgentApiHostPolicy(),
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }
) {
    suspend fun handle(request: AgentHttpRequest): AgentHttpResponse {
        if (request.method != "POST") {
            return httpProtocolError(405, null, -32600, "MCP only accepts HTTP POST")
        }
        if (!hostPolicy.isAllowed(request.header("host")) || request.header("origin") != null) {
            return httpProtocolError(403, null, -32600, "MCP request origin is not allowed")
        }
        if (!request.header("content-type").orEmpty().startsWith("application/json")) {
            return httpProtocolError(400, null, -32600, "MCP requires application/json")
        }

        val root = try {
            json.parseToJsonElement(request.body.toString(StandardCharsets.UTF_8))
        } catch (_: SerializationException) {
            return httpProtocolError(400, null, -32700, "Parse error")
        }
        if (root is JsonArray) {
            return httpProtocolError(400, null, -32600, "JSON-RPC batching is not supported")
        }
        val message = root as? JsonObject
            ?: return httpProtocolError(400, null, -32600, "Invalid JSON-RPC request")
        val id = message["id"]
        if (message["jsonrpc"]?.jsonPrimitive?.contentOrNull != JSON_RPC) {
            return httpProtocolError(400, id, -32600, "Invalid JSON-RPC version")
        }
        val method = message["method"]?.jsonPrimitive?.contentOrNull
            ?: return httpProtocolError(400, id, -32600, "Missing JSON-RPC method")
        val params = message["params"] as? JsonObject ?: buildJsonObject {}
        val modern = isModern(request, method, params)

        if (modern) {
            validateModern(request, method, params)?.let {
                return httpProtocolError(400, id, HEADER_MISMATCH, it, modern = true)
            }
        } else if (request.header(PROTOCOL_HEADER) !in setOf(null, "", LEGACY_VERSION)) {
            return httpProtocolError(
                400,
                id,
                UNSUPPORTED_PROTOCOL,
                "Unsupported MCP protocol version"
            )
        }

        if (id == null) {
            return handleNotification(request, method, modern)
        }

        val scopes = requiredScopes(method, params)
        val authError = authorize(request, scopes, toolCall = method == "tools/call")
        if (authError != null) return authError

        return when (method) {
            "server/discover" -> if (modern) discover(id) else {
                httpProtocolError(
                    400,
                    id,
                    UNSUPPORTED_PROTOCOL,
                    "server/discover requires MCP 2026-07-28"
                )
            }
            "initialize" -> if (!modern) initialize(id, params) else {
                protocolError(id, -32601, "initialize was removed in MCP 2026-07-28", true)
            }
            "ping" -> if (!modern) success(id, buildJsonObject {}, false) else {
                protocolError(id, -32601, "ping is not defined in MCP 2026-07-28", true)
            }
            "tools/list" -> listTools(id, modern)
            "tools/call" -> callTool(id, params, request, modern)
            else -> protocolError(id, -32601, "Method not found", modern)
        }
    }

    private suspend fun handleNotification(
        request: AgentHttpRequest,
        method: String,
        modern: Boolean
    ): AgentHttpResponse {
        if (!modern && method in LEGACY_NOTIFICATIONS) {
            authorize(request, AgentOperation.CATALOG_READ.requiredScopes, toolCall = true)
                ?.let { return it }
        }
        return AgentHttpResponse(
            status = 202,
            headers = if (modern) modernHeaders() else emptyMap()
        )
    }

    private suspend fun authorize(
        request: AgentHttpRequest,
        scopes: Set<AgentScope>,
        toolCall: Boolean
    ): AgentHttpResponse? {
        val token = request.header("authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            .orEmpty()
        if (token.isBlank()) {
            return authError(401, "missing_bearer_token", "Bearer access token is required")
        }
        val binding = AgentIdentityBinding(
            transportKeyFingerprint = request.header("x-nexaflow-transport-key")
                ?.takeIf(String::isNotBlank)
        )
        val result = if (toolCall) {
            accessManager.authorize(token, scopes, binding)
        } else {
            authorizer.authorize(
                token,
                AgentOperation.CATALOG_READ,
                binding,
                request.body.size
            )
        }
        return authorizationError(result)
    }

    private fun authorizationError(result: AgentAuthorizationResult): AgentHttpResponse? =
        when (result) {
            is AgentAuthorizationResult.Authorized -> null
            AgentAuthorizationResult.Disabled ->
                authError(403, "agent_access_disabled", "AI Agent Access is disabled")
            AgentAuthorizationResult.InvalidToken ->
                authError(401, "invalid_access_token", "Access token is invalid")
            AgentAuthorizationResult.Expired ->
                authError(401, "access_token_expired", "Access token expired")
            AgentAuthorizationResult.Revoked ->
                authError(401, "agent_revoked", "Agent grant was revoked")
            AgentAuthorizationResult.BindingMismatch ->
                authError(401, "binding_mismatch", "Agent transport identity does not match")
            AgentAuthorizationResult.ScopeDenied ->
                authError(403, "scope_denied", "Agent scope does not allow this operation")
            AgentAuthorizationResult.PayloadTooLarge ->
                authError(413, "payload_too_large", "Request payload is too large")
            AgentAuthorizationResult.RateLimited ->
                authError(429, "rate_limited", "Agent request rate limit exceeded")
        }

    private fun requiredScopes(method: String, params: JsonObject): Set<AgentScope> =
        if (method == "tools/call") {
            params["name"]?.jsonPrimitive?.contentOrNull
                ?.let(AgentMcpToolRegistry::find)
                ?.requiredScopes
                ?: AgentOperation.CATALOG_READ.requiredScopes
        } else {
            AgentOperation.CATALOG_READ.requiredScopes
        }

    private fun initialize(id: JsonElement, params: JsonObject): AgentHttpResponse {
        val requested = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        val selected = if (requested == LEGACY_VERSION) requested else LEGACY_VERSION
        return success(
            id,
            buildJsonObject {
                put("protocolVersion", selected)
                putJsonObject("capabilities") {
                    putJsonObject("tools") { put("listChanged", false) }
                }
                putJsonObject("serverInfo") {
                    put("name", SERVER_NAME)
                    put("version", SERVER_VERSION)
                }
                put("instructions", INSTRUCTIONS)
            },
            false
        )
    }

    private fun discover(id: JsonElement) = success(
        id,
        buildJsonObject {
            put("resultType", "complete")
            putJsonArray("supportedVersions") {
                add(JsonPrimitive(MODERN_VERSION))
                add(JsonPrimitive(LEGACY_VERSION))
            }
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
            }
            put("instructions", INSTRUCTIONS)
            put("ttlMs", CACHE_TTL_MS)
            put("cacheScope", "private")
            put("_meta", serverMeta())
        },
        modern = true,
        stamp = false
    )

    private fun listTools(id: JsonElement, modern: Boolean): AgentHttpResponse =
        success(
            id,
            buildJsonObject {
                putJsonArray("tools") {
                    AgentMcpToolRegistry.tools.forEach { add(it.toJson()) }
                }
                if (modern) {
                    put("ttlMs", CACHE_TTL_MS)
                    put("cacheScope", "private")
                }
            },
            modern
        )

    private suspend fun callTool(
        id: JsonElement,
        params: JsonObject,
        request: AgentHttpRequest,
        modern: Boolean
    ): AgentHttpResponse {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return protocolError(id, -32602, "Missing tool name", modern)
        AgentMcpToolRegistry.find(name)
            ?: return toolResult(
                id,
                buildJsonObject {
                    putJsonObject("error") {
                        put("code", "unknown_tool")
                        put("message", "MCP tool was not found")
                    }
                },
                isError = true,
                modern = modern
            )

        val arguments = params["arguments"] as? JsonObject ?: buildJsonObject {}
        val response = toolExecutor.execute(name, arguments, request)
        val payload = runCatching {
            json.parseToJsonElement(response.body.toString(StandardCharsets.UTF_8))
        }.getOrElse {
            JsonPrimitive(response.body.toString(StandardCharsets.UTF_8))
        }
        return toolResult(
            id,
            payload,
            isError = response.status !in 200..299,
            modern = modern
        )
    }

    private fun toolResult(
        id: JsonElement,
        payload: JsonElement,
        isError: Boolean,
        modern: Boolean
    ): AgentHttpResponse = success(
        id,
        buildJsonObject {
            putJsonArray("content") {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", payload.toString())
                    }
                )
            }
            putJsonObject("structuredContent") { put("data", payload) }
            put("isError", isError)
        },
        modern
    )

    private fun success(
        id: JsonElement,
        result: JsonObject,
        modern: Boolean,
        stamp: Boolean = true
    ): AgentHttpResponse {
        val bodyResult = if (modern && stamp) {
            buildJsonObject {
                result.forEach { (key, value) -> put(key, value) }
                put("resultType", "complete")
                put("_meta", serverMeta())
            }
        } else {
            result
        }
        return jsonResponse(
            200,
            buildJsonObject {
                put("jsonrpc", JSON_RPC)
                put("id", id)
                put("result", bodyResult)
            },
            modern
        )
    }

    private fun protocolError(
        id: JsonElement?,
        code: Int,
        message: String,
        modern: Boolean
    ) = jsonResponse(200, errorBody(id, code, message), modern)

    private fun httpProtocolError(
        status: Int,
        id: JsonElement?,
        code: Int,
        message: String,
        modern: Boolean = false
    ) = jsonResponse(status, errorBody(id, code, message), modern)

    private fun errorBody(id: JsonElement?, code: Int, message: String) =
        buildJsonObject {
            put("jsonrpc", JSON_RPC)
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

    private fun authError(status: Int, code: String, message: String) =
        AgentHttpResponse(
            status,
            buildJsonObject {
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            }.toString().toByteArray(StandardCharsets.UTF_8),
            mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "WWW-Authenticate" to "Bearer realm=\"NexaFlow MCP\""
            )
        )

    private fun jsonResponse(
        status: Int,
        body: JsonObject,
        modern: Boolean
    ) = AgentHttpResponse(
        status,
        body.toString().toByteArray(StandardCharsets.UTF_8),
        buildMap {
            put("Content-Type", "application/json; charset=utf-8")
            if (modern) put("MCP-Protocol-Version", MODERN_VERSION)
        }
    )

    private fun isModern(
        request: AgentHttpRequest,
        method: String,
        params: JsonObject
    ): Boolean {
        val metaVersion = (params["_meta"] as? JsonObject)
            ?.get(PROTOCOL_META)
            ?.jsonPrimitive
            ?.contentOrNull
        return method == "server/discover" ||
            request.header(PROTOCOL_HEADER) == MODERN_VERSION ||
            metaVersion == MODERN_VERSION
    }

    private fun validateModern(
        request: AgentHttpRequest,
        method: String,
        params: JsonObject
    ): String? {
        if (request.header(PROTOCOL_HEADER) != MODERN_VERSION) {
            return "MCP-Protocol-Version does not match the modern protocol"
        }
        if (request.header(METHOD_HEADER) != method) {
            return "Mcp-Method header does not match the JSON-RPC method"
        }
        val meta = params["_meta"] as? JsonObject
            ?: return "Modern MCP requests require params._meta"
        if (meta[PROTOCOL_META]?.jsonPrimitive?.contentOrNull != MODERN_VERSION) {
            return "Modern MCP _meta protocolVersion does not match"
        }
        if (meta[CAPABILITIES_META] !is JsonObject) {
            return "Modern MCP requests require clientCapabilities in _meta"
        }
        if (method == "tools/call") {
            val name = params["name"]?.jsonPrimitive?.contentOrNull
                ?: return "tools/call requires params.name"
            if (request.header(NAME_HEADER) != name) {
                return "Mcp-Name header does not match the tool name"
            }
        } else if (request.header(NAME_HEADER) != null) {
            return "Mcp-Name is not valid for this MCP method"
        }
        return null
    }

    private fun serverMeta() = buildJsonObject {
        putJsonObject(SERVER_INFO_META) {
            put("name", SERVER_NAME)
            put("version", SERVER_VERSION)
        }
    }

    private fun modernHeaders() =
        mapOf("MCP-Protocol-Version" to MODERN_VERSION)

    companion object {
        const val MODERN_VERSION = "2026-07-28"
        const val LEGACY_VERSION = "2025-11-25"
        private const val JSON_RPC = "2.0"
        private const val SERVER_NAME = "NexaFlow"
        private const val SERVER_VERSION = "1.0.0"
        private const val CACHE_TTL_MS = 300_000
        private const val HEADER_MISMATCH = -32020
        private const val UNSUPPORTED_PROTOCOL = -32022
        private const val PROTOCOL_HEADER = "mcp-protocol-version"
        private const val METHOD_HEADER = "mcp-method"
        private const val NAME_HEADER = "mcp-name"
        private const val PROTOCOL_META = "io.modelcontextprotocol/protocolVersion"
        private const val CAPABILITIES_META =
            "io.modelcontextprotocol/clientCapabilities"
        private const val SERVER_INFO_META = "io.modelcontextprotocol/serverInfo"
        private const val INSTRUCTIONS =
            "Inspect, validate, simulate and manage Android automations with NexaFlow tools. " +
                "Mutations require revision and idempotency preconditions."
        private val LEGACY_NOTIFICATIONS = setOf(
            "notifications/initialized",
            "notifications/cancelled"
        )
    }
}
