package com.nexaflow.core.agentrelay

import com.nexaflow.core.agentapi.AgentHttpRequest
import com.nexaflow.core.agentapi.AgentHttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Device-side outbound relay client (Phase 15).
 *
 * The phone dials out to the relay and serves signed requests over that
 * single connection; it never opens a public listener. Every inbound frame
 * is validated (size, version, device binding, timestamp window, nonce
 * uniqueness, HMAC signature, route allow-list, header/body bounds) before
 * it reaches [handler], which is the same local agent API pipeline used by
 * REST/MCP/A2A/Binder, with bearer/scope/idempotency semantics unchanged.
 *
 * Connection loss triggers bounded exponential backoff with jitter until
 * [stop] is called. Heartbeats detect half-open connections.
 */
interface AgentRelayTransport {
    suspend fun connect(url: String)
    suspend fun send(text: String)

    /**
     * Blocks for the next frame. Implementations must throw
     * [AgentRelayReadTimeout] when no frame arrives within their read
     * timeout so the client can heartbeat; returns null only when the peer
     * cleanly closed the connection.
     */
    suspend fun receive(): String?
    suspend fun close()
}

/** Thrown by [AgentRelayTransport.receive] on read timeout (not a failure). */
class AgentRelayReadTimeout : RuntimeException()

interface AgentRelayRequestHandler {
    suspend fun handle(request: AgentHttpRequest): AgentHttpResponse
}

