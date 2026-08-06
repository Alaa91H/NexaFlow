package com.nexaflow.core.common

/** Injectable wall-clock so timestamps are deterministic in tests. */
fun interface EpochMillis {
    fun now(): Long

    companion object {
        val System = EpochMillis { java.lang.System.currentTimeMillis() }
    }
}
