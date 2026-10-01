package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** NexaFlow-handled SMS metadata only. Message bodies, phone numbers and credentials are never stored. */
@Entity(tableName = "sms_activity", indices = [Index(value = ["occurredAt"])])
data class SmsActivityEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val automationId: String?,
    val automationName: String?,
    val outcome: String,
    val errorCode: String?,
    val occurredAt: Long
)

@Dao
interface SmsActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SmsActivityEntity): Long

    @Query("SELECT * FROM sms_activity ORDER BY occurredAt DESC, rowid DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<SmsActivityEntity>>

    @Query("DELETE FROM sms_activity WHERE id NOT IN (SELECT id FROM sms_activity ORDER BY occurredAt DESC, rowid DESC LIMIT :keepCount)")
    suspend fun pruneToNewest(keepCount: Int): Int

    @Query("DELETE FROM sms_activity")
    suspend fun clear()
}
