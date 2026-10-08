package com.nexaflow.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class TriggerOccurrenceDeduplicatorTest {

    @Test
    fun `communication event values never appear in occurrence diagnostics`() {
        val occurrence = TriggerOccurrence.single(
            triggerIndex = 0,
            occurredAtEpochMs = 1L,
            sourceId = "sms",
            eventData = mapOf("sms.sender" to "+15551234567"),
        )

        assertFalse(occurrence.toString().contains("15551234567"))
        assertFalse(occurrence.toString().contains("sms.sender"))
    }

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

    @Test
    fun durableKeyUsesOnlyStableAutomationSourceAndEventIdentity() {
        val event = occurrence(eventId = "physical-event", sourceId = "sms")
        val key = TriggerOccurrenceDeduplicator.durableOccurrenceKey("automation-a", event)

        assertTrue(key!!.matches(Regex("[a-f0-9]{64}")))
        assertEquals(
            key,
            TriggerOccurrenceDeduplicator.durableOccurrenceKey(
                "automation-a",
                event.copy(matchedTriggerIndices = setOf(1))
            )
        )
        assertTrue(
            key != TriggerOccurrenceDeduplicator.durableOccurrenceKey(
                "automation-a",
                event.copy(sourceId = "webhook")
            )
        )
        assertTrue(
            TriggerOccurrenceDeduplicator.durableOccurrenceKey(
                "automation-a",
                occurrence(eventId = null)
            ) == null
        )
        assertTrue(
            TriggerOccurrenceDeduplicator.durableOccurrenceKey(
                "automation-a",
                event.copy(sourceId = null)
            ) == null
        )
    }
}
