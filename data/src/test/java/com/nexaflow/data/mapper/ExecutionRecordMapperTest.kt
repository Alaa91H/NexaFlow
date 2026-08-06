package com.nexaflow.data.mapper

import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRecordMapperTest {

    @Test
    fun domainToEntityAndBackRoundTrips() {
        val record = ExecutionRecord(
            id = "r1",
            automationId = "a1",
            automationName = "Morning routine",
            success = true,
            message = "Brightness set to 128",
            executedAt = 1700000000000L,
            channel = "ROOT"
        )

        val entity = record.toEntity()
        assertEquals(record.id, entity.id)
        assertEquals(record.automationName, entity.automationName)
        assertEquals(record.channel, entity.channel)

        assertEquals(record, entity.toDomain())
    }

    @Test
    fun failedExecutionRoundTrips() {
        val record = ExecutionRecord(
            id = "r2",
            automationId = "a2",
            automationName = "Night mode",
            success = false,
            message = "Shizuku unavailable",
            executedAt = 1700000000000L,
            channel = "SHIZUKU"
        )

        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun nullChannelRoundTrips() {
        val record = ExecutionRecord(
            id = "r3",
            automationId = "a3",
            automationName = "Legacy",
            success = true,
            message = "ok",
            executedAt = 1700000000000L
        )
        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun actionResultsRoundTripThroughJson() {
        val record = ExecutionRecord(
            id = "r4",
            automationId = "a4",
            automationName = "Multi-step",
            success = false,
            message = "Brightness ok | Waited 5s",
            executedAt = 1700000000000L,
            channel = "ROOT",
            actionResults = listOf(
                ActionExecutionResult("SYSTEM_BRIGHTNESS", true, "Brightness set", 42L),
                ActionExecutionResult("SYSTEM_WAIT", false, "Cancelled", 5000L)
            )
        )

        val entity = record.toEntity()
        assertEquals(record, entity.toDomain())
        // JSON column is populated and carries every field.
        assertTrue(entity.resultsJson!!.contains("SYSTEM_BRIGHTNESS"))
        assertTrue(entity.resultsJson!!.contains("\"durationMs\":42"))
        assertTrue(entity.resultsJson!!.contains("\"success\":true"))
    }

    @Test
    fun emptyActionResultsStoresNullJson() {
        val record = ExecutionRecord(
            id = "r5",
            automationId = "a5",
            automationName = "Legacy run",
            success = true,
            message = "ok",
            executedAt = 1700000000000L
        )
        val entity = record.toEntity()
        assertEquals(null, entity.resultsJson)
        assertEquals(record, entity.toDomain())
    }
}
