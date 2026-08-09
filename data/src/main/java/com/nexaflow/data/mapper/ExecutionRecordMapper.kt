package com.nexaflow.data.mapper

import com.nexaflow.core.database.ExecutionRecordEntity
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun ExecutionRecordEntity.toDomain(): ExecutionRecord {
    return ExecutionRecord(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt,
        channel = channel,
        actionResults = resultsJson?.let { jsonText ->
            runCatching {
                json.decodeFromString<List<ActionExecutionResult>>(jsonText)
            }.getOrNull()
        } ?: emptyList()
    )
}

fun ExecutionRecord.toEntity(): ExecutionRecordEntity {
    return ExecutionRecordEntity(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt,
        channel = channel,
        resultsJson = actionResults.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString<List<ActionExecutionResult>>(it) }
    )
}
