package com.nexaflow.domain.models

data class ExecutionRecord(
    val id: String,
    val automationId: String,
    val automationName: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long
)
