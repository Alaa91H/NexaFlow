package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "execution_history",
    // Indexed: history list is ORDER BY executedAt DESC, retention pruner filters on it,
    // and dashboard needs latest per automation (GROUP BY automationId, MAX(executedAt)).
    indices = [
        Index(value = ["executedAt"]),
        Index(value = ["automationId", "executedAt"])
    ]
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
