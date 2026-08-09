package com.nexaflow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_variables")
data class GlobalVariableEntity(
    @PrimaryKey val id: String,
    /** Variable name without the leading `%`. */
    val name: String,
    val value: String,
    val updatedAt: Long,
    /**
     * When true the value is encrypted at rest via Android Keystore
     * (AES-GCM) — for tokens, passwords and other secrets injected into
     * action texts with %NAME.
     */
    val sensitive: Boolean = false
)
