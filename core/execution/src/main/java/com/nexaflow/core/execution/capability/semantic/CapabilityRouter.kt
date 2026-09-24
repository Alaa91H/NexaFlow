package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.OperationSpec
import com.nexaflow.domain.capability.operation.StrategyId
import kotlinx.coroutines.delay

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

/** Read-only preflight state for one semantic operation. */
enum class OperationPlanStatus {
    READY,
    PENDING_USER_ACTION,
    UNAVAILABLE,
    INVALID_CONFIGURATION
}

/**
 * Safe, value-only view of one strategy candidate. The concrete strategy
 * object never escapes the router, so preflight cannot become an execution
 * handle or an authorization cache.
 */
data class StrategyPlanCandidate(
    val strategy: StrategyId,
    val available: Boolean,
    val selected: Boolean,
    val interactive: Boolean,
    val permissionRequired: Boolean,
    val confidence: Int,
    val reason: String,
    val evidenceScore: Long,
    val privilegeCost: Int
)

/**
 * Side-effect-free result of asking how an operation would run right now.
 * execute() uses the same preparation path immediately before side effects.
 */
data class OperationExecutionPlan(
    val operation: SemanticOperationId,
    val status: OperationPlanStatus,
    val selectedStrategy: StrategyId? = null,
    val candidates: List<StrategyPlanCandidate> = emptyList(),
    val message: String,
    val errorCode: CapabilityErrorCode? = null
) {
    val executable: Boolean
        get() = status == OperationPlanStatus.READY
}

