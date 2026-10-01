package com.nexaflow.data.repository

import com.nexaflow.core.database.SmsActivityDao
import com.nexaflow.core.database.SmsActivityEntity
import com.nexaflow.domain.models.SmsActivityEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsActivityRepositoryTest {
    @Test fun persistsOnlyRedactedEventMetadataAndPrunesToBound() = runTest {
        val dao = RecordingDao()
        val repository = SmsActivityRepositoryImpl(dao)
        repository.record(SmsActivityEvent("event", "INCOMING_TRIGGER", "automation", "Morning", "SUCCESS", null, 123L))
        assertEquals("event", dao.inserted?.id)
        assertEquals("automation", dao.inserted?.automationId)
        assertEquals("Morning", dao.inserted?.automationName)
        assertNull(dao.inserted?.errorCode)
        assertEquals(500, dao.prunedTo)
        assertEquals("event", repository.observeLatest(9).first().single().id)
        assertEquals(9, dao.limitCaptured)
    }

    private class RecordingDao : SmsActivityDao {
        var inserted: SmsActivityEntity? = null
        var prunedTo = 0
        var limitCaptured = 0
        override suspend fun insert(event: SmsActivityEntity): Long { inserted = event; return 1 }
        override fun observeLatest(limit: Int): Flow<List<SmsActivityEntity>> {
            limitCaptured = limit
            return flowOf(listOfNotNull(inserted))
        }
        override suspend fun pruneToNewest(keepCount: Int): Int { prunedTo = keepCount; return 0 }
        override suspend fun clear() { inserted = null }
    }
}
