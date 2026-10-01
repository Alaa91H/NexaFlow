package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.SmsActivityEvent
import kotlinx.coroutines.flow.Flow

interface SmsActivityRepository {
    fun observeLatest(limit: Int = 200): Flow<List<SmsActivityEvent>>
    suspend fun record(event: SmsActivityEvent)
    suspend fun clear()
}
