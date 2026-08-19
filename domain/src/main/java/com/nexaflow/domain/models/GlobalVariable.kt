package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable

/**
 * A user-defined global variable, Tasker-style. Referenced inside action
 * configs with `%NAME` (case-insensitive) and resolved to [value] by the
 * engine when the task runs. Names must start with a letter or underscore and
 * contain only letters, digits and underscores.
 */
@Immutable
data class GlobalVariable(
    val id: String,
    /** Variable name without the leading `%`, e.g. "HomeAddress". */
    val name: String,
    val value: String,
    val updatedAt: Long,
    /** Monotonic persisted revision used by typed snapshots and conflict-aware restore. */
    val version: Long = 1L,
    /** Typed JSON representation when the value was created by Data Runtime; null means legacy text. */
    val serializedValue: String? = null,
    /**
     * When true the value is encrypted at rest (tokens, passwords, secrets
     * injected into action texts). Stored as ciphertext in the database;
     * decrypted transparently by the repository when read.
     */
    val sensitive: Boolean = false
)
