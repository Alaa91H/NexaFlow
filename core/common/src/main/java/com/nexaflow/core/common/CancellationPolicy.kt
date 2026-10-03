package com.nexaflow.core.common

import kotlinx.coroutines.CancellationException

/**
 * Re-throws structured-concurrency cancellation before broad error handling.
 * Use at boundaries that intentionally translate ordinary failures to domain results.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/** Equivalent to runCatching, except cancellation is never converted to Result.failure. */
inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        throwable.rethrowIfCancellation()
        Result.failure(throwable)
    }
