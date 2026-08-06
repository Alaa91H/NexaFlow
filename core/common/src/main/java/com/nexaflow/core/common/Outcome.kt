package com.nexaflow.core.common

/**
 * A lightweight success/failure result used by framework components that must
 * stay independent of any specific Android result type.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
