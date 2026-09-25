package com.nexaflow.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerOccurrenceDeduplicatorTest {

    private fun occurrence(
        eventId: String? = "event-1",
        sourceId: String = "test",
    ): TriggerOccurrence = TriggerOccurrence.single(
        triggerIndex = 0,
        occurredAtEpochMs = 100L,
        sourceId = sourceId,
        eventId = eventId,
    )

    @Test
    fun sameIdentifiedOccurrenceIsRejectedInsideWindow() {
        val deduplicator = TriggerOccurrenceDeduplicator(windowMs = 1_000L)

        assertTrue(deduplicator.tryAdmit("a", occurrence(), now = 100L))
        assertFalse(deduplicator.tryAdmit("a", occurrence(), now = 500L))
    }

    @Test
    fun sameEventIdIsIndependentAcrossAutomationsAndSources() {
        val deduplicator = TriggerOccurrenceDeduplicator(windowMs = 1_000L)

        assertTrue(deduplicator.tryAdmit("a", occurrence(sourceId = "sms"), now = 100L))
        assertTrue(deduplicator.tryAdmit("b", occurrence(sourceId = "sms"), now = 101L))
        assertTrue(deduplicator.tryAdmit("a", occurrence(sourceId = "webhook"), now = 102L))
    }

    @Test
    fun occurrenceMayBeAdmittedAgainAfterWindowExpires() {
        val deduplicator = TriggerOccurrenceDeduplicator(windowMs = 1_000L)

        assertTrue(deduplicator.tryAdmit("a", occurrence(), now = 100L))
        assertTrue(deduplicator.tryAdmit("a", occurrence(), now = 1_100L))
    }

    @Test
    fun missingEventIdentityIsNeverGuessedOrDeduplicated() {
        val deduplicator = TriggerOccurrenceDeduplicator(windowMs = 1_000L)
        val anonymous = occurrence(eventId = null)

        assertTrue(deduplicator.tryAdmit("a", anonymous, now = 100L))
        assertTrue(deduplicator.tryAdmit("a", anonymous, now = 101L))
        assertEquals(0, deduplicator.sizeForTest())
    }
}
