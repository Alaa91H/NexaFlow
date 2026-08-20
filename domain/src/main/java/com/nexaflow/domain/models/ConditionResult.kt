package com.nexaflow.domain.models

/**
 * Typed result of evaluating one condition whose state may be unavailable or
 * indeterminate. This contract intentionally does not expose Android types and
 * must never be coerced to Boolean without an explicit caller policy.
 */
sealed class ConditionResult {
    /** The condition was evaluated and is currently true. */
    data object Satisfied : ConditionResult()

    /** The condition was evaluated and is currently false. */
    data object Unsatisfied : ConditionResult()

    /** The provider cannot determine the current state; it is not false. */
    data object Unknown : ConditionResult()

    /** The provider/component is unavailable before a condition result exists. */
    data object Unavailable : ConditionResult()

    /** The provider responded with an invalid or otherwise actionable error. */
    data class Error(val reason: String) : ConditionResult() {
        init {
            require(reason.isNotBlank()) { "Condition error reason must not be blank" }
            require(reason.length <= MAX_REASON_LENGTH) { "Condition error reason is too long" }
        }
    }

    companion object {
        private const val MAX_REASON_LENGTH = 1_024
    }
}
