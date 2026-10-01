package com.nexaflow.domain.models

data class SmsActivityEvent(
    val id: String,
    val eventType: String,
    val automationId: String?,
    val automationName: String?,
    val outcome: String,
    val errorCode: String?,
    val occurredAt: Long
)
