package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "execution_history",
    // Indexed: the history list is ORDER BY executedAt DESC and the retention
    // pruner filters on the same column, so this keeps both fast as the table
    // approaches the 1,000-record ceiling (added in v12).
    indices = [Index(value = ["executedAt"])]
)
data class ExecutionRecordEntity(
    @PrimaryKey val id: String,
    val automationId: String,
    val automationName: String,
    val success: Boolean,
    val message: String,
    val executedAt: Long,
    /** Execution provider that ran the actions; null for pre-v6 rows. */
    val channel: String? = null,
    /** Per-action outcomes as JSON (actionType/success/message/durationMs); null for pre-v7 rows. */
    val resultsJson: String? = null
)
