package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM execution_history ORDER BY executedAt DESC")
    fun getAllExecutions(): Flow<List<ExecutionRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(record: ExecutionRecordEntity)

    @Query("DELETE FROM execution_history")
    suspend fun clearHistory()
}
