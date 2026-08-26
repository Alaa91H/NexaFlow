package com.nexaflow.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM execution_history ORDER BY executedAt DESC")
    fun getAllExecutions(): Flow<List<ExecutionRecordEntity>>

    /**
     * Pageable view of the same table — the history screen streams pages of
     * [PAGE_SIZE] instead of materializing the whole table on every change.
     */
    @Query("SELECT * FROM execution_history ORDER BY executedAt DESC")
    fun getExecutionsPaged(): PagingSource<Int, ExecutionRecordEntity>

    /** Pageable execution history scoped to one routine, newest record first. */
    @Query("SELECT * FROM execution_history WHERE automationId = :automationId ORDER BY executedAt DESC")
    fun getExecutionsPagedForAutomation(automationId: String): PagingSource<Int, ExecutionRecordEntity>

    @Query("SELECT * FROM execution_history ORDER BY executedAt DESC LIMIT 1")
    suspend fun getLatestExecution(): ExecutionRecordEntity?

    @Query("SELECT * FROM execution_history WHERE id = :id")
    suspend fun getExecutionById(id: String): ExecutionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(record: ExecutionRecordEntity)

    /**
     * Inserts a record and immediately enforces the retention policy so the
     * history table never grows without bound. Runs atomically with the insert.
     */
    @Transaction
    suspend fun insertWithRetention(
        record: ExecutionRecordEntity,
        retentionDays: Int = DEFAULT_RETENTION_DAYS
    ) {
        require(retentionDays > 0) { "retentionDays must be greater than zero" }
        insertExecution(record)
        pruneOlderThan(System.currentTimeMillis() - retentionDays.toLong() * MILLIS_PER_DAY)
        pruneExcess(RETAIN_LIMIT)
    }

    /** Deletes history older than [cutoffTimestamp]; returns rows removed. */
    @Query("DELETE FROM execution_history WHERE executedAt < :cutoffTimestamp")
    suspend fun pruneOlderThan(cutoffTimestamp: Long): Int

    /** Keeps only the newest [keepCount] records; returns rows removed. */
    @Query(
        "DELETE FROM execution_history WHERE id NOT IN " +
            "(SELECT id FROM execution_history ORDER BY executedAt DESC LIMIT :keepCount)"
    )
    suspend fun pruneExcess(keepCount: Int): Int

    @Query("DELETE FROM execution_history")
    suspend fun clearHistory()

    companion object {
        /** Default retention period; callers may explicitly select another positive value. */
        const val DEFAULT_RETENTION_DAYS = 90
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        /** Backwards-compatible default cutoff used by periodic pruning. */
        const val RETENTION_MS = DEFAULT_RETENTION_DAYS * MILLIS_PER_DAY
        /** Hard ceiling on stored execution records. */
        const val RETAIN_LIMIT = 1_000
        /** Rows fetched per page by the history pager. */
        const val PAGE_SIZE = 30
    }
}
