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
        AgentDefinitionEntity::class,
        AgentRunEntity::class,
        AgentRunEventEntity::class,
        AgentApprovalEntity::class,
        AgentAutomationApprovalEntity::class,
        SmsActivityEntity::class
    ],
    version = 28,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun executionDao(): ExecutionDao
    abstract fun variableDao(): VariableDao
    abstract fun agentPlatformDao(): AgentPlatformDao
    abstract fun agentDefinitionDao(): AgentDefinitionDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun smsActivityDao(): SmsActivityDao
}
