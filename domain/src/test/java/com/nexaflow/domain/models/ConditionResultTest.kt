package com.nexaflow.domain.models

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionResultTest {

    @Test
    fun unknownAndUnavailableRemainDistinctFromUnsatisfied() {
        assertNotEquals(ConditionResult.Unsatisfied, ConditionResult.Unknown)
        assertNotEquals(ConditionResult.Unsatisfied, ConditionResult.Unavailable)
        assertNotEquals(ConditionResult.Unknown, ConditionResult.Unavailable)
    }

    @Test
    fun errorRetainsActionableReason() {
        val result = ConditionResult.Error("Plugin receiver returned an invalid result code")

        assertTrue(result.reason.contains("invalid result code"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun errorRejectsBlankReason() {
        ConditionResult.Error(" ")
    }
}
