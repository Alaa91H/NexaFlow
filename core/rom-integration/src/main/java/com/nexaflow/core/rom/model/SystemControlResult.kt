package com.nexaflow.core.rom.model

/**
 * Normalized action result shared by legacy controllers and the typed
 * capability router. Optional diagnostic fields are deliberately strings /
 * primitives so core:rom-integration stays independent from capability-domain
 * enums while execution history can still retain provenance.
 */
data class SystemControlResult(
    val success: Boolean,
    val message: String,
    /** Concrete backend/strategy that performed the side effect, when known. */
    val executionChannel: String? = null,
    /** Stable machine-readable failure classification, when available. */
    val errorCode: String? = null,
    /** True when a post-condition read-back was attempted. */
    val verificationAttempted: Boolean = false,
    /** null = no verification verdict; true/false = explicit read-back verdict. */
    val verified: Boolean? = null
) {
    companion object {
        fun ok(
            message: String,
            executionChannel: String? = null,
            verificationAttempted: Boolean = false,
            verified: Boolean? = null
        ) = SystemControlResult(
            success = true,
            message = message,
            executionChannel = executionChannel,
            verificationAttempted = verificationAttempted,
            verified = verified
        )

        fun fail(
            message: String,
            executionChannel: String? = null,
            errorCode: String? = null,
            verificationAttempted: Boolean = false,
            verified: Boolean? = null
        ) = SystemControlResult(
            success = false,
            message = message,
            executionChannel = executionChannel,
            errorCode = errorCode,
            verificationAttempted = verificationAttempted,
            verified = verified
        )
    }
}
