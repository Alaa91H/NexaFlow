package com.nexaflow.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Six 4-hour buckets, proportional bars, empty buckets at zero. */
class SparklineTest {

    @Test
    fun bucketsGroupHoursIntoFourHourWindows() {
        val counts = executionBuckets(listOf(0, 3, 4, 11, 23))
        assertEquals(6, counts.size)
        assertEquals(listOf(2, 1, 1, 0, 0, 1), counts)
    }

    @Test
    fun outOfRangeHoursAreIgnored() {
        assertEquals(List(6) { 0 }, executionBuckets(listOf(-1, 24, 99)))
    }

    @Test
    fun fractionsAreRelativeToTheBusiestBucket() {
        assertEquals(
            listOf(0f, 0.5f, 1f, 0f, 0.25f, 0f),
            sparklineFractions(listOf(0, 2, 4, 0, 1, 0))
        )
    }

    @Test
    fun anEmptyDayHasNoBars() {
        assertEquals(List(6) { 0f }, sparklineFractions(List(6) { 0 }))
    }
}
