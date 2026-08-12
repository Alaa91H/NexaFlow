package com.nexaflow.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the execution-history retention policy: time-based pruning, the
 * count ceiling, and the atomic insert+prune path used by the repository.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionDaoRetentionTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExecutionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.executionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(id: String, executedAt: Long) = ExecutionRecordEntity(
        id = id,
        automationId = "a1",
        automationName = "Task",
        success = true,
        message = "ok",
        executedAt = executedAt
    )

    @Test
    fun pruneOlderThan_removesOnlyExpiredRecords() = runBlocking {
        dao.insertExecution(entity("old", 1_000L))
        dao.insertExecution(entity("new", 9_999_999_999L))

        val deleted = dao.pruneOlderThan(5_000_000_000L)

        assertEquals(1, deleted)
        assertNull(dao.getExecutionById("old"))
        assertNotNull(dao.getExecutionById("new"))
    }

    @Test
    fun pruneExcess_keepsOnlyNewestRecords() = runBlocking {
        for (i in 0 until 10) {
            dao.insertExecution(entity("e$i", i.toLong()))
        }

        val deleted = dao.pruneExcess(3)

        assertEquals(7, deleted)
        // The three newest (e7, e8, e9 by executedAt) survive.
        assertNull(dao.getExecutionById("e0"))
        assertNull(dao.getExecutionById("e6"))
        assertNotNull(dao.getExecutionById("e7"))
        assertNotNull(dao.getExecutionById("e9"))
    }

    @Test
    fun insertWithRetention_prunesExpiredHistoryAtomically() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertExecution(entity("expired", now - ExecutionDao.RETENTION_MS - 1_000))

        dao.insertWithRetention(entity("fresh", now))

        assertNull("expired row must be pruned", dao.getExecutionById("expired"))
        assertNotNull("fresh row must survive", dao.getExecutionById("fresh"))
    }

    @Test
    fun insertWithRetention_keepsRecordsInsideRetentionWindow() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertExecution(entity("recent", now - 1_000))

        dao.insertWithRetention(entity("fresh", now))

        assertNotNull(dao.getExecutionById("recent"))
        assertNotNull(dao.getExecutionById("fresh"))
    }

    @Test
    fun pruneExcess_withLimitAboveRowCount_deletesNothing() = runBlocking {
        for (i in 0 until 4) {
            dao.insertExecution(entity("e$i", i.toLong()))
        }

        val deleted = dao.pruneExcess(10)

        assertEquals(0, deleted)
        assertNotNull(dao.getExecutionById("e0"))
        assertNotNull(dao.getExecutionById("e3"))
    }
}
