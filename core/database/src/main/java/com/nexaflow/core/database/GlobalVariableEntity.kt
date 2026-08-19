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
    /** Monotonic revision; pre-typed variables migrate with version one. */
    val version: Long = 1L,
    /** Null for legacy text values; typed values use the domain JSON codec. */
    val serializedValue: String? = null,
    /**
     * When true the value is encrypted at rest via Android Keystore
     * (AES-GCM) — for tokens, passwords and other secrets injected into
     * action texts with %NAME.
     */
    val sensitive: Boolean = false
)
