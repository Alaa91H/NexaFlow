package com.nexaflow.core.execution.events

import com.nexaflow.core.execution.compat.EventSource
import com.nexaflow.domain.events.EventFilter
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventType
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryNexaFlowEventBusTest {

    private fun event(
        id: String = UUID.randomUUID().toString(),
        type: NexaFlowEventType = NexaFlowEventType.BATTERY_CHANGED,
        source: String = "battery",
        correlationId: String? = null,
        deduplicationKey: String? = null
    ) = NexaFlowEvent(
        eventId = id,
        type = type,
        source = source,
        occurredAt = 1_000L,
        payload = JsonObject(mapOf("level" to JsonPrimitive(80))),
        correlationId = correlationId,
        deduplicationKey = deduplicationKey
    )

    @Test
    fun `publish routes matching events in order and filters unrelated sources`() = runTest {
        val bus = InMemoryNexaFlowEventBus(scope = this)
        val received = mutableListOf<String>()
        bus.subscribe(
            filter = EventFilter(
                types = setOf(NexaFlowEventType.BATTERY_CHANGED),
                sources = setOf("battery")
            )
        ) { received += it.eventId }

        val first = event(id = "first")
        val ignored = event(id = "ignored", source = "connectivity")
        val second = event(id = "second")
        assertEquals(1, bus.publish(first).matchedSubscriptions)
        assertEquals(0, bus.publish(ignored).matchedSubscriptions)
        assertEquals(1, bus.publish(second).matchedSubscriptions)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), received)
        bus.close()
    }

    @Test
    fun `deduplication rejects repeated key until its window expires`() = runTest {
        var now = 10_000L
        val bus = InMemoryNexaFlowEventBus(
            scope = this,
            nowMs = { now },
            deduplicationWindowMs = 1_000L
        )
        var received = 0
        bus.subscribe { received++ }

        val original = bus.publish(event(id = "one", deduplicationKey = "battery:80"))
        val duplicate = bus.publish(event(id = "two", deduplicationKey = "battery:80"))
        now += 1_000L
        val afterWindow = bus.publish(event(id = "three", deduplicationKey = "battery:80"))
        advanceUntilIdle()

        assertTrue(original.accepted)
        assertFalse(duplicate.accepted)
        assertTrue(duplicate.deduplicated)
        assertTrue(afterWindow.accepted)
        assertEquals(2, received)
        bus.close()
    }

    @Test
    fun `unsubscribe atomically removes listener before later publish`() = runTest {
        val bus = InMemoryNexaFlowEventBus(scope = this)
        var received = 0
        val subscription = bus.subscribe { received++ }

        assertTrue(bus.publish(event(id = "before")).accepted)
        advanceUntilIdle()
        assertTrue(bus.unsubscribe(subscription.id))
        assertFalse(bus.unsubscribe(subscription.id))
        assertTrue(bus.publish(event(id = "after")).accepted)
        advanceUntilIdle()

        assertEquals(1, received)
        bus.close()
    }

    @Test
    fun `subscriber failure is isolated from other subscriptions`() = runTest {
        val failures = mutableListOf<String>()
        val bus = InMemoryNexaFlowEventBus(
            scope = this,
            onSubscriberFailure = { id, _ -> failures += id }
        )
        var healthyCalls = 0
        bus.subscribe { error("expected listener failure") }
        bus.subscribe { healthyCalls++ }

        assertTrue(bus.publish(event()).accepted)
        advanceUntilIdle()

        assertEquals(1, failures.size)
        assertEquals(1, healthyCalls)
        bus.close()
    }

    @Test
    fun `monitor adapter emits canonical event from existing source`() = runTest {
        val bus = InMemoryNexaFlowEventBus(scope = this)
        val observed = mutableListOf<NexaFlowEvent>()
        bus.subscribe { observed += it }
        val source = object : EventSource {
            override val sourceId: String = "test-monitor"
            override val description: String = "Test monitor"
            override fun start() = Unit
            override fun stop() = Unit
        }
        val adapter = MonitorEventAdapter(
            source = source,
            eventBus = bus,
            eventId = { "canonical-event" },
            nowMs = { 4_200L }
        )

        val result = adapter.publish(
            type = NexaFlowEventType.SYSTEM_EVENT,
            payload = JsonObject(mapOf("state" to JsonPrimitive("changed"))),
            correlationId = "run-7",
            deduplicationKey = "system:changed"
        )
        advanceUntilIdle()

        assertTrue(result.accepted)
        assertEquals(1, observed.size)
        assertEquals("canonical-event", observed.single().eventId)
        assertEquals("test-monitor", observed.single().source)
        assertEquals(4_200L, observed.single().occurredAt)
        assertEquals("run-7", observed.single().correlationId)
        bus.close()
    }
}
