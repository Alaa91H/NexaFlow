package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.OperationSpec
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * One ranked, fully explainable candidate produced by the resolver. The router
 * decision is auditable end-to-end: every exclusion and every ordering factor
 * is materialized here.
 */
data class StrategyCandidate(
    val strategy: CapabilityStrategy,
    val supported: Boolean,
    val availability: StrategyAvailability,
    /** 0..100 confidence; derived from evidence, health and privilege cost. */
    val confidence: Int,
    /** Human-readable why (never containing secrets or raw commands). */
    val reason: String,
    /** Cumulative weighted evidence score from the store. */
    val evidenceScore: Long,
    /** Least-privilege rank; lower is more privileged-free. */
    val privilegeCost: Int
)

/**
 * The single decision point for "how do I execute this operation on this
 * device, right now?". Ordering factors, most significant first:
 *
 * 1. operation compatibility (spec strategies + policy allow-list)
 * 2. security/privilege eligibility (explicit opt-in for privileged strategies)
 * 3. user policy (preferred strategies)
 * 4. live availability
 * 5. previously verified evidence (same device fingerprint)
 * 6. recent success/failure health (cooldown-aware)
 * 7. strategy confidence (evidence+health composite)
 * 8. least privilege (public API < settings < accessibility < shizuku < root)
 *
 * Ties are resolved toward less privilege. Root is never chosen merely
 * because it is available.
 */
class OperationStrategyResolver(
    private val evidenceStore: CapabilityEvidenceStore,
    private val healthTracker: StrategyHealthTracker,
    private val fingerprint: DeviceFingerprint,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun candidates(
        request: TypedOperationRequest,
        spec: OperationSpec,
        strategies: List<CapabilityStrategy>
    ): List<StrategyCandidate> = buildList {
        for (strategy in strategies) {
            if (spec.id !in strategy.supportedOperations) continue
            if (strategy.id !in spec.strategies) continue
            if (isPrivileged(strategy.id) && !request.allowPrivilegedStrategies) continue

            val availability = runCatching {
                strategy.availability(request, spec.id)
            }.getOrElse {
                StrategyAvailability(available = false, reason = "Availability probe failed")
            }

            val evidence = evidenceStore.evidenceFor(spec.id, strategy.id, fingerprint.deviceKey)
            val health = healthTracker.healthFor(strategy.id, fingerprint.deviceKey)
            val cooling = healthTracker.isCoolingDown(strategy.id, fingerprint.deviceKey, nowMs())

            val evidenceScore = evidence.score(nowMs())
            val confidence = computeConfidence(availability, evidenceScore, health.state, isPrivileged(strategy.id))
            add(
                StrategyCandidate(
                    strategy = strategy,
                    supported = availability.available,
                    availability = availability,
                    confidence = confidence,
                    reason = buildReason(availability, evidence, health, cooling, isPrivileged(strategy.id)),
                    evidenceScore = evidenceScore,
                    privilegeCost = privilegeCost(strategy.id)
                )
            )
        }
    }

    /** Ranks candidates for execution; eligible first, explainable order. */
    fun rank(candidates: List<StrategyCandidate>): List<StrategyCandidate> =
        candidates.sortedWith(
            compareBy<StrategyCandidate> { !it.supported }
                .thenBy { it.strategy.id !in preferredSet() }
                .thenByDescending { it.confidence }
                .thenBy { it.privilegeCost }
                .thenBy { it.strategy.id.name }
        )

    private var preferred: List<StrategyId> = emptyList()
    private fun preferredSet(): Set<StrategyId> = preferred.toSet()

    fun withPreferred(strategies: List<StrategyId>): OperationStrategyResolver = apply {
        preferred = strategies
    }

    private fun computeConfidence(
        availability: StrategyAvailability,
        evidenceScore: Long,
        health: StrategyHealthTracker.State,
        privileged: Boolean
    ): Int = when {
        !availability.available -> 0
        else -> {
            val base = 40
            val evidenceBoost = (evidenceScore.coerceAtMost(30L)).toInt()
            val healthBoost = when (health) {
                StrategyHealthTracker.State.HEALTHY -> 20
                StrategyHealthTracker.State.UNKNOWN -> 10
                StrategyHealthTracker.State.DEGRADED -> 0
                else -> -15
            }
            val privilegePenalty = if (privileged) 5 else 0
            (base + evidenceBoost + healthBoost - privilegePenalty).coerceIn(0, 100)
        }
    }

    private fun buildReason(
        availability: StrategyAvailability,
        evidence: CapabilityEvidence,
        health: StrategyHealthTracker.Health,
        cooling: Boolean,
        privileged: Boolean
    ): String = buildString {
        append(if (availability.available) "available" else "unavailable")
        availability.reason?.let { append(": ").append(it) }
        if (evidence.verifiedSuccesses > 0) {
            append("; verified on this device (${evidence.verifiedSuccesses}x)")
        }
        if (evidence.failures > 0) append("; failures: ${evidence.failures}")
        when {
            health.state == StrategyHealthTracker.State.DISABLED -> append("; disabled")
            cooling -> append("; cooling down after repeated failures")
            health.state == StrategyHealthTracker.State.DEGRADED -> append("; degraded")
            privileged -> append("; privileged opt-in granted")
        }
    }

    private fun isPrivileged(strategyId: StrategyId): Boolean =
        strategyId == StrategyId.SHIZUKU_USER_SERVICE || strategyId == StrategyId.ROOT_SHELL

    private fun privilegeCost(strategyId: StrategyId): Int = when (strategyId) {
        StrategyId.ANDROID_PUBLIC_API -> 0
        StrategyId.WRITE_SETTINGS -> 1
        StrategyId.DEVICE_OWNER -> 2
        StrategyId.SETTINGS_USER_ACTION -> 3
        StrategyId.OEM_SPECIFIC -> 4
        StrategyId.SHIZUKU_USER_SERVICE -> 5
        StrategyId.ROOT_SHELL -> 6
    }
}

