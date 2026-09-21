package com.nexaflow.wear.data

import com.nexaflow.wear.data.WearProtocol.PATH_AUTOMATIONS
import com.nexaflow.wear.data.WearProtocol.KEY_PAYLOAD
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the watch-side cached-snapshot recovery path (the
 * reference-companion pattern): the watch must be able to decode the phone's
 * last pushed DataItem straight from the local Data Layer cache without any
 * round-trip, so the UI never depends on a live GMS message chain to leave
 * the "Connecting" state.
 */
class WearSyncSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun snapshotUriParityWithPhonePushPath() {
        // The snapshot read resolves "wear://*" + the push path; if either
        // side renames its constant independently the cache read silently
        // finds nothing and the watch falls back to Connecting. The wear
        // module is deliberately standalone, so the protocol constants are
        // duplicated with the phone's AutomationIntents — this test pins the
        // wire format that must stay in sync.
        assertEquals("/nexaflow/automations", PATH_AUTOMATIONS)
        assertTrue(PATH_AUTOMATIONS.startsWith("/nexaflow/"))
    }

    @Test
    fun snapshotPayloadKeyParityWithPhonePushKey() {
        assertEquals("payload", KEY_PAYLOAD)
    }

    @Test
    fun snapshotUriStringMatchesTheClientParseShape() {
        // Plain-string mirror of the Uri the client builds (Uri.parse needs
        // an Android runtime to assert here): the read URI is the wear scheme,
        // a wildcard host, and the push path — exactly what the Data Layer
        // scopes cached items by.
        assertEquals("wear://*/nexaflow/automations", "wear://*$PATH_AUTOMATIONS")
    }

    @Test
    fun cachedPayloadDecodesWithRepositoryDecoder() {
        // The snapshot callback feeds payloads into the same Json decoder the
        // DATA_CHANGED listener uses — one decode contract, both entry points.
        val payload = json.encodeToString(
            listOf(
                WearAutomationDto(
                    id = "auto-1",
                    name = "Charging at night",
                    icon = "bolt",
                    iconColor = 0xFF000000L,
                    enabled = true
                )
            )
        )
        val decoded = json.decodeFromString<List<WearAutomationDto>>(payload)
        assertEquals(1, decoded.size)
        assertEquals("auto-1", decoded.first().id)
    }
}
