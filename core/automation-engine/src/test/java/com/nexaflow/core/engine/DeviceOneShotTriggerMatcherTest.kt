package com.nexaflow.core.engine

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOneShotTriggerMatcherTest {

    @Test
    fun clipboardFilterMatchesOnlyRelevantPayload() {
        val config = mapOf("contains" to "OTP")
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.CLIPBOARD_CHANGED,
                config,
                textValue = "Your otp is 1234",
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.CLIPBOARD_CHANGED,
                config,
                textValue = "unrelated text",
            )
        )
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.CLIPBOARD_CHANGED,
                emptyMap(),
                textValue = null,
            )
        )
    }

    @Test
    fun timezoneFilterIsExactWhenConfigured() {
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.TIMEZONE_CHANGED,
                mapOf("zone" to "Europe/Berlin"),
                textValue = "Europe/Berlin",
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.TIMEZONE_CHANGED,
                mapOf("zone" to "Europe/Berlin"),
                textValue = "Europe/Paris",
            )
        )
    }

    @Test
    fun nfcContainsFilterMatchesTagIdCaseInsensitively() {
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.NFC_TAG_SCANNED,
                mapOf("contains" to "a1b2"),
                textValue = "00A1B2FF",
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.NFC_TAG_SCANNED,
                mapOf("contains" to "DEAD"),
                textValue = "00A1B2FF",
            )
        )
    }

    @Test
    fun screenTimeoutFilterUsesCurrentSecondsWhenConfigured() {
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.SCREEN_TIMEOUT_CHANGED,
                mapOf("seconds" to "30"),
                numericValue = 30L,
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.SCREEN_TIMEOUT_CHANGED,
                mapOf("seconds" to "30"),
                numericValue = 60L,
            )
        )
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.SCREEN_TIMEOUT_CHANGED,
                emptyMap(),
                numericValue = 60L,
            )
        )
    }

    @Test
    fun alarmSetAndClearedAreDistinctEvents() {
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                mapOf("event" to "SET"),
                flagValue = true,
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                mapOf("event" to "CLEARED"),
                flagValue = true,
            )
        )
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                mapOf("event" to "CLEARED"),
                flagValue = false,
            )
        )
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                emptyMap(),
                flagValue = true,
            )
        )
        assertTrue(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                emptyMap(),
                flagValue = false,
            )
        )
    }

    @Test
    fun missingRequiredPayloadFailsClosedForFilteredEvents() {
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.NFC_TAG_SCANNED,
                mapOf("contains" to "ABC"),
                textValue = null,
            )
        )
        assertFalse(
            DeviceOneShotTriggerMatcher.matches(
                TriggerType.ALARM_SET_CHANGED,
                mapOf("event" to "SET"),
                flagValue = null,
            )
        )
    }
}
