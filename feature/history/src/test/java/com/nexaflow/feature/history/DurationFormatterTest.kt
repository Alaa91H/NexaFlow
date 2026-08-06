package com.nexaflow.feature.history

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun subSecond_usesMs() {
        assertEquals("820 ms", formatDuration(820, "ms", "s"))
        assertEquals("0 ms", formatDuration(0, "ms", "s"))
    }

    @Test
    fun exactlyOneSecond_usesSeconds() {
        assertEquals("1.0 s", formatDuration(1000, "ms", "s"))
    }

    @Test
    fun multiSecond_roundsToOneDecimal() {
        assertEquals("1.4 s", formatDuration(1420, "ms", "s"))
        assertEquals("5.0 s", formatDuration(5000, "ms", "s"))
    }

    @Test
    fun localizedUnitLabelsAreUsed() {
        assertEquals("820 ms", formatDuration(820, "ms", "s"))
        assertEquals("820 مللي ثانية", formatDuration(820, "مللي ثانية", "ثانية"))
        assertEquals("1.4 ثانية", formatDuration(1420, "مللي ثانية", "ثانية"))
    }

    @Test
    fun largeDurations_keepSecondsPrecision() {
        assertEquals("90.0 s", formatDuration(90000, "ms", "s"))
        assertEquals("3600.0 s", formatDuration(3_600_000, "ms", "s"))
    }
}