class AgentRelayClient(
    private val linkStore: AgentRelayLinkStore,
    private val replayGuard: AgentRelayReplayGuard = AgentRelayReplayGuard(),
    private val transportFactory: () -> AgentRelayTransport,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val pingIntervalMs: Long = 30_000L,
    private val pongTimeoutMs: Long = 10_000L,
    private val initialBackoffMs: Long = 1_000L,
    private val maxBackoffMs: Long = 60_000L,
    private val jitterMs: () -> Long = { (Math.random() * 500L).toLong() },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
) {
    @Volatile
    private var running = false

    fun isRunning(): Boolean = running

    /**
     * Blocks serving the relay until [stop] or coroutine cancellation.
     * Returns normally after [stop]; throws only on unexpected errors when
     * the retry budget is misconfigured (there is none: retries are
     * unbounded while running).
     */
    suspend fun start(url: String, deviceId: String, handler: AgentRelayRequestHandler) {
        require(url.isNotBlank()) { "Relay url must not be blank" }
        require(AgentRelayProtocol.TOKEN_PATTERN.matches(deviceId)) {
            "Device id has an invalid format"
        }
        val linkKey = linkStore.linkKey()
            ?: throw IllegalStateException("Relay link key is not provisioned")
        running = true
        var backoff = initialBackoffMs
        try {
            while (running) {
                try {
                    serve(url, deviceId, linkKey, handler)
                    backoff = initialBackoffMs
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (!running) return
                    delay(backoff + jitterMs().coerceAtLeast(0L))
                    backoff = (backoff * 2L).coerceAtMost(maxBackoffMs)
                }
            }
        } finally {
            running = false
        }
    }

    fun stop() {
        running = false
    }

    private suspend fun serve(
        url: String,
        deviceId: String,
        linkKey: ByteArray,
        handler: AgentRelayRequestHandler
    ) {
        val transport = transportFactory()
        try {
            transport.connect(url)
            transport.send(
                json.encodeToString(
                    AgentRelayHelloV1(
                        deviceId = deviceId,
                        timestamp = clockMillis(),
                        nonce = idGenerator()
                    )
                )
            )
            var lastSeen = clockMillis()
            var pingSentAt = 0L
            while (running) {
                val frame = try {
                    transport.receive()
                } catch (_: AgentRelayReadTimeout) {
                    val now = clockMillis()
                    if (pingSentAt == 0L && now - lastSeen >= pingIntervalMs) {
                        transport.send(
                            json.encodeToString(AgentRelayPingV1(timestamp = now))
                        )
                        pingSentAt = now
                    } else if (pingSentAt != 0L && now - pingSentAt >= pongTimeoutMs) {
                        return
                    }
                    continue
                } ?: return
                lastSeen = clockMillis()
                pingSentAt = 0L
                if (frame.toByteArray(StandardCharsets.UTF_8).size > AgentRelayProtocol.MAX_FRAME_BYTES) {
                    continue
                }
                dispatch(frame, deviceId, linkKey, handler, transport)
            }
        } finally {
            runCatching { transport.close() }
        }
    }

    private suspend fun dispatch(
        frame: String,
        deviceId: String,
        linkKey: ByteArray,
        handler: AgentRelayRequestHandler,
        transport: AgentRelayTransport
    ) {
        val body = runCatching {
            json.parseToJsonElement(frame)
        }.getOrNull() as? JsonObject
            ?: return
        // Encoders may omit default-valued fields such as "type"; a frame
        // carrying request markers is treated as a request so default
        // omission can never silently drop work.
        when (val kind = body["type"]?.jsonPrimitive?.contentOrNull) {
            "pong" -> Unit
            "ping" -> transport.send(
                json.encodeToString(AgentRelayPongV1(timestamp = clockMillis()))
            )
            "request" -> {
                val response = handleRequest(frame, deviceId, linkKey, handler)
                transport.send(json.encodeToString(response))
            }
            null -> {
                if (looksLikeRequest(body)) {
                    val response = handleRequest(frame, deviceId, linkKey, handler)
                    transport.send(json.encodeToString(response))
                }
            }
            else -> Unit
        }
    }

    private fun looksLikeRequest(body: JsonObject): Boolean =
        body.containsKey("signature") &&
            body.containsKey("requestId") &&
            body.containsKey("method")

    internal suspend fun handleRequest(
        frame: String,
        deviceId: String,
        linkKey: ByteArray,
        handler: AgentRelayRequestHandler
    ): AgentRelayResponseV1 {
        val envelope = try {
            json.decodeFromString<AgentRelayRequestV1>(frame)
        } catch (_: SerializationException) {
            return errorResponse(requestIdOf(frame), 400, "invalid_envelope")
        } catch (_: IllegalArgumentException) {
            return errorResponse(requestIdOf(frame), 400, "invalid_envelope")
        }
        if (envelope.v != AgentRelayProtocol.VERSION) {
            return errorResponse(envelope.requestId, 400, "unsupported_version")
        }
        if (envelope.deviceId != deviceId) {
            return errorResponse(envelope.requestId, 403, "device_mismatch")
        }
        if (!AgentRelayProtocol.TOKEN_PATTERN.matches(envelope.agentId) ||
            !AgentRelayProtocol.TOKEN_PATTERN.matches(envelope.requestId)
        ) {
            return errorResponse(envelope.requestId, 400, "invalid_identity")
        }
        if (envelope.method !in AgentRelayProtocol.ALLOWED_METHODS ||
            !isValidTarget(envelope.target) ||
            !AgentRelayProtocol.isAllowedTarget(envelope.target)
        ) {
            return errorResponse(envelope.requestId, 404, "not_found")
        }
        if (!validHeaders(envelope.headers)) {
            return errorResponse(envelope.requestId, 431, "headers_too_large")
        }
        val body = try {
            if (envelope.bodyBase64.isEmpty()) ByteArray(0)
            else Base64.getDecoder().decode(envelope.bodyBase64)
        } catch (_: IllegalArgumentException) {
            return errorResponse(envelope.requestId, 400, "invalid_body")
        }
        if (body.size > AgentRelayProtocol.MAX_FRAME_BYTES) {
            return errorResponse(envelope.requestId, 413, "payload_too_large")
        }
        when (replayGuard.check(envelope.timestamp, envelope.nonce)) {
            AgentRelayReplay.Stale -> return errorResponse(envelope.requestId, 408, "stale_request")
            AgentRelayReplay.Replay -> return errorResponse(envelope.requestId, 409, "replay_rejected")
            AgentRelayReplay.InvalidNonce -> return errorResponse(envelope.requestId, 400, "invalid_nonce")
            AgentRelayReplay.Accepted -> Unit
        }
        if (!AgentRelaySigner.verify(linkKey, envelope.canonicalBytes(), envelope.signature)) {
            return errorResponse(envelope.requestId, 401, "invalid_signature")
        }
        replayGuard.record(envelope.nonce)

        val headers = LinkedHashMap<String, String>()
        headers["host"] = "127.0.0.1"
        envelope.headers.forEach { (name, value) ->
            headers[name.lowercase(java.util.Locale.US)] = value
        }
        val response = try {
            handler.handle(
                AgentHttpRequest(
                    method = envelope.method,
                    target = envelope.target,
                    headers = headers,
                    body = body
                )
            )
        } catch (_: Exception) {
            return errorResponse(envelope.requestId, 502, "handler_failed")
        }
        return AgentRelayResponseV1(
            requestId = envelope.requestId,
            status = response.status,
            headers = response.headers,
            bodyBase64 = if (response.body.isEmpty()) "" else
                Base64.getEncoder().encodeToString(response.body)
        )
    }

    private fun isValidTarget(target: String): Boolean {
        if (!target.startsWith("/") || target.length > AgentRelayProtocol.MAX_TARGET_LENGTH) {
            return false
        }
        return target.none { it == '\n' || it == '\r' || it == ' ' }
    }

    private fun validHeaders(headers: Map<String, String>): Boolean {
        if (headers.size > AgentRelayProtocol.MAX_HEADERS) return false
        return headers.all { (name, value) ->
            name.isNotEmpty() && name.length <= 128 &&
                value.length <= 8192 &&
                name.none { it == '\n' || it == '\r' } &&
                value.none { it == '\n' || it == '\r' }
        }
    }

    private fun requestIdOf(frame: String): String = runCatching {
        json.parseToJsonElement(frame)
            .let { it as? JsonObject }
            ?.get("requestId")?.jsonPrimitive?.contentOrNull
            ?.takeIf { AgentRelayProtocol.TOKEN_PATTERN.matches(it) }
    }.getOrNull() ?: "unknown"

    private fun errorResponse(requestId: String, status: Int, code: String) =
        AgentRelayResponseV1(
            requestId = requestId,
            status = status,
            bodyBase64 = Base64.getEncoder().encodeToString(
                """{"error":{"code":"$code"}}""".toByteArray(StandardCharsets.UTF_8)
            )
        )
}
