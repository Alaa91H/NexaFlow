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
    fun `typed command result event and device snapshot round trip`() {
        val command = WearCommandRequest(
            requestId = "req-1",
            command = WearCommandKind.SET_AUTOMATION_ENABLED,
            automationId = "automation-1",
            enabled = true,
            arguments = mapOf("source" to "tile"),
        )
        val result = WearCommandResult(
            requestId = "req-1",
            status = WearCommandStatus.SUCCESS,
            executedAtEpochMs = 2_000L,
            message = "ok",
            data = mapOf("watch" to "watch-1"),
        )
        val event = WearEvent(
            eventId = "event-1",
            kind = WearEventKind.BATTERY_LEVEL_CHANGED,
            occurredAtEpochMs = 3_000L,
            state = "BELOW",
            data = mapOf("level" to "18"),
        )
        val device = WearDeviceDescriptor(
            watchInstallId = "watch-1",
            nodeId = "node-1",
            displayName = "Test watch",
            appVersionName = "3.87.0",
            appVersionCode = 38700L,
            wearOsSdk = 36,
            capabilities = WearCapability.entries.toSet(),
            lastSeenEpochMs = 4_000L,
        )
        val capabilities = WearCapabilitySnapshot(
            watchInstallId = "watch-1",
            capabilities = WearCapability.entries.toSet(),
            permissions = mapOf("BODY_SENSORS" to true),
            updatedAtEpochMs = 5_000L,
        )

        assertEquals(
            command,
            WearProtocolJson.format.decodeFromString<WearCommandRequest>(
                WearProtocolJson.format.encodeToString(command),
            ),
        )
        assertEquals(
            result,
            WearProtocolJson.format.decodeFromString<WearCommandResult>(
                WearProtocolJson.format.encodeToString(result),
            ),
        )
        assertEquals(
            event,
            WearProtocolJson.format.decodeFromString<WearEvent>(
                WearProtocolJson.format.encodeToString(event),
            ),
        )
        assertEquals(
            device,
            WearProtocolJson.format.decodeFromString<WearDeviceDescriptor>(
                WearProtocolJson.format.encodeToString(device),
            ),
        )
        assertEquals(
            capabilities,
            WearProtocolJson.format.decodeFromString<WearCapabilitySnapshot>(
                WearProtocolJson.format.encodeToString(capabilities),
            ),
        )
    }

    @Test
    fun `versioned paths are namespaced and distinct`() {
        val paths = listOf(
            WearProtocol.PATH_COMMAND_V1,
            WearProtocol.PATH_EVENT_V1,
            WearProtocol.PATH_RESULT_V1,
            WearProtocol.PATH_DEVICE_STATE_V1,
            WearProtocol.PATH_CAPABILITIES_V1,
        )

        assertEquals(paths.size, paths.toSet().size)
        assertTrue(paths.all { it.startsWith("/nexaflow/v1/") })
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