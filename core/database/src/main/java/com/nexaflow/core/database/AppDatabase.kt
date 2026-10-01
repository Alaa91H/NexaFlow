package com.nexaflow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AutomationEntity::class,
        ExecutionRecordEntity::class,
        GlobalVariableEntity::class,
        AutomationApiMetadataEntity::class,
        AgentAuditEntity::class,
        AgentIdempotencyEntity::class,
        SmsActivityEntity::class
    ],
    version = 23,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun executionDao(): ExecutionDao
    abstract fun variableDao(): VariableDao
    abstract fun agentPlatformDao(): AgentPlatformDao
    abstract fun smsActivityDao(): SmsActivityDao
}