/**
 * Executes a semantic operation through the best safe strategy, with the
 * fallback contract: transport-level failures may advance to the next
 * candidate; a possible side effect (UNKNOWN outcome) must never trigger a
 * blind re-execution — it reconciles by reading the actual device state.
 */
class CapabilityRouter(
    private val registry: OperationRegistry,
    private val strategies: List<CapabilityStrategy>,
    private val evidenceStore: CapabilityEvidenceStore,
    private val healthTracker: StrategyHealthTracker,
    private val fingerprint: DeviceFingerprint,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private fun resolverFor(request: TypedOperationRequest): OperationStrategyResolver =
        OperationStrategyResolver(evidenceStore, healthTracker, fingerprint, nowMs)
            .withPreferred(request.preferredStrategies)

    suspend fun execute(request: TypedOperationRequest): OperationOutcome {
        val spec = registry.specFor(request.operation)
            ?: return OperationOutcome.unsupported(
                request.operation,
                "Operation is not registered; it cannot be executed safely"
            )

        val parameterError = validateParameters(spec, request)
        if (parameterError != null) {
            return OperationOutcome.failed(
                request.operation, CapabilityErrorCode.INVALID_CONFIGURATION, parameterError
            )
        }

        val ranked = resolverFor(request)
            .candidates(request, spec, strategies)
            .let { resolverFor(request).rank(it) }
            .filter { it.supported }

        if (ranked.isEmpty()) {
            val anyPrivilegedBlocked = strategies.any {
                it.id in spec.strategies && isPrivileged(it.id)
            } && !request.allowPrivilegedStrategies
            return if (anyPrivilegedBlocked) {
                OperationOutcome(
                    operation = request.operation,
                    status = OperationOutcomeStatus.PENDING_USER_ACTION,
                    errorCode = CapabilityErrorCode.PERMISSION_DENIED,
                    message = "Executing this operation requires enabling a privileged provider (Shizuku or Root)"
                )
            } else {
                OperationOutcome.unsupported(
                    request.operation,
                    "No registered strategy is available on this device right now"
                )
            }
        }

        var lastOutcome: OperationOutcome? = null
        for (candidate in ranked) {
            val strategy = candidate.strategy
            val startedAt = nowMs()
            val outcome = runCatching { strategy.execute(request, request.operation) }
                .getOrElse { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    OperationOutcome.failed(
                        request.operation, CapabilityErrorCode.UNKNOWN_ERROR,
                        throwable.message ?: "Strategy threw an unexpected error",
                        strategy.id
                    )
                }
            val duration = nowMs() - startedAt
            val normalized = outcome.copy(durationMs = outcome.durationMs.takeIf { it > 0 } ?: duration)

            when {
                normalized.isSuccess -> {
                    evidenceStore.recordSuccess(
                        request.operation, strategy.id, fingerprint.deviceKey,
                        verified = normalized.verification?.verified == true,
                        latencyMs = normalized.durationMs
                    )
                    healthTracker.recordSuccess(strategy.id, fingerprint.deviceKey)
                    return verifyIfNeeded(request, spec, strategy, normalized)
                }
                normalized.transportFailure -> {
                    evidenceStore.recordFailure(request.operation, strategy.id, fingerprint.deviceKey)
                    healthTracker.recordFailure(strategy.id, fingerprint.deviceKey)
                    lastOutcome = normalized
                    continue // safe fallback: no side effect was proven to start
                }
                normalized.status == OperationOutcomeStatus.UNKNOWN -> {
                    // Possible side effect: reconcile through a read instead of
                    // re-executing. Never fallback blindly.
                    return reconcile(request, strategy, normalized)
                }
                else -> {
                    evidenceStore.recordFailure(request.operation, strategy.id, fingerprint.deviceKey)
                    healthTracker.recordFailure(strategy.id, fingerprint.deviceKey)
                    // Definitive non-transport failure (rejected/unsupported/
                    // postcondition). If the operation is retry-safe to a
                    // different strategy (idempotent), advance; otherwise stop.
                    if (spec.idempotency ==
                        com.nexaflow.domain.capability.CapabilityIdempotency.IDEMPOTENT
                    ) {
                        lastOutcome = normalized
                        continue
                    }
                    return normalized
                }
            }
        }
        return lastOutcome ?: OperationOutcome.unsupported(
            request.operation, "No strategy could execute the operation"
        )
    }

    /**
     * Reconciles an UNKNOWN outcome by reading the actual state via the
     * counterpart GET operation. If the observed state matches the requested
     * one, the operation is reclassified as verified SUCCESS; otherwise it
     * fails honestly without a fallback re-execution.
     */
    private suspend fun reconcile(
        request: TypedOperationRequest,
        strategy: CapabilityStrategy,
        unknownOutcome: OperationOutcome
    ): OperationOutcome {
        val getStateId = SemanticOperationId.counterpartOf(request.operation)
            ?.takeIf { it.isReadOnly }
        if (getStateId == null) {
            return unknownOutcome.copy(
                message = unknownOutcome.message + " (outcome unconfirmed; no read-back available)"
            )
        }
        val observed = runCatching { strategy.readState(request, getStateId) }.getOrNull()
        val requestedEnabled = request.parameters["enabled"]?.toBooleanStrictOrNull()
        return if (observed != null && requestedEnabled != null && observed == requestedEnabled) {
            OperationOutcome(
                operation = request.operation,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = strategy.id,
                message = "Reconciled: observed state matches the requested state",
                verification = com.nexaflow.domain.capability.VerificationResult(
                    attempted = true, verified = true,
                    message = "Post-condition verified by state reconciliation"
                ),
                metadata = unknownOutcome.metadata
            )
        } else {
            unknownOutcome.copy(
                status = OperationOutcomeStatus.FAILED,
                errorCode = CapabilityErrorCode.UNKNOWN_ERROR,
                message = "Outcome could not be confirmed; observed state differs or is unreadable"
            )
        }
    }

    private suspend fun verifyIfNeeded(
        request: TypedOperationRequest,
        spec: OperationSpec,
        strategy: CapabilityStrategy,
        outcome: OperationOutcome
    ): OperationOutcome {
        if (spec.verificationMode == com.nexaflow.domain.capability.VerificationMode.NONE) {
            return outcome
        }
        // Read-back always targets the paired GET operation: a write operation
        // has no observable state of its own.
        val getStateId = SemanticOperationId.counterpartOf(request.operation)
            ?.takeIf { it.isReadOnly }
        val observed = getStateId
            ?.let { runCatching { strategy.readState(request, it) }.getOrNull() }
        val requestedEnabled = outcome.metadata["requestedEnabled"]
        val verification = com.nexaflow.domain.capability.VerificationResult(
            attempted = observed != null,
            verified = requestedEnabled != null &&
                ((observed == true && requestedEnabled == "true") ||
                    (observed == false && requestedEnabled == "false")),
            message = when {
                observed == null -> "Strategy cannot read back the state; success remains unverified"
                else -> "Observed state: $observed"
            }
        )
        val merged = outcome.copy(verification = verification)
        // REQUIRED fails closed only when a read-back was attempted and
        // contradicted the request. When the strategy has no reliable read
        // (observed == null), the transport success stays honest-but-unverified:
        // converting it to a definite failure would itself be a false claim.
        val contradicted = verification.attempted && !verification.verified
        return if (spec.verificationMode == com.nexaflow.domain.capability.VerificationMode.REQUIRED && contradicted) {
            merged.copy(status = OperationOutcomeStatus.FAILED, errorCode = CapabilityErrorCode.VERIFICATION_FAILED)
        } else merged
    }

    private fun validateParameters(spec: OperationSpec, request: TypedOperationRequest): String? {
        for (param in spec.parameters) {
            if (param.required && param.name !in request.parameters) {
                return "Missing required parameter '${param.name}' for ${spec.id.name}"
            }
        }
        for ((name, _) in request.parameters) {
            if (spec.parameterSchema(name) == null) {
                return "Unknown parameter '$name' for ${spec.id.name}"
            }
        }
        return null
    }

    private fun isPrivileged(strategyId: StrategyId): Boolean =
        strategyId == StrategyId.SHIZUKU_USER_SERVICE || strategyId == StrategyId.ROOT_SHELL
}
