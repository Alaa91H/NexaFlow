package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Delete
    suspend fun deleteAutomation(automation: AutomationEntity)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun updateAutomationStatus(id: String, enabled: Boolean)
}
