package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val color: Long,
    val active: Boolean,
    val automationIdsJson: String,
    val createdAt: Long
)
