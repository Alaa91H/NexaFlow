package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTriggerMatcherTest {
    @Test
    fun `legacy singular package filter remains effective`() {
        assertTrue(NotificationTriggerMatcher.matches(mapOf("package" to "com.example.mail"), "com.example.mail", null, null))
        assertFalse(NotificationTriggerMatcher.matches(mapOf("package" to "com.example.mail"), "com.example.chat", null, null))
    }

    @Test
    fun `package list and content filter are both enforced without storing content`() {
        val config = mapOf("packages" to "com.example.mail, com.example.calendar", "contains" to "meeting")
        assertTrue(NotificationTriggerMatcher.matches(config, "com.example.calendar", "Meeting", "at 10"))
        assertFalse(NotificationTriggerMatcher.matches(config, "com.example.chat", "Meeting", "at 10"))
        assertFalse(NotificationTriggerMatcher.matches(config, "com.example.mail", "Dinner", "at 10"))
    }

    @Test
    fun `package list takes precedence when present and content spans title and text`() {
        val config = mapOf("package" to "com.example.old", "packages" to "com.example.new", "contains" to "status ready")
        assertTrue(NotificationTriggerMatcher.matches(config, "com.example.new", "Status", "ready"))
        assertFalse(NotificationTriggerMatcher.matches(config, "com.example.old", "Status", "ready"))
        assertFalse(NotificationTriggerMatcher.matches(config, "com.example.new", "Status", null))
    }
}
