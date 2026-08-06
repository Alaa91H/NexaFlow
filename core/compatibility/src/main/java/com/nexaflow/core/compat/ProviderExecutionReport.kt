package com.nexaflow.core.compat

/**
 * Result of an [ProviderSelector.executeWithFallback] run: which channel
 * actually executed the command (or why every channel was rejected), plus the
 * full fallback chain that was walked.
 *
 * Phase 6 makes the compatibility layer transparent: instead of a bare
 * [com.nexaflow.core.rom.model.SystemControlResult], every fallback execution
 * reports the [channel] used, so callers and logs can show "ran via Root",
 * "ran via Shizuku", etc.
 */
data class ProviderExecutionReport(
    val success: Boolean,
    val message: String,
    /** The channel that executed the command; null when every channel failed. */
    val channel: ExecutionProviderType?,
    /** Every channel tried, in fallback order (includes [channel] when set). */
    val attemptedChannels: List<ExecutionProviderType>
) {
    companion object {
        fun ok(
            message: String,
            channel: ExecutionProviderType,
            attemptedChannels: List<ExecutionProviderType>
        ) = ProviderExecutionReport(true, message, channel, attemptedChannels)

        fun fail(
            message: String,
            attemptedChannels: List<ExecutionProviderType>
        ) = ProviderExecutionReport(false, message, null, attemptedChannels)
    }
}
