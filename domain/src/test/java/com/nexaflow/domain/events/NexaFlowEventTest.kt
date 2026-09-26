package com.nexaflow.domain.events

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NexaFlowEventTest {

    @Test
    fun `event validates identity timestamp and payload budget`() {
        val event = event()
        assertEquals("event-1", event.eventId)
        assertEquals(NexaFlowEventType.CONNECTIVITY_CHANGED, event.type)

        assertThrows(IllegalArgumentException::class.java) {
            event.copy(eventId = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            event.copy(source = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            event.copy(occurredAt = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            event.copy(
                payload = JsonObject(
                    mapOf("oversized" to JsonPrimitive("x".repeat(NexaFlowEvent.MAX_PAYLOAD_BYTES)))
                )
            )
        }
    }

    @Test
    fun `empty filter matches every event`() {
        assertTrue(EventFilter().matches(event()))
    }

    @Test
    fun `filter independently enforces type source and correlation`() {
        val base = event().copy(correlationId = "corr-1")
        val matching = EventFilter(
            types = setOf(NexaFlowEventType.CONNECTIVITY_CHANGED),
            sources = setOf("connectivity"),
            correlationId = "corr-1"
        )
        assertTrue(matching.matches(base))

        assertFalse(
            matching.copy(types = setOf(NexaFlowEventType.BATTERY_CHANGED)).matches(base)
        )
        assertFalse(matching.copy(sources = setOf("battery")).matches(base))
        assertFalse(matching.copy(correlationId = "corr-2").matches(base))

        assertTrue(
            EventFilter(
                types = setOf(NexaFlowEventType.CONNECTIVITY_CHANGED),
                correlationId = "corr-1"
            ).matches(base)
        )
    }

    @Test
    fun `publish result preserves acceptance diagnostics`() {
        val accepted = EventPublishResult(
            accepted = true,
            deduplicated = false,
            matchedSubscriptions = 3
        )
        assertTrue(accepted.accepted)
        assertFalse(accepted.deduplicated)
        assertEquals(3, accepted.matchedSubscriptions)

        val duplicate = EventPublishResult(
            accepted = false,
            deduplicated = true,
            matchedSubscriptions = 0,
            reason = "duplicate"
        )
        assertTrue(duplicate.deduplicated)
        assertEquals("duplicate", duplicate.reason)
    }

    private fun event() = NexaFlowEvent(
        eventId = "event-1",
        type = NexaFlowEventType.CONNECTIVITY_CHANGED,
        source = "connectivity",
        occurredAt = 100L,
        payload = JsonObject(mapOf("online" to JsonPrimitive(true))),
        deduplicationKey = "network:true"
    )
}
