package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Pure webhook matching logic — no live socket needed. */
@RunWith(JUnit4::class)
class WebhookTriggerMatcherTest {

    @Test
    fun matches_pathAndDefaultMethod() {
        val config = mapOf("path" to "/nexaflow")
        // Default method is ANY — path is what matters.
        assertTrue(WebhookTriggerMatcher.matches(config, "GET", "/nexaflow", null))
        assertTrue(WebhookTriggerMatcher.matches(config, "POST", "/nexaflow", null))
        assertFalse(WebhookTriggerMatcher.matches(config, "POST", "/other", null))
    }

    @Test
    fun matches_restrictsMethod() {
        val config = mapOf("path" to "/run", "method" to "POST")
        assertTrue(WebhookTriggerMatcher.matches(config, "POST", "/run", null))
        assertFalse(WebhookTriggerMatcher.matches(config, "GET", "/run", null))
    }

    @Test
    fun matches_requiresTokenWhenConfigured() {
        val config = mapOf("path" to "/run", "method" to "ANY", "token" to "s3cret")
        assertTrue(WebhookTriggerMatcher.matches(config, "POST", "/run", "s3cret"))
        assertFalse(WebhookTriggerMatcher.matches(config, "POST", "/run", null))
        assertFalse(WebhookTriggerMatcher.matches(config, "POST", "/run", "wrong"))
        // Blank token = no auth required.
        assertTrue(WebhookTriggerMatcher.matches(mapOf("path" to "/run"), "POST", "/run", null))
    }

    @Test
    fun matches_blankPathDefaultsToRoot() {
        assertTrue(WebhookTriggerMatcher.matches(emptyMap(), "GET", "/", null))
        assertFalse(WebhookTriggerMatcher.matches(emptyMap(), "GET", "/x", null))
    }

    @Test
    fun webhookAutomations_filtersEnabled() {
        val automation = Automation(
            id = "w1",
            name = "Webhook",
            description = "",
            icon = "web",
            iconColor = 0,
            backgroundColor = 0,
            category = "",
            priority = 0,
            enabled = true,
            triggers = listOf(Trigger(TriggerType.WEBHOOK, mapOf("path" to "/x"))),
            actions = emptyList(),
            createdAt = 0,
            updatedAt = 0
        )
        val disabled = automation.copy(id = "w2", enabled = false)
        val timeOnly = automation.copy(
            id = "w3",
            triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00")))
        )
        val result = WebhookTriggerMatcher.webhookAutomations(listOf(automation, disabled, timeOnly))
        assertEquals(listOf("w1"), result.map { it.id })
    }
}
