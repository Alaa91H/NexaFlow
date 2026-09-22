package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.OperationSpec
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Typed request for one semantic operation. Parameters are already validated
 * against the [OperationSpec] schema before reaching a strategy; strategies
 * never receive raw, unbounded maps from workflow config.
 */
data class TypedOperationRequest(
    val operation: SemanticOperationId,
    /** Fully validated string parameters; secrets are redacted by callers. */
    val parameters: Map<String, String> = emptyMap(),
    /** True when an explicit user policy authorized privileged strategies. */
    val allowPrivilegedStrategies: Boolean = false,
    /** User-preferred strategy ordering; empty delegates to the router. */
    val preferredStrategies: List<StrategyId> = emptyList(),
    val workflowId: String? = null,
    val executionId: String? = null
)

/** Live answer of one strategy for "can I run this operation right now?". */
data class StrategyAvailability(
    val available: Boolean,
    /** Why the strategy is or is not usable; shown in diagnostics only. */
    val reason: String? = null,
    /** When false-but-known, the caller can present a grant path. */
    val permissionRequired: Boolean = false
)

/**
 * One concrete, independently verifiable way to execute one operation class.
 * A strategy owns exactly one mechanism (public API, settings grant, Shizuku
 * typed operation, root typed operation, …) and builds its own argv or API
 * calls — it never accepts or forwards a workflow-supplied command string.
 */
interface CapabilityStrategy {
    val id: StrategyId

    /** The operations this strategy actually implements (not promises). */
    val supportedOperations: Set<SemanticOperationId>

    /**
     * Live availability for [operation]. Implementations must be cheap and
     * must not execute the operation or any side effect.
     */
    suspend fun availability(request: TypedOperationRequest, operation: SemanticOperationId): StrategyAvailability

    /** Executes the operation and returns a normalized, honest outcome. */
    suspend fun execute(request: TypedOperationRequest, operation: SemanticOperationId): OperationOutcome

    /**
     * Reads the current externally observable state for reconcile/verification.
     * Returns null when this strategy has no reliable read for the operation;
     * a router then treats the outcome as unverifiable rather than proven.
     */
    suspend fun readState(request: TypedOperationRequest, operation: SemanticOperationId): Boolean? = null

    /**
     * Reads the current externally observable *scalar* state (brightness
     * level, timeout seconds, …) in the operation's own unit for value-write
     * verification. Returns null when this strategy has no reliable read;
     * a strict-verification router then reports the outcome as unconfirmed
     * instead of fabricating a boolean verdict.
     */
    suspend fun readStateValue(request: TypedOperationRequest, operation: SemanticOperationId): String? = null
}
