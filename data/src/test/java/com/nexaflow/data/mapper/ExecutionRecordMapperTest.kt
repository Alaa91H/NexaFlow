package com.nexaflow.data.mapper

import com.nexaflow.domain.models.ExecutionRecord
import org.junit.Assert.assertEquals
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
            executedAt = 1700000000000L
        )

        val entity = record.toEntity()
        assertEquals(record.id, entity.id)
        assertEquals(record.automationName, entity.automationName)

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
            executedAt = 1700000000000L
        )

        assertEquals(record, record.toEntity().toDomain())
    }
}
