package com.nexaflow.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexaflow.core.database.ExecutionRecordEntity
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ExecutionRecord

private val gson = Gson()
private val resultsType = object : TypeToken<List<ActionExecutionResult>>() {}.type

fun ExecutionRecordEntity.toDomain(): ExecutionRecord {
    return ExecutionRecord(
        id = id,
        automationId = automationId,
        automationName = automationName,
        success = success,
        message = message,
        executedAt = executedAt,
        channel = channel,
        actionResults = resultsJson?.let { json ->
            runCatching { gson.fromJson<List<ActionExecutionResult>>(json, resultsType) }.getOrNull()
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
        resultsJson = actionResults.takeIf { it.isNotEmpty() }?.let { gson.toJson(it, resultsType) }
    )
}
