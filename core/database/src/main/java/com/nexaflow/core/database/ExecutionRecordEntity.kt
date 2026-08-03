package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_history")
data class ExecutionRecordEntity(
    @PrimaryKey val id: String,
    val automationId: String,
    val automationName: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long
)
