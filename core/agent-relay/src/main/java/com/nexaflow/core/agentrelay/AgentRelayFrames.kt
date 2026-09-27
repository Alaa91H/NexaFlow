package com.nexaflow.core.agentrelay

import kotlinx.serialization.Serializable

/**
 * Phase 15 - relay wire frames (device side).
 *
 * The phone always dials out; it is never a public listener. Every inbound
 * `request` frame carries bounded identity/replay fields (device, agent,
 * request, timestamp, nonce) plus an HMAC signature over the canonical
 * payload. The device validates all of them before forwarding anything to
 * the local agent API, which then applies its own bearer/scope/idempotency
 * pipeline unchanged.
 */
object AgentRelayProtocol {
    const val VERSION = 1
    const val MAX_FRAME_BYTES = 256 * 1024
    const val MAX_HEADERS = 64
    const val MAX_TARGET_LENGTH = 4096
    const val DEFAULT_CLOCK_SKEW_MS = 5 * 60 * 1000L
    const val MAX_NONCES = 4096

    val TOKEN_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    val NONCE_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
    val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")

    /** Local routes a relayed request may address. The agent card stays local-only. */
    fun isAllowedTarget(target: String): Boolean {
        val path = target.substringBefore('?')
        return path == "/mcp" ||
            path == "/a2a" ||
            path == "/api/v1/openapi.json" ||
            path == "/api/v1/schemas/task-v1.json" ||
            (path.startsWith("/api/v1/") && path.length <= MAX_TARGET_LENGTH)
    }
}

@Serializable
data class AgentRelayHelloV1(
    val type: String = "hello",
    val v: Int = AgentRelayProtocol.VERSION,
    val deviceId: String,
    val timestamp: Long,
    val nonce: String
)

@Serializable
data class AgentRelayRequestV1(
    val type: String = "request",
    val v: Int = AgentRelayProtocol.VERSION,
    val deviceId: String,
    val agentId: String,
    val requestId: String,
    val timestamp: Long,
    val nonce: String,
    val method: String,
    val target: String,
    val headers: Map<String, String> = emptyMap(),
    /** Base64 (standard) encoded request body, possibly empty. */
    val bodyBase64: String = "",
    /** Base64Url (no padding) HMAC-SHA256 over [canonicalBytes]. */
    val signature: String = ""
) {
    /** Exact bytes covered by [signature]. Fields are pre-validated to exclude '\n'. */
    fun canonicalBytes(): ByteArray = listOf(
        "nexaflow-relay-v1",
        deviceId,
        agentId,
        requestId,
        timestamp.toString(),
        nonce,
        method,
        target,
        headers.toSortedMap().entries.joinToString("&") { (key, value) -> "$key=$value" },
        bodyBase64
    ).joinToString("\n").toByteArray(Charsets.UTF_8)
}

@Serializable
data class AgentRelayResponseV1(
    val type: String = "response",
    val v: Int = AgentRelayProtocol.VERSION,
    val requestId: String,
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    /** Base64 (standard) encoded response body, possibly empty. */
    val bodyBase64: String = ""
)

@Serializable
data class AgentRelayPingV1(
    val type: String = "ping",
    val v: Int = AgentRelayProtocol.VERSION,
    val timestamp: Long
)

@Serializable
data class AgentRelayPongV1(
    val type: String = "pong",
    val v: Int = AgentRelayProtocol.VERSION,
    val timestamp: Long
)
