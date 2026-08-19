package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.NetworkRequirement
import com.nexaflow.domain.capability.PolicyBlockReason
import com.nexaflow.domain.capability.PolicyEvaluation
import com.nexaflow.domain.capability.ThermalState

/**
 * One concrete, independently health-checked implementation of a capability.
 * Implementations belong to Android/ROM integration modules; this contract
 * deliberately contains no Android framework types.
 */
interface CapabilityBackend {
    val id: CapabilityBackendId
    val supportedCapabilities: Set<CapabilityId>

    /**
     * Returns the live ability of this backend to execute [request] now. A
     * backend may be known but unavailable because a binder died, an Android
     * permission was revoked, or the device version is incompatible.
     */
    suspend fun availability(request: CapabilityRequest): BackendAvailability

    /** Executes a request only after the resolver has selected this backend. */
    suspend fun execute(request: CapabilityRequest): CapabilityResult

    /**
     * Confirms an externally observable post-condition. Backends that cannot
     * observe one must leave this false; REQUIRED verification then fails
     * safely rather than upgrading a transport response to a proven result.
     */
    suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult =
        VerificationResult(
            attempted = false,
            verified = false,
            message = "Backend does not provide post-condition verification"
        )
}

/**
 * Registry of static capability declarations and runtime backend providers.
 * Construction fails on duplicate identifiers to prevent ambiguous execution.
 */
class CapabilityRegistry private constructor(
    private val descriptorsById: Map<CapabilityId, CapabilityDescriptor>,
    private val backendsById: Map<CapabilityBackendId, CapabilityBackend>
) {
    fun descriptorFor(id: CapabilityId): CapabilityDescriptor? = descriptorsById[id]

    fun descriptors(): List<CapabilityDescriptor> = descriptorsById.values.sortedBy { it.id.name }

    fun backendFor(id: CapabilityBackendId): CapabilityBackend? = backendsById[id]

    fun backends(): List<CapabilityBackend> = backendsById.values.sortedBy { it.id.name }

    fun backendsFor(descriptor: CapabilityDescriptor): List<CapabilityBackend> =
        descriptor.supportedBackends.mapNotNull(::backendFor)

    companion object {
        fun of(
            descriptors: List<CapabilityDescriptor>,
            backends: List<CapabilityBackend>
        ): CapabilityRegistry {
            val descriptorMap = descriptors.associateBy { it.id }
            require(descriptorMap.size == descriptors.size) { "A capability descriptor was registered twice" }
            val backendMap = backends.associateBy { it.id }
            require(backendMap.size == backends.size) { "A capability backend was registered twice" }
            return CapabilityRegistry(descriptorMap, backendMap)
        }
    }
}

/** Pure policy evaluation, isolated for deterministic unit testing. */
object ExecutionPolicyEvaluator {
    fun evaluate(policy: ExecutionPolicy, state: CapabilityDeviceState): PolicyEvaluation {
        val reasons = buildList {
            if (policy.network == NetworkRequirement.WIFI_ONLY && state.wifiConnected != true) {
                add(PolicyBlockReason.NETWORK_REQUIREMENT_NOT_MET)
            }
            val batteryPercent = state.batteryPercent
            if (policy.minimumBatteryPercent > 0 &&
                (batteryPercent == null || batteryPercent < policy.minimumBatteryPercent)
            ) {
                add(PolicyBlockReason.BATTERY_REQUIREMENT_NOT_MET)
            }
            if (policy.chargingRequired && state.charging != true) {
                add(PolicyBlockReason.CHARGING_REQUIREMENT_NOT_MET)
            }
            if (policy.screenOffRequired && state.screenInteractive != false) {
                add(PolicyBlockReason.SCREEN_REQUIREMENT_NOT_MET)
            }
            if (policy.rejectCriticalThermalState && state.thermalState == ThermalState.CRITICAL) {
                add(PolicyBlockReason.THERMAL_REQUIREMENT_NOT_MET)
            }
        }
        return PolicyEvaluation(allowed = reasons.isEmpty(), reasons = reasons)
    }
}

