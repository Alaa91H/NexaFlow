package com.nexaflow.core.database

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
    suspend fun insertWithRetention(record: ExecutionRecordEntity) {
        insertExecution(record)
        pruneOlderThan(System.currentTimeMillis() - RETENTION_MS)
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
        /** Execution history older than this is pruned (60 days). */
        const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000
        /** Hard ceiling on stored execution records. */
        const val RETAIN_LIMIT = 1_000
    }
}
