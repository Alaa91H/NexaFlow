package com.nexaflow.core.agentapi

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventHubTest {

    @Test
    fun ringIsBoundedAndMarksOldCursorTruncated() {
        var now = 100L
        val hub = AgentEventHub(
            streamId = "stream",
            nowMillis = { now++ },
            capacity = 2
        )

        hub.publish(AgentEventTypeV1.AUTOMATION_CREATED, automationId = "a")
        hub.publish(AgentEventTypeV1.AUTOMATION_UPDATED, automationId = "a")
        hub.publish(AgentEventTypeV1.AUTOMATION_COMPLETED, automationId = "a")

        val batch = hub.snapshot(AgentEventQuery(afterSequence = 0L, limit = 10))

        assertEquals(listOf(2L, 3L), batch.events.map { it.sequence })
        assertEquals(2L, batch.oldestSequence)
        assertEquals(3L, batch.latestSequence)
        assertTrue(batch.truncated)
    }

    @Test
    fun streamMismatchResetsCursor() {
        val hub = AgentEventHub(streamId = "new-stream")
        hub.publish(AgentEventTypeV1.CAPABILITY_CHANGED)

        val batch = hub.snapshot(
            AgentEventQuery(
                streamId = "old-stream",
                afterSequence = 99L
            )
        )

        assertTrue(batch.reset)
        assertEquals(1, batch.events.size)
        assertEquals("new-stream", batch.streamId)
    }

    @Test
    fun awaitWakesWhenNewEventArrives() = runTest {
        val hub = AgentEventHub(streamId = "stream")
        val waiting = async {
            hub.await(
                AgentEventQuery(
                    streamId = "stream",
                    afterSequence = 0L,
                    waitMs = 1_000L
                )
            )
        }

        hub.publish(
            AgentEventTypeV1.AUTOMATION_COMPLETED,
            automationId = "a",
            executionId = "run"
        )

        val batch = waiting.await()
        assertFalse(batch.reset)
        assertEquals(1, batch.events.size)
        assertEquals("run", batch.events.single().executionId)
    }

    @Test
    fun attributesAreBounded() {
        val hub = AgentEventHub(streamId = "stream")
        val attributes = (1..30).associate {
            "key-$it" to "x".repeat(1_000)
        }

        val event = hub.publish(
            AgentEventTypeV1.DEVICE_STATE_CHANGED,
            attributes = attributes
        )

        assertEquals(AgentEventHub.MAX_ATTRIBUTES, event.attributes.size)
        assertTrue(
            event.attributes.values.all {
                it.length <= AgentEventHub.MAX_ATTRIBUTE_VALUE_LENGTH
            }
        )
    }
}