/** A resolver outcome exposed to dry-run, diagnostics, and the executor. */
data class CapabilityResolution(
    val descriptor: CapabilityDescriptor?,
    val policy: PolicyEvaluation,
    val candidates: List<BackendAvailability>,
    val selectedBackend: CapabilityBackend? = null,
    val failure: CapabilityResult? = null
) {
    val isResolved: Boolean get() = selectedBackend != null
}

/**
 * Resolves a request through the descriptor, current device policy, backend
 * health, and configured safe priority. A Root-enabled device is never enough
 * to select Root when a lower-privilege backend is genuinely available.
 */
class CapabilityResolver(
    private val registry: CapabilityRegistry,
    private val priority: List<CapabilityBackendId> = DEFAULT_PRIORITY
) {

    fun validate(request: CapabilityRequest): CapabilityValidationResult =
        CapabilityRequestValidator.validate(registry.descriptorFor(request.capability), request)

    suspend fun resolve(
        request: CapabilityRequest,
        state: CapabilityDeviceState
    ): CapabilityResolution {
        val descriptor = registry.descriptorFor(request.capability)
            ?: return CapabilityResolution(
                descriptor = null,
                policy = PolicyEvaluation(allowed = false),
                candidates = emptyList(),
                failure = CapabilityResult.unsupported(
                    message = "Capability ${request.capability} is not registered"
                )
            )

        val policy = ExecutionPolicyEvaluator.evaluate(request.policy, state)
        if (!policy.allowed) {
            return CapabilityResolution(
                descriptor = descriptor,
                policy = policy,
                candidates = emptyList(),
                failure = CapabilityResult.failed(
                    errorCode = CapabilityErrorCode.POLICY_NOT_SATISFIED,
                    message = "Execution policy requirements are not met"
                )
            )
        }

        val allowedIds = request.policy.allowedBackends.toSet()
        val declaredBackends = registry.backendsFor(descriptor)
            .filter { request.capability in it.supportedCapabilities }
            .filter { allowedIds.isEmpty() || it.id in allowedIds }
        if (declaredBackends.isEmpty()) {
            return CapabilityResolution(
                descriptor = descriptor,
                policy = policy,
                candidates = emptyList(),
                failure = CapabilityResult.unsupported(
                    message = "No allowed backend is registered for ${request.capability}",
                    errorCode = CapabilityErrorCode.BACKEND_UNAVAILABLE
                )
            )
        }
        val permittedBackends = declaredBackends.filter { backend ->
            request.policy.allowPrivilegedBackends || backend.id !in PRIVILEGED_BACKENDS
        }
        if (permittedBackends.isEmpty()) {
            val blockedPolicy = policy.copy(
                allowed = false,
                reasons = policy.reasons + PolicyBlockReason.PRIVILEGE_NOT_ALLOWED
            )
            return CapabilityResolution(
                descriptor = descriptor,
                policy = blockedPolicy,
                candidates = emptyList(),
                failure = CapabilityResult.failed(
                    errorCode = CapabilityErrorCode.POLICY_NOT_SATISFIED,
                    message = "Privileged backend use was not authorized by execution policy"
                )
            )
        }

        val liveCandidates = permittedBackends.map { backend ->
            backend to runCatching { backend.availability(request) }.getOrElse { throwable ->
                BackendAvailability(
                    backend = backend.id,
                    availability = CapabilityAvailability.UNAVAILABLE,
                    reason = throwable.message ?: "Backend availability check failed"
                )
            }
        }
        val ordered = liveCandidates.sortedWith(
            compareBy<Pair<CapabilityBackend, BackendAvailability>> {
                preferenceIndex(it.first.id, request.policy)
            }.thenBy { priorityIndex(it.first.id) }
        )
        val selected = ordered.firstOrNull { (_, availability) ->
            availability.availability == CapabilityAvailability.AVAILABLE ||
                availability.availability == CapabilityAvailability.PARTIAL
        }
        if (selected != null) {
            return CapabilityResolution(
                descriptor = descriptor,
                policy = policy,
                candidates = ordered.map { it.second },
                selectedBackend = selected.first
            )
        }

        val error = ordered.firstOrNull { it.second.availability == CapabilityAvailability.PERMISSION_REQUIRED }
            ?.let {
                CapabilityResult.failed(
                    errorCode = CapabilityErrorCode.PERMISSION_DENIED,
                    message = it.second.reason ?: "Required permission is unavailable",
                    backend = it.first.id
                )
            }
            ?: CapabilityResult.unsupported(
                message = ordered.mapNotNull { it.second.reason }.firstOrNull()
                    ?: "No backend is currently available for ${request.capability}",
                errorCode = CapabilityErrorCode.BACKEND_UNAVAILABLE
            )
        return CapabilityResolution(
            descriptor = descriptor,
            policy = policy,
            candidates = ordered.map { it.second },
            failure = error
        )
    }

    private fun preferenceIndex(id: CapabilityBackendId, policy: ExecutionPolicy): Int {
        val preferred = policy.preferredBackends
        return if (id in preferred) preferred.indexOf(id) else Int.MAX_VALUE
    }

    private fun priorityIndex(id: CapabilityBackendId): Int =
        priority.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private companion object {
        val DEFAULT_PRIORITY = listOf(
            CapabilityBackendId.ANDROID_API,
            CapabilityBackendId.INTENT,
            CapabilityBackendId.PACKAGE_MANAGER,
            CapabilityBackendId.PACKAGE_INSTALLER,
            CapabilityBackendId.NETWORK,
            CapabilityBackendId.ACCESSIBILITY,
            CapabilityBackendId.SHIZUKU,
            CapabilityBackendId.ROOT,
            CapabilityBackendId.ADB,
            CapabilityBackendId.OEM,
            CapabilityBackendId.NATIVE
        )
        val PRIVILEGED_BACKENDS = setOf(
            CapabilityBackendId.SHIZUKU,
            CapabilityBackendId.ROOT,
            CapabilityBackendId.ADB
        )
    }
}

