package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VariableDao {
    @Query("SELECT * FROM global_variables ORDER BY name COLLATE NOCASE")
    fun getAllVariables(): Flow<List<GlobalVariableEntity>>

    @Query("SELECT * FROM global_variables ORDER BY name COLLATE NOCASE")
    suspend fun getVariablesOnce(): List<GlobalVariableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(variable: GlobalVariableEntity)

    @Query("DELETE FROM global_variables WHERE id = :id")
    suspend fun deleteById(id: String)
}
