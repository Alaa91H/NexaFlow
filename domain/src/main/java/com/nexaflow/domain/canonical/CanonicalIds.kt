package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * Stable semantic identifiers persisted by future canonical workflow schemas.
 *
 * These values are deliberately independent from Kotlin enum/class names.
 * Once released, an identifier is immutable: implementation types may be
 * renamed without changing persisted workflow meaning.
 */
@Serializable
@JvmInline
value class TargetId(val value: String) {
    init { StableIdRules.requireValid(value, "TargetId") }
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class OperationId(val value: String) {
    init { StableIdRules.requireValid(value, "OperationId") }
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class PredicateId(val value: String) {
    init { StableIdRules.requireValid(value, "PredicateId") }
    override fun toString(): String = value
}

/**
 * Stable identity for the existing [com.nexaflow.domain.capability.CapabilityId]
 * compatibility enum. Kept as a distinct type to prevent accidentally mixing
 * capability identity with target/operation identity.
 */
@Serializable
@JvmInline
value class CapabilityStableId(val value: String) {
    init { StableIdRules.requireValid(value, "CapabilityStableId") }
    override fun toString(): String = value
}

private object StableIdRules {
    private val PATTERN = Regex("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*){2,}")
    private const val MAX_LENGTH = 160

    fun requireValid(value: String, kind: String) {
        require(value.length in 3..MAX_LENGTH) { "$kind length must be in 3..$MAX_LENGTH" }
        require(PATTERN.matches(value)) {
            "$kind must be a lowercase dotted stable identifier: $value"
        }
    }
}