/**
 * The central capability execution seam. The result is normalized so callers
 * can log backend/error/verification data without parsing a human message.
 */
class CapabilityExecutionService(
    private val resolver: CapabilityResolver,
    private val deviceStateProvider: suspend () -> CapabilityDeviceState,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun execute(request: CapabilityRequest): CapabilityResult {
        val startedAt = nowMs()
        val validation = resolver.validate(request)
        if (!validation.isValid) {
            return CapabilityResult.failed(
                errorCode = CapabilityErrorCode.INVALID_CONFIGURATION,
                message = "Capability request validation failed",
            ).copy(
                metadata = mapOf("validationCodes" to validation.issues.joinToString(",") { it.code.name }),
                durationMs = nowMs() - startedAt
            )
        }
        val resolution = resolver.resolve(request, deviceStateProvider())
        val backend = resolution.selectedBackend
        if (backend == null) {
            return (resolution.failure ?: CapabilityResult.unsupported("Capability resolution failed"))
                .withDurationIfMissing(nowMs() - startedAt)
        }

        var attempt = 0
        var outcome: CapabilityResult
        while (true) {
            attempt++
            outcome = executeAttempt(backend, request)
            if (outcome.errorCode !in request.policy.retry.retryableErrors ||
                attempt >= request.policy.retry.maxAttempts ||
                outcome.status == CapabilityStatus.CANCELLED
            ) break
            val exponential = request.policy.retry.baseDelayMs
                .saturatingTimes(1L shl (attempt - 1).coerceAtMost(20))
            kotlinx.coroutines.delay(exponential.coerceAtMost(request.policy.retry.capDelayMs))
        }

        val normalized = outcome.copy(
            backend = outcome.backend ?: backend.id,
            durationMs = outcome.durationMs.takeIf { it > 0 } ?: (nowMs() - startedAt),
            metadata = outcome.metadata + ("attempts" to attempt.toString())
        )
        return verifyIfNeeded(backend, request, normalized)
    }

    private suspend fun executeAttempt(
        backend: CapabilityBackend,
        request: CapabilityRequest
    ): CapabilityResult = try {
        kotlinx.coroutines.withTimeoutOrNull(request.policy.timeoutMs) { backend.execute(request) }
            ?: CapabilityResult.failed(
                errorCode = CapabilityErrorCode.TIMEOUT,
                message = "Capability execution timed out",
                backend = backend.id
            )
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        CapabilityResult(
            status = CapabilityStatus.CANCELLED,
            backend = backend.id,
            errorCode = CapabilityErrorCode.CANCELLED,
            message = "Capability execution was cancelled"
        )
    } catch (throwable: Throwable) {
        CapabilityResult.failed(
            errorCode = CapabilityErrorCode.UNKNOWN_ERROR,
            message = throwable.message ?: "Capability backend failed",
            backend = backend.id
        )
    }

    private suspend fun verifyIfNeeded(
        backend: CapabilityBackend,
        request: CapabilityRequest,
        outcome: CapabilityResult
    ): CapabilityResult {
        if (outcome.status != CapabilityStatus.SUCCESS || request.verification == com.nexaflow.domain.capability.VerificationMode.NONE) {
            return outcome
        }
        val verification = runCatching { backend.verify(request, outcome) }.getOrElse { error ->
            VerificationResult(false, false, error.message ?: "Verification failed")
        }
        if (request.verification == com.nexaflow.domain.capability.VerificationMode.REQUIRED && !verification.verified) {
            return CapabilityResult.failed(
                errorCode = CapabilityErrorCode.VERIFICATION_FAILED,
                message = verification.message,
                backend = backend.id,
                durationMs = outcome.durationMs
            ).copy(verification = verification, metadata = outcome.metadata)
        }
        return outcome.copy(verification = verification)
    }

    private fun Long.saturatingTimes(other: Long): Long =
        if (this == 0L || other == 0L) 0L else if (this > Long.MAX_VALUE / other) Long.MAX_VALUE else this * other

    private fun CapabilityResult.withDurationIfMissing(durationMs: Long): CapabilityResult =
        if (this.durationMs > 0) this else copy(durationMs = durationMs)
}

