package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val iconColor: Long,
    val backgroundColor: Long,
    val category: String,
    val priority: Int,
    val enabled: Boolean,
    val triggersJson: String, // Store as JSON string
    val conditionsJson: String, // Store as JSON string
    val actionsJson: String, // Store as JSON string
    val createdAt: Long,
    val updatedAt: Long
)