/**
 * The single decision point for "how do I execute this operation on this
 * device, right now?". Ordering factors, most significant first:
 *
 * 1. operation compatibility (spec strategies + policy allow-list)
 * 2. security/privilege eligibility (explicit opt-in for privileged strategies)
 * 3. automatic execution before interactive Settings hand-off
 * 4. user policy (preferred strategies)
 * 5. live availability
 * 6. previously verified evidence (same device fingerprint)
 * 7. recent success/failure health (cooldown-aware)
 * 8. strategy confidence (evidence+health composite)
 * 9. least privilege among automatic strategies
 *
 * Android Settings is deliberately a last-resort hand-off, not a peer
 * execution backend. A granted Root/Shizuku path must never lose to a page
 * that asks the user to perform the change manually.
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
                // SETTINGS_USER_ACTION is an interactive escape hatch. Keep it
                // behind every live automatic backend, including Root/Shizuku.
                .thenBy { it.strategy.id == StrategyId.SETTINGS_USER_ACTION }
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

    suspend fun plan(request: TypedOperationRequest): OperationExecutionPlan =
        prepare(request).plan

    suspend fun execute(request: TypedOperationRequest): OperationOutcome {
        val prepared = prepare(request)
        val spec = prepared.spec
            ?: return terminalOutcome(prepared.plan)

        if (prepared.plan.status == OperationPlanStatus.INVALID_CONFIGURATION ||
            prepared.plan.status == OperationPlanStatus.UNAVAILABLE
        ) {
            return terminalOutcome(prepared.plan)
        }

        val ranked = prepared.ranked.filter { it.supported }
        if (ranked.isEmpty()) {
            return terminalOutcome(prepared.plan)
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
                    // NF-P0-002: verification runs FIRST; evidence and health
                    // are recorded only for the post-verification verdict, so
                    // an unverified or contradicted transport success can
                    // never poison the evidence store with a positive record.
                    val verified = verifyIfNeeded(request, spec, strategy, normalized)
                    recordPostVerification(verified)
                    return verified
                }
                normalized.status == OperationOutcomeStatus.PENDING_USER_ACTION ||
                    normalized.status == OperationOutcomeStatus.CANCELLED -> {
                    // Externally-actionable or caller-cancelled: terminal by
                    // contract. Nothing failed inside the strategy, so no
                    // failure evidence is scored and no privileged route is
                    // opened by falling through to the next candidate.
                    return normalized
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

    private suspend fun prepare(request: TypedOperationRequest): PreparedOperation {
        val spec = registry.specFor(request.operation)
            ?: return PreparedOperation(
                spec = null,
                ranked = emptyList(),
                plan = OperationExecutionPlan(
                    operation = request.operation,
                    status = OperationPlanStatus.UNAVAILABLE,
                    message = "Operation is not registered; it cannot be executed safely",
                    errorCode = CapabilityErrorCode.BACKEND_UNAVAILABLE
                )
            )

        // NF-P0-003: one typed validation point shared by plan() and execute().
        val violations = OperationParameterValidator.validate(spec, request.parameters)
        if (violations.isNotEmpty()) {
            return PreparedOperation(
                spec = spec,
                ranked = emptyList(),
                plan = OperationExecutionPlan(
                    operation = request.operation,
                    status = OperationPlanStatus.INVALID_CONFIGURATION,
                    message = violations.joinToString("; ") { it.reason },
                    errorCode = CapabilityErrorCode.INVALID_CONFIGURATION
                )
            )
        }

        val resolver = resolverFor(request)
        val ranked = resolver.candidates(request, spec, strategies)
            .let(resolver::rank)
        val supported = ranked.filter { it.supported }
        val automatic = supported.firstOrNull {
            it.strategy.id != StrategyId.SETTINGS_USER_ACTION
        }
        val selected = automatic ?: supported.firstOrNull()
        val privilegedBlockedByPolicy = strategies.any {
            it.id in spec.strategies && isPrivileged(it.id)
        } && !request.allowPrivilegedStrategies

        val status = when {
            automatic != null -> OperationPlanStatus.READY
            selected?.strategy?.id == StrategyId.SETTINGS_USER_ACTION ->
                OperationPlanStatus.PENDING_USER_ACTION
            privilegedBlockedByPolicy -> OperationPlanStatus.PENDING_USER_ACTION
            else -> OperationPlanStatus.UNAVAILABLE
        }
        val message = when (status) {
            OperationPlanStatus.READY ->
                "Ready via \${selected?.strategy?.id?.name ?: "available strategy"}"
            OperationPlanStatus.PENDING_USER_ACTION ->
                if (selected?.strategy?.id == StrategyId.SETTINGS_USER_ACTION) {
                    "Automatic execution is unavailable; Android Settings requires user action"
                } else {
                    "Executing this operation requires an authorized privileged provider"
                }
            OperationPlanStatus.UNAVAILABLE ->
                "No registered automatic strategy is available on this device right now"
            OperationPlanStatus.INVALID_CONFIGURATION ->
                "Operation parameters are invalid"
        }
        val errorCode = when (status) {
            OperationPlanStatus.PENDING_USER_ACTION -> CapabilityErrorCode.PERMISSION_DENIED
            OperationPlanStatus.UNAVAILABLE -> CapabilityErrorCode.BACKEND_UNAVAILABLE
            OperationPlanStatus.INVALID_CONFIGURATION -> CapabilityErrorCode.INVALID_CONFIGURATION
            OperationPlanStatus.READY -> null
        }

        return PreparedOperation(
            spec = spec,
            ranked = ranked,
            plan = OperationExecutionPlan(
                operation = request.operation,
                status = status,
                selectedStrategy = selected?.strategy?.id,
                candidates = ranked.map { candidate ->
                    StrategyPlanCandidate(
                        strategy = candidate.strategy.id,
                        available = candidate.supported,
                        selected = candidate.strategy.id == selected?.strategy?.id,
                        interactive = candidate.strategy.id == StrategyId.SETTINGS_USER_ACTION,
                        permissionRequired = candidate.availability.permissionRequired,
                        confidence = candidate.confidence,
                        reason = candidate.reason,
                        evidenceScore = candidate.evidenceScore,
                        privilegeCost = candidate.privilegeCost
                    )
                },
                message = message,
                errorCode = errorCode
            )
        )
    }

    private fun terminalOutcome(plan: OperationExecutionPlan): OperationOutcome = when (plan.status) {
        OperationPlanStatus.INVALID_CONFIGURATION ->
            OperationOutcome.failed(
                plan.operation,
                CapabilityErrorCode.INVALID_CONFIGURATION,
                plan.message
            )
        OperationPlanStatus.PENDING_USER_ACTION ->
            OperationOutcome(
                operation = plan.operation,
                status = OperationOutcomeStatus.PENDING_USER_ACTION,
                errorCode = plan.errorCode ?: CapabilityErrorCode.PERMISSION_DENIED,
                message = plan.message,
                strategy = plan.selectedStrategy
            )
        OperationPlanStatus.UNAVAILABLE ->
            OperationOutcome.unsupported(plan.operation, plan.message)
        OperationPlanStatus.READY ->
            OperationOutcome.failed(
                plan.operation,
                CapabilityErrorCode.UNKNOWN_ERROR,
                "Execution plan was ready but no executable strategy was retained"
            )
    }

    private data class PreparedOperation(
        val spec: OperationSpec?,
        val ranked: List<StrategyCandidate>,
        val plan: OperationExecutionPlan
    )

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
        val requestedEnabled = request.parameters["enabled"]?.toBooleanStrictOrNull()
        val observation = observePostCondition(
            request = request,
            getStateId = getStateId,
            executingStrategy = strategy,
            requestedEnabled = requestedEnabled,
            requestedValue = null
        )
        val observed = observation.booleanValue
        val reconciled = when {
            observed != null && requestedEnabled != null && observed == requestedEnabled ->
                OperationOutcome(
                    operation = request.operation,
                    status = OperationOutcomeStatus.SUCCESS,
                    strategy = strategy.id,
                    message = "Reconciled: observed state matches the requested state",
                    verification = com.nexaflow.domain.capability.VerificationResult(
                        attempted = true, verified = true,
                        message = "Post-condition verified by " +
                            (observation.strategyId?.name ?: "state reader")
                    ),
                    metadata = unknownOutcome.metadata
                )
            observed != null && requestedEnabled != null ->
                // The read-back is reliable and contradicts the request after
                // the bounded settling window: the side effect did not land.
                unknownOutcome.copy(
                    status = OperationOutcomeStatus.FAILED,
                    errorCode = CapabilityErrorCode.UNKNOWN_ERROR,
                    message = "Outcome could not be confirmed; observed state differs"
                )
            else ->
                // No comparable requested state (one-shot transitions such as
                // force-stop/clear-data) or an unreadable read-back: the side
                // effect remains unconfirmed — UNKNOWN, never a fabricated
                // failure, and never a claimed success.
                unknownOutcome.copy(
                    message = unknownOutcome.message + " (outcome unconfirmed; no comparable read-back)"
                )
        }
        // Reconciliation IS the verification pass: evidence/health are scored
        // on its verdict, never on the raw UNKNOWN transport result.
        recordPostVerification(reconciled)
        return reconciled
    }

    /**
     * NF-P0-002: the only place positive evidence/health is written. A record
     * is scored strictly by the post-verification verdict:
     * - verified SUCCESS → verified-success evidence + healthy;
     * - unverified transport SUCCESS → unverified evidence only (no health
     *   success, so confidence never rises on claims nobody observed);
     * - verification-FAILED / final UNKNOWN → failure evidence + unhealthy,
     *   exactly as if the strategy had reported the failure itself.
     */
    private fun recordPostVerification(outcome: OperationOutcome) {
        val strategyId = outcome.strategy ?: return
        when {
            outcome.isSuccess && outcome.verification?.verified == true -> {
                evidenceStore.recordSuccess(
                    outcome.operation, strategyId, fingerprint.deviceKey,
                    verified = true, latencyMs = outcome.durationMs
                )
                healthTracker.recordSuccess(strategyId, fingerprint.deviceKey)
            }
            outcome.isSuccess -> {
                evidenceStore.recordSuccess(
                    outcome.operation, strategyId, fingerprint.deviceKey,
                    verified = false, latencyMs = outcome.durationMs
                )
            }
            else -> {
                evidenceStore.recordFailure(outcome.operation, strategyId, fingerprint.deviceKey)
                healthTracker.recordFailure(strategyId, fingerprint.deviceKey)
            }
        }
    }

    /**
     * NF-P0-002: REQUIRED verification is strict. A transport SUCCESS is only
     * trustworthy when the actual post-condition was observed to match the
     * request:
     * - read-back observable and matching  → SUCCESS (verified);
     * - read-back observable and different → FAILED / VERIFICATION_FAILED;
     * - read-back unreadable               → UNKNOWN ("outcome unconfirmed"),
     *   never SUCCESS — an unobserved state is not a verified one.
     * BEST_EFFORT keeps the older honest-but-unverified behavior.
     */
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
        if (getStateId == null) {
            return outcome.copy(
                verification = com.nexaflow.domain.capability.VerificationResult(
                    attempted = false,
                    verified = false,
                    message = "No read-back operation is registered for this write"
                )
            )
        }
        val requestedEnabled = outcome.metadata["requestedEnabled"]
        val requestedEnabledBoolean = requestedEnabled?.toBooleanStrictOrNull()
        val requestedValue = request.parameters["value"] ?: request.parameters["seconds"]
        val observation = observePostCondition(
            request = request,
            getStateId = getStateId,
            executingStrategy = strategy,
            requestedEnabled = requestedEnabledBoolean,
            requestedValue = requestedValue
        )
        val observed = observation.booleanValue
        val observedValue = observation.scalarValue

        // Package one-shot transitions (force-stop, clear-data) have no
        // requested toggle to compare against: their read-back proves nothing
        // about the side effect. Their specs declare BEST_EFFORT precisely so
        // a transport success stays honest-but-unverified instead of being
        // fabricated into a boolean verdict here.
        val comparable = requestedEnabled != null || requestedValue != null

        // A read-back matches when the boolean state matches the requested
        // toggle, or (for value writes) the observed scalar equals the value
        // the strategy applied.
        val booleanMatch = requestedEnabled != null && (
            (observed == true && requestedEnabled == "true") ||
                (observed == false && requestedEnabled == "false")
            )
        val valueMatch = requestedValue != null && observedValue != null &&
            observedValue == requestedValue
        val matched = booleanMatch || valueMatch

        val verification = com.nexaflow.domain.capability.VerificationResult(
            attempted = observed != null || observedValue != null,
            verified = matched,
            message = when {
                observed != null -> "Observed state: " + observed + " via " +
                    (observation.strategyId?.name ?: "state reader")
                observedValue != null -> "Observed value: " + observedValue + " via " +
                    (observation.strategyId?.name ?: "state reader")
                else -> "Strategy cannot read back the state; outcome is unconfirmed"
            }
        )
        val merged = outcome.copy(verification = verification)
        if (spec.verificationMode != com.nexaflow.domain.capability.VerificationMode.REQUIRED) {
            return merged
        }
        return when {
            !comparable -> merged.copy(
                // REQUIRED without a comparable request (e.g. a read-back-only
                // pair) can never be judged: unconfirmed, not success.
                status = OperationOutcomeStatus.UNKNOWN,
                message = outcome.message + " (outcome unconfirmed: no comparable post-condition)"
            )
            verification.attempted && !verification.verified -> merged.copy(
                status = OperationOutcomeStatus.FAILED,
                errorCode = CapabilityErrorCode.VERIFICATION_FAILED
            )
            verification.attempted -> merged // observed and matching: verified SUCCESS
            else -> merged.copy(
                // NF-P0-002 strictness: an unreadable post-condition is
                // UNKNOWN — the side effect may or may not have landed — so
                // callers reconcile instead of trusting an unverified claim.
                status = OperationOutcomeStatus.UNKNOWN,
                message = outcome.message + " (outcome unconfirmed: state could not be read back)"
            )
        }
    }

    /**
     * Reads a write's post-condition without requiring the executor to also
     * implement the paired GET operation. Root may be the only backend allowed
     * to toggle NFC/SoftAP while the public framework remains the most
     * trustworthy observer of the resulting state.
     *
     * State transitions are asynchronous, so a bounded settling window keeps a
     * transient old value from being misclassified as a verification failure.
     */
    private suspend fun observePostCondition(
        request: TypedOperationRequest,
        getStateId: SemanticOperationId,
        executingStrategy: CapabilityStrategy,
        requestedEnabled: Boolean?,
        requestedValue: String?
    ): StateObservation {
        val readSpec = registry.specFor(getStateId) ?: return StateObservation()
        val readRequest = request.copy(operation = getStateId)

        val rankedReaders = resolverFor(readRequest)
            .candidates(readRequest, readSpec, strategies)
            .let { resolverFor(readRequest).rank(it) }
            .filter { it.supported }
            .map { it.strategy }

        val readers = buildList {
            if (
                getStateId in executingStrategy.supportedOperations &&
                executingStrategy.id in readSpec.strategies &&
                (!isPrivileged(executingStrategy.id) || request.allowPrivilegedStrategies)
            ) {
                add(executingStrategy)
            }
            rankedReaders
                .filterNot { it.id == executingStrategy.id }
                .forEach(::add)
        }

        if (readers.isEmpty()) return StateObservation()

        var lastObserved = StateObservation()
        for (delayMs in VERIFICATION_POLL_DELAYS_MS) {
            if (delayMs > 0L) delay(delayMs)
            for (reader in readers) {
                val booleanValue = runCatching {
                    reader.readState(readRequest, getStateId)
                }.getOrNull()
                if (booleanValue != null) {
                    val observed = StateObservation(
                        booleanValue = booleanValue,
                        strategyId = reader.id
                    )
                    lastObserved = observed
                    if (requestedEnabled == null || booleanValue == requestedEnabled) {
                        return observed
                    }
                    continue
                }

                val scalarValue = runCatching {
                    reader.readStateValue(readRequest, getStateId)
                }.getOrNull()
                if (scalarValue != null) {
                    val observed = StateObservation(
                        scalarValue = scalarValue,
                        strategyId = reader.id
                    )
                    lastObserved = observed
                    if (requestedValue == null || scalarValue == requestedValue) {
                        return observed
                    }
                }
            }
        }
        return lastObserved
    }

    private data class StateObservation(
        val booleanValue: Boolean? = null,
        val scalarValue: String? = null,
        val strategyId: StrategyId? = null
    )

    private fun isPrivileged(strategyId: StrategyId): Boolean =
        strategyId == StrategyId.SHIZUKU_USER_SERVICE || strategyId == StrategyId.ROOT_SHELL

    private companion object {
        // Cumulative wait is 2.7s. Most radios settle sooner, while NFC/SoftAP
        // on customized ROMs get enough time for an honest read-back.
        val VERIFICATION_POLL_DELAYS_MS =
            longArrayOf(0L, 100L, 200L, 400L, 800L, 1_200L)
    }
}
