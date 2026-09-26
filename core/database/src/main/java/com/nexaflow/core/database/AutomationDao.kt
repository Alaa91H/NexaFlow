package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    /** Transaction-local snapshot used by atomic dependency revalidation. */
    @Query("SELECT * FROM automations")
    suspend fun getAllAutomationsSnapshot(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    /**
     * Collection inserts are executed by Room in a single suspending database
     * transaction. Bulk import therefore commits every automation or none.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomations(automations: List<AutomationEntity>)

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    /**
     * Transactional compare-and-set used by API/agent optimistic concurrency.
     * The read and update share one Room transaction, so two writers that both
     * observed the same revision cannot both commit successfully.
     */
    @Transaction
    suspend fun compareAndSetAutomation(
        automation: AutomationEntity,
        expectedRevision: Long
    ): Boolean {
        val current = getAutomationById(automation.id) ?: return false
        if (current.updatedAt != expectedRevision) return false
        updateAutomation(automation)
        return true
    }

    @Delete
    suspend fun deleteAutomation(automation: AutomationEntity)

    /**
     * Deletes only the exact revision observed by an optimistic-concurrency
     * caller. SQLite evaluates the predicate and delete atomically.
     */
    @Query("DELETE FROM automations WHERE id = :id AND updatedAt = :expectedRevision")
    suspend fun deleteAutomationIfRevisionMatches(
        id: String,
        expectedRevision: Long
    ): Int

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun updateAutomationStatus(id: String, enabled: Boolean)
}
