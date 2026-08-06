package com.nexaflow.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Testable dispatcher bundle. Production uses the real Android dispatchers;
 * unit tests can substitute a single-threaded dispatcher.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher
) {
    companion object {
        val Default = AppDispatchers(
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            main = Dispatchers.Main
        )
    }
}
