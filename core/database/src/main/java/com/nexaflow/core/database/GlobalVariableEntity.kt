package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_variables")
data class GlobalVariableEntity(
    @PrimaryKey val id: String,
    /** Variable name without the leading `%`. */
    val name: String,
    val value: String,
    val updatedAt: Long
)
