package com.nexaflow.data.mapper

import com.nexaflow.core.database.ExecutionRecordEntity
import com.nexaflow.domain.models.ExecutionRecord

fun ExecutionRecordEntity.toDomain(): ExecutionRecord {
    return ExecutionRecord(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt
    )
}

fun ExecutionRecord.toEntity(): ExecutionRecordEntity {
    return ExecutionRecordEntity(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt
    )
}
