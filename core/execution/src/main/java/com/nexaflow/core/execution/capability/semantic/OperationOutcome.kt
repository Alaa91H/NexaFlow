package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Terminal or externally-actionable lifecycle state of one semantic operation.
 * `UNKNOWN` exists for the critical distinction the requirement mandates: a
 * transport timeout or process death *after a side effect may have occurred*
 * must not be reported as a definite failure.
 */
enum class OperationOutcomeStatus {
    SUCCESS,
    PARTIAL,
    PENDING_USER_ACTION,
    UNSUPPORTED,
    FAILED,
    CANCELLED,
    UNKNOWN
}

/**
 * The normalized result of one semantic operation execution. Messages are
 * safe, human-facing summaries; machine classification lives in [errorCode].
 * Metadata is strictly non-sensitive (no raw commands, no parameter secrets).
 */
data class OperationOutcome(
    val operation: SemanticOperationId,
    val status: OperationOutcomeStatus,
    val strategy: StrategyId? = null,
    val errorCode: CapabilityErrorCode? = null,
    val message: String,
    val verification: VerificationResult? = null,
    val durationMs: Long = 0L,
    /** Transport-level failure classifications for router fallback decisions. */
    val transportFailure: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean get() = status == OperationOutcomeStatus.SUCCESS
    val isVerifiedSuccess: Boolean
        get() = status == OperationOutcomeStatus.SUCCESS && verification?.verified == true

    companion object {
        fun unsupported(operation: SemanticOperationId, message: String) = OperationOutcome(
            operation = operation,
            status = OperationOutcomeStatus.UNSUPPORTED,
            errorCode = CapabilityErrorCode.UNSUPPORTED_CAPABILITY,
            message = message
        )

        fun failed(
            operation: SemanticOperationId,
            errorCode: CapabilityErrorCode,
            message: String,
            strategy: StrategyId? = null,
            transportFailure: Boolean = false
        ) = OperationOutcome(
            operation = operation,
            status = OperationOutcomeStatus.FAILED,
            strategy = strategy,
            errorCode = errorCode,
            message = message,
            transportFailure = transportFailure
        )
    }
}
