package com.nexaflow.core.agentrelay

import com.nexaflow.core.agentapi.AgentHttpRequest
import com.nexaflow.core.agentapi.AgentHttpResponse
import com.nexaflow.core.security.SecureStorage
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRelayClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun signedRequestIsForwardedToHandler() = runTest {
        val fixture = fixture()
        val frame = signedFrame(
            fixture,
            method = "GET",
            target = "/api/v1/status",
            headers = mapOf("authorization" to "Bearer token-1")
        )

        val response = fixture.client.handleRequest(
            frame = json.encodeToString(frame),
            deviceId = "device-1",
            linkKey = fixture.key,
            handler = fixture.handler
        )

        assertEquals(200, response.status)
        assertEquals(1, fixture.handler.calls)
        assertEquals("GET", fixture.handler.last!!.method)
        assertEquals("/api/v1/status", fixture.handler.last!!.target)
        assertEquals("Bearer token-1", fixture.handler.last!!.header("authorization"))
    }

    @Test
    fun replayedNonceIsRejectedWithoutForwarding() = runTest {
        val fixture = fixture()
        val raw = json.encodeToString(signedFrame(fixture))

        fixture.client.handleRequest(raw, "device-1", fixture.key, fixture.handler)
        val replay = fixture.client.handleRequest(raw, "device-1", fixture.key, fixture.handler)

        assertEquals(409, replay.status)
        assertEquals(1, fixture.handler.calls)
    }

    @Test
    fun tamperedSignatureIsRejected() = runTest {
        val fixture = fixture()
        val frame = signedFrame(fixture).copy(signature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

        val response = fixture.client.handleRequest(
            json.encodeToString(frame), "device-1", fixture.key, fixture.handler
        )

        assertEquals(401, response.status)
        assertEquals(0, fixture.handler.calls)
    }

    @Test
    fun wrongDeviceBindingIsRejected() = runTest {
        val fixture = fixture()
        val frame = signedFrame(fixture, deviceId = "device-2")

        val response = fixture.client.handleRequest(
            json.encodeToString(frame), "device-1", fixture.key, fixture.handler
        )

        assertEquals(403, response.status)
        assertEquals(0, fixture.handler.calls)
    }

    @Test
    fun disallowedRoutesNeverReachTheHandler() = runTest {
        val fixture = fixture()
        val frame = signedFrame(fixture, target = "/.well-known/agent-card.json")

        val response = fixture.client.handleRequest(
            json.encodeToString(frame), "device-1", fixture.key, fixture.handler
        )

        assertEquals(404, response.status)
        assertEquals(0, fixture.handler.calls)
    }

    @Test
    fun staleTimestampsAreRejected() = runTest {
        val fixture = fixture()
        val frame = signedFrame(fixture, timestamp = 0L)

        val response = fixture.client.handleRequest(
            json.encodeToString(frame), "device-1", fixture.key, fixture.handler
        )

        assertEquals(408, response.status)
        assertEquals(0, fixture.handler.calls)
    }

    @Test
    fun malformedEnvelopesFailClosed() = runTest {
        val fixture = fixture()

        val response = fixture.client.handleRequest(
            "{not json", "device-1", fixture.key, fixture.handler
        )

        assertEquals(400, response.status)
        assertEquals(0, fixture.handler.calls)
    }

    @Test
    fun handlerResponsesAreFramed() = runTest {
        val fixture = fixture(
            handlerBody = "hello".toByteArray(StandardCharsets.UTF_8),
            handlerStatus = 201
        )
        val frame = signedFrame(fixture)

        val response = fixture.client.handleRequest(
            json.encodeToString(frame), "device-1", fixture.key, fixture.handler
        )

        assertEquals(201, response.status)
        assertEquals(
            "hello",
            String(Base64.getDecoder().decode(response.bodyBase64), StandardCharsets.UTF_8)
        )
    }

    @Test
    fun linkStoreProvisionsRotatesAndDeprovisions() = runTest {
        val store = AgentRelayLinkStore(InMemorySecureStorage())

        assertFalse(store.isProvisioned())
        val first = store.provision()
        assertTrue(store.isProvisioned())
        assertTrue(store.linkKey()!!.contentEquals(first))
        val second = store.rotate()
        assertFalse(second.contentEquals(first))
        store.deprovision()
        assertFalse(store.isProvisioned())
    }

    @Test
    fun serveLoopSendsHelloForwardsRequestsAndReconnectsOnClose() = runTest {
        val fixture = fixture()
        val frame = json.encodeToString(signedFrame(fixture))
        val sent = ArrayList<String>()
        var connects = 0
        // LinkedList (unlike ArrayDeque) permits null queue entries, which the
        // fake transport uses to model a clean peer close.
        val queue = java.util.LinkedList<String?>()
        val transport = object : AgentRelayTransport {
            override suspend fun connect(url: String) {
                connects += 1
                if (connects == 1) {
                    queue.add(frame)
                    queue.add(null)
                } else {
                    throw kotlinx.coroutines.CancellationException("test done")
                }
            }

            override suspend fun send(text: String) {
                sent += text
            }

            override suspend fun receive(): String? = queue.removeFirst()

            override suspend fun close() = Unit
        }
        val reconnecting = AgentRelayClient(
            linkStore = AgentRelayLinkStore(InMemorySecureStorage()).also {
                assertTrue(it.import(AgentRelaySigner.encodeKey(fixture.key)))
            },
            replayGuard = AgentRelayReplayGuard(clockMillis = { NOW }),
            transportFactory = { transport },
            clockMillis = { NOW },
            idGenerator = { "generated-nonce" },
            jitterMs = { 0L }
        )

        try {
            reconnecting.start("wss://relay.example", "device-1", fixture.handler)
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected test termination.
        }

        assertEquals(2, connects)
        assertEquals(1, fixture.handler.calls)
        assertTrue(sent.first().contains("\"hello\""))
    }

    @Test
    fun requestFramesWithoutDefaultedTypeFieldAreStillServed() = runTest {
        val fixture = fixture()
        // The shared test Json omits default-valued fields ("type", "v",
        // "headers", "bodyBase64"); the serve path must not drop such frames.
        val frame = json.encodeToString(signedFrame(fixture))
        assertFalse(frame.contains("\"type\""))

        val response = fixture.client.handleRequest(
            frame, "device-1", fixture.key, fixture.handler
        )

        assertEquals(200, response.status)
        assertEquals(1, fixture.handler.calls)
    }

    private fun fixture(
        handlerBody: ByteArray = ByteArray(0),
        handlerStatus: Int = 200
    ): Fixture {
        val key = AgentRelaySigner.newLinkKey()
        val handler = FakeHandler(handlerBody, handlerStatus)
        val client = AgentRelayClient(
            linkStore = AgentRelayLinkStore(InMemorySecureStorage()),
            replayGuard = AgentRelayReplayGuard(clockMillis = { NOW }),
            transportFactory = { error("no transport in unit tests") },
            clockMillis = { NOW },
            idGenerator = { "generated-nonce" },
            jitterMs = { 0L }
        )
        return Fixture(client, key, handler)
    }

    private fun signedFrame(
        fixture: Fixture,
        deviceId: String = "device-1",
        method: String = "GET",
        target: String = "/api/v1/status",
        headers: Map<String, String> = emptyMap(),
        timestamp: Long = NOW
    ): AgentRelayRequestV1 {
        val unsigned = AgentRelayRequestV1(
            deviceId = deviceId,
            agentId = "agent-1",
            requestId = "req-${nonceCounter++}",
            timestamp = timestamp,
            nonce = "nonce-${nonceCounter}",
            method = method,
            target = target,
            headers = headers
        )
        return unsigned.copy(
            signature = AgentRelaySigner.sign(fixture.key, unsigned.canonicalBytes())
        )
    }

    private var nonceCounter = 0L

    private data class Fixture(
        val client: AgentRelayClient,
        val key: ByteArray,
        val handler: FakeHandler
    )

    private class FakeHandler(
        private val body: ByteArray,
        private val status: Int = 200
    ) : AgentRelayRequestHandler {
        var calls = 0
        var last: AgentHttpRequest? = null

        override suspend fun handle(request: AgentHttpRequest): AgentHttpResponse {
            calls += 1
            last = request
            return AgentHttpResponse(status, body, mapOf("Content-Type" to "application/json"))
        }
    }

    private class InMemorySecureStorage : SecureStorage {
        private val values = HashMap<String, String>()
        override suspend fun get(key: String): String? = values[key]
        override suspend fun put(key: String, value: String) {
            values[key] = value
        }
        override suspend fun remove(key: String) {
            values.remove(key)
        }
        override suspend fun clear() {
            values.clear()
        }
    }

    private companion object {
        const val NOW = 5_000_000L
    }
}