/** Read-only diagnostics facade used by settings and dry-run UI. */
class CapabilityDiagnostics(private val registry: CapabilityRegistry) {
    suspend fun report(request: CapabilityRequest): CapabilityAvailabilityReport {
        val descriptor = registry.descriptorFor(request.capability)
            ?: return CapabilityAvailabilityReport(
                capability = request.capability,
                availability = CapabilityAvailability.UNSUPPORTED,
                backends = emptyList(),
                reason = "Capability is not registered"
            )
        val candidates = registry.backendsFor(descriptor)
            .filter { request.capability in it.supportedCapabilities }
            .map { backend ->
                runCatching { backend.availability(request) }.getOrElse { throwable ->
                    BackendAvailability(
                        backend = backend.id,
                        availability = CapabilityAvailability.UNAVAILABLE,
                        reason = throwable.message ?: "Backend availability check failed"
                    )
                }
            }
        val aggregate = when {
            candidates.any { it.availability == CapabilityAvailability.AVAILABLE } ->
                CapabilityAvailability.AVAILABLE
            candidates.any { it.availability == CapabilityAvailability.PARTIAL } ->
                CapabilityAvailability.PARTIAL
            candidates.any { it.availability == CapabilityAvailability.PERMISSION_REQUIRED } ->
                CapabilityAvailability.PERMISSION_REQUIRED
            candidates.isEmpty() -> CapabilityAvailability.UNSUPPORTED
            else -> CapabilityAvailability.UNAVAILABLE
        }
        return CapabilityAvailabilityReport(
            capability = request.capability,
            availability = aggregate,
            backends = candidates,
            reason = candidates.firstOrNull { it.reason != null }?.reason
        )
    }
}
