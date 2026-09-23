package com.nexaflow.core.wearprotocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolContractTest {

    @Test
    fun `legacy paths remain stable`() {
        assertEquals("/nexaflow/automations", WearProtocol.PATH_AUTOMATIONS)
        assertEquals("/nexaflow/run", WearProtocol.PATH_RUN_COMMAND)
        assertEquals("/nexaflow/toggle", WearProtocol.PATH_TOGGLE_COMMAND)
        assertEquals("/nexaflow/sync-request", WearProtocol.PATH_SYNC_REQUEST)
    }

    @Test
    fun `version compatibility is bounded`() {
        assertTrue(WearProtocol.isVersionSupported(WearProtocol.CURRENT_VERSION))
        assertFalse(WearProtocol.isVersionSupported(0))
        assertFalse(WearProtocol.isVersionSupported(WearProtocol.CURRENT_VERSION + 1))
    }

    @Test
    fun `envelope round trips with typed metadata`() {
        val input = WearEnvelope(
            messageId = "msg-1",
            timestampEpochMs = 1_000L,
            source = WearEndpoint.WATCH,
            target = WearEndpoint.PHONE,
            kind = WearMessageKind.EVENT,
            requestId = "request-1",
            payload = mapOf("battery" to "18"),
        )

        val encoded = WearProtocolJson.format.encodeToString(input)
        val decoded = WearProtocolJson.format.decodeFromString<WearEnvelope>(encoded)

        assertEquals(input, decoded)
    }

    @Test
    fun `unknown future fields do not break older decoder`() {
        val encoded = """
            {
              "protocolVersion": 1,
              "messageId": "future-1",
              "timestampEpochMs": 1000,
              "source": "WATCH",
              "target": "PHONE",
              "kind": "EVENT",
              "payload": {},
              "futureField": "ignored"
            }
        """.trimIndent()

        val decoded = WearProtocolJson.format.decodeFromString<WearEnvelope>(encoded)

        assertEquals("future-1", decoded.messageId)
        assertEquals(WearMessageKind.EVENT, decoded.kind)
    }

    @Test
    fun `ttl expires only after deadline`() {
        val envelope = WearEnvelope(
            messageId = "ttl-1",
            timestampEpochMs = 10_000L,
            source = WearEndpoint.PHONE,
            target = WearEndpoint.WATCH,
            kind = WearMessageKind.COMMAND,
            ttlMs = 5_000L,
        )

        assertFalse(envelope.isExpired(15_000L))
        assertTrue(envelope.isExpired(15_001L))
    }
}
