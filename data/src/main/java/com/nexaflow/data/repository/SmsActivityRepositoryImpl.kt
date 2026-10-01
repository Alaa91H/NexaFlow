package com.nexaflow.data.repository

import com.nexaflow.core.database.SmsActivityDao
import com.nexaflow.core.database.SmsActivityEntity
import com.nexaflow.domain.models.SmsActivityEvent
import com.nexaflow.domain.repositories.SmsActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SmsActivityRepositoryImpl @Inject constructor(
    private val dao: SmsActivityDao
) : SmsActivityRepository {
    override fun observeLatest(limit: Int): Flow<List<SmsActivityEvent>> =
        dao.observeLatest(limit.coerceIn(1, MAX_EVENTS)).map { records -> records.map { it.toDomain() } }

    override suspend fun record(event: SmsActivityEvent) {
        dao.insert(event.toEntity())
        dao.pruneToNewest(MAX_EVENTS)
    }

    override suspend fun clear() = dao.clear()

    private fun SmsActivityEntity.toDomain() = SmsActivityEvent(id, eventType, automationId, automationName, outcome, errorCode, occurredAt)
    private fun SmsActivityEvent.toEntity() = SmsActivityEntity(id, eventType, automationId, automationName, outcome, errorCode, occurredAt)

    private companion object { const val MAX_EVENTS = 500 }
}
