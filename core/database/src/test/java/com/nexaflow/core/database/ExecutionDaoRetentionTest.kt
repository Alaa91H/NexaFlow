package com.nexaflow.core.database

import androidx.paging.PagingSource
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

    private fun entity(
        id: String,
        executedAt: Long,
        automationId: String = "a1"
    ) = ExecutionRecordEntity(
        id = id,
        automationId = automationId,
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
    fun pagedHistoryForAutomation_returnsOnlyThatRoutineNewestFirst() = runBlocking {
        dao.insertExecution(entity("a-old", 10L, automationId = "routine-a"))
        dao.insertExecution(entity("other", 99L, automationId = "routine-b"))
        dao.insertExecution(entity("a-new", 20L, automationId = "routine-a"))

        val result = dao.getExecutionsPagedForAutomation("routine-a").load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )

        val page = result as PagingSource.LoadResult.Page<Int, ExecutionRecordEntity>
        assertEquals(listOf("a-new", "a-old"), page.data.map { it.id })
    }

    @Test
    fun pagedHistoryFilter_returnsOnlyFailedRunsForSelectedRoutine() = runBlocking {
        dao.insertExecution(entity("success", 30L, automationId = "routine-a"))
        dao.insertExecution(entity("skipped", 20L, automationId = "routine-a"))
        dao.insertExecution(
            entity("failed", 10L, automationId = "routine-a").copy(success = false, message = "network unavailable")
        )
        dao.insertExecution(
            entity("other-failed", 40L, automationId = "routine-b").copy(success = false, message = "other")
        )

        val result = dao.getExecutionsPagedFiltered("routine-a", false).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )

        val page = result as PagingSource.LoadResult.Page<Int, ExecutionRecordEntity>
        assertEquals(listOf("failed"), page.data.map { it.id })
    }

    @Test
    fun pagedSkippedHistory_returnsOnlySkippedRunsForSelectedRoutineNewestFirst() = runBlocking {
        dao.insertExecution(entity("completed", 50L, automationId = "routine-a"))
        dao.insertExecution(
            entity("a-old-skip", 10L, automationId = "routine-a")
                .copy(message = "Skipped: maintenance waiting for CHARGING_REQUIRED")
        )
        dao.insertExecution(
            entity("a-new-skip", 30L, automationId = "routine-a")
                .copy(message = "Skipped: constraint not met")
        )
        dao.insertExecution(
            entity("failed", 40L, automationId = "routine-a")
                .copy(success = false, message = "network unavailable")
        )
        dao.insertExecution(
            entity("other-skip", 60L, automationId = "routine-b")
                .copy(message = "Skipped: other routine")
        )

        val result = dao.getExecutionsPagedSkipped("routine-a", "Skipped:%").load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )

        val page = result as PagingSource.LoadResult.Page<Int, ExecutionRecordEntity>
        assertEquals(listOf("a-new-skip", "a-old-skip"), page.data.map { it.id })
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
