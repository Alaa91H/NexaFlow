package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTriggerMatcherTest {

    private fun automation(
        id: String = "a1",
        from: String = "",
        contains: String = ""
    ): Automation = Automation(
        id = id,
        name = "Task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(
            Trigger(TriggerType.SMS, buildMap {
                if (from.isNotEmpty()) put("from", from)
                if (contains.isNotEmpty()) put("contains", contains)
            })
        ),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun matches_blankFilters_matchAnything() {
        val config = mapOf("from" to "", "contains" to "")
        assertTrue(SmsTriggerMatcher.matches(config, "+15551234567", "hello world"))
    }

    @Test
    fun matches_fromFilter_matchesSubstringCaseInsensitive() {
        val config = mapOf("from" to "GOOGLE")
        assertTrue(SmsTriggerMatcher.matches(config, "GOOGLE", "code 1234"))
        assertTrue(SmsTriggerMatcher.matches(config, "google", "code 1234"))
        assertTrue(SmsTriggerMatcher.matches(config, "+1-google-svc", "code 1234"))
        assertFalse(SmsTriggerMatcher.matches(config, "BANK", "code 1234"))
    }

    @Test
    fun matches_containsFilter_matchesBodySubstring() {
        val config = mapOf("contains" to "nexa")
        assertTrue(SmsTriggerMatcher.matches(config, "+1555", "run nexaflow now"))
        assertFalse(SmsTriggerMatcher.matches(config, "+1555", "nothing here"))
    }

    @Test
    fun matches_bothFilters_requireBoth() {
        val config = mapOf("from" to "BANK", "contains" to "OTP")
        assertTrue(SmsTriggerMatcher.matches(config, "BANK-OPS", "Your OTP is 1234"))
        assertFalse(SmsTriggerMatcher.matches(config, "OTHER", "Your OTP is 1234"))
        assertFalse(SmsTriggerMatcher.matches(config, "BANK-OPS", "No code here"))
    }

    @Test
    fun matchingAutomations_returnsOnlyEnabledMatching() {
        val match = automation(from = "BANK", contains = "otp")
        val disabled = automation(id = "a2", from = "BANK").copy(enabled = false)
        val other = automation(id = "a3", contains = "weather")
        val result = SmsTriggerMatcher.matchingAutomations(
            listOf(match, disabled, other),
            "BANK", "Your OTP is 42"
        )
        assertEquals(listOf("a1"), result.map { it.id })
    }

    @Test
    fun matchingAutomations_emptyList_returnsEmpty() {
        assertTrue(SmsTriggerMatcher.matchingAutomations(emptyList(), "x", "y").isEmpty())
    }

    @Test
    fun replyOf_returnsFirstSmsTriggerReply() {
        val withReply = automation(from = "BANK").copy(
            triggers = listOf(
                Trigger(TriggerType.SMS, mapOf("from" to "BANK", "reply" to "Thanks"))
            )
        )
        assertEquals("Thanks", SmsTriggerMatcher.replyOf(withReply))
        assertNull(SmsTriggerMatcher.replyOf(automation()))
    }
}
