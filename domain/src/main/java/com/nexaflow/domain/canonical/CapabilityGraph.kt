package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.PrivilegeLevel
import com.nexaflow.domain.capability.operation.DeviceFeature
import com.nexaflow.domain.capability.operation.StrategyId
import kotlinx.serialization.Serializable

/**
 * T07 — Capability Graph & Resolver (plan §7, ADR-007).
 *
 * Pure, serializable graph over the established capability vocabulary
 * ([CapabilityBackendId], [PrivilegeLevel], [StrategyId], [DeviceFeature],
 * [CapabilityAvailability]). It answers exactly one question deterministically:
 *
 * > Given a declared operation requirement and a snapshot of what this device
 * > can do right now, which declared providers can execute the intent, and
 * > which one is selected?
 *
 * What it deliberately does NOT do:
 * - no fallback that substitutes a different intent (ADR-007 invariant);
 * - no interactive hand-off ranked as a peer backend;
 * - no mutable global state — every decision is a function of
 *   (requirement, catalog, snapshot, policy).
 */
@Serializable
data class ProviderCapability(
    /** Backend channel a provider runs through, e.g. SHIZUKU or ROOT. */
    val backend: CapabilityBackendId,
    /** Optional privilege the backend must currently hold, e.g. ROOT. */
    val privilege: PrivilegeLevel? = null,
)

/**
 * One executable way to realize an operation requirement. Providers are data:
 * the same declaration always yields the same decision for the same snapshot.
 */
@Serializable
data class ProviderDescriptor(
    /** Stable provider identity; [providerId] rules match canonical IDs. */
    val providerId: String,
    /** Established semantic strategy vocabulary; ordering never uses enum position. */
    val strategy: StrategyId,
    val capabilities: Set<ProviderCapability>,
    /** Hardware/environment prerequisites, reusing the semantic-layer enum. */
    val requiredFeatures: Set<DeviceFeature> = emptySet(),
) {
    init {
        require(providerId.matches(PROVIDER_ID)) {
            "ProviderDescriptor.providerId must be a lowercase dotted stable id: $providerId"
        }
        require(capabilities.isNotEmpty()) {
            "ProviderDescriptor must declare at least one capability"
        }
    }

    companion object {
        private val PROVIDER_ID = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
    }
}

/**
 * The declared way an operation may execute. Requirements are declarative and
 * serializable; the resolver never invents a provider that is not declared.
 */
@Serializable
data class OperationCapabilityRequirements(
    val operation: OperationId,
    /** All listed providers must be consulted; none is implied. */
    val providers: List<ProviderDescriptor>,
) {
    init {
        require(providers.isNotEmpty()) {
            "OperationCapabilityRequirements requires at least one provider"
        }
        require(providers.map { it.providerId }.distinct().size == providers.size) {
            "OperationCapabilityRequirements providers must have unique ids"
        }
    }
}

/**
 * Declared user constraints. Every field is explicit; there is no implicit
 * default that silently changes which provider runs (fail-closed invariant).
 */
@Serializable
data class CapabilitySelectionPolicy(
    /** Backends the user allows. Empty = every declared backend is allowed. */
    val allowedBackends: Set<CapabilityBackendId> = emptySet(),
    /** Preferred backends applied before confidence tie-breaking. */
    val preferredBackends: Set<CapabilityBackendId> = emptySet(),
    /** Explicit opt-in without which Shizuku/Root/ADB providers are ineligible. */
    val allowPrivilegedBackends: Boolean = false,
    /** Backend the user pinned; only this backend may be selected. */
    val pinnedBackend: CapabilityBackendId? = null,
) {
    init {
        require(pinnedBackend == null || pinnedBackend in allowedBackends || allowedBackends.isEmpty()) {
            "pinnedBackend must be inside allowedBackends when allowedBackends is non-empty"
        }
    }

    companion object {
        /** Explicit user opt-in for privileged providers (Shizuku/Root/ADB). */
        val PRIVILEGED: CapabilitySelectionPolicy = CapabilitySelectionPolicy(
            allowPrivilegedBackends = true,
        )
    }
}

/**
 * What the device can do right now. Unobserved inputs are absent keys — an
 * unknown, never coerced into AVAILABLE or UNSUPPORTED.
 */
@Serializable
data class CapabilityGraphSnapshot(
    /** Availability per backend; a missing key means "not observed". */
    val backendAvailability: Map<CapabilityBackendId, CapabilityAvailability> = emptyMap(),
    /** Privileges currently granted, e.g. ROOT once a root shell was verified. */
    val grantedPrivileges: Set<PrivilegeLevel> = emptySet(),
    /** Hardware/environment features this device physically has. */
    val deviceFeatures: Set<DeviceFeature> = emptySet(),
)

/**
 * Deterministic, explainable decision for one operation requirement. Richer
 * than "a backend id": the runner needs selected + fallbacks + the exact
 * reasons, and the diagnostics UI (plan §23) renders [exclusions] directly.
 */
@Serializable
data class CapabilityResolution(
    val status: CapabilityResolutionStatus,
    /** Deterministically selected provider when [status] is [CapabilityResolutionStatus.RESOLVED]. */
    val selectedProviderId: String? = null,
    /** Remaining executable providers in deterministic fallback order. */
    val fallbackProviderIds: List<String> = emptyList(),
    /** Every excluded provider and the exact reason, for the diagnostics UI. */
    val exclusions: List<ProviderExclusion> = emptyList(),
    /** Stable machine-readable code; UI strings are produced elsewhere. */
    val errorCode: CapabilityResolutionError? = null,
) {
    val isExecutable: Boolean
        get() = status == CapabilityResolutionStatus.RESOLVED
}

@Serializable
enum class CapabilityResolutionStatus {
    RESOLVED,
    /** A provider exists but needs user action (permission/opt-in) first. */
    PENDING_USER_ACTION,
    /** Nothing can execute this intent; execution must not silently substitute. */
    UNSUPPORTED,
}

/** Machine-readable exclusion/failure codes (ADR-007 contract). */
@Serializable
enum class CapabilityResolutionError {
    CAPABILITY_MISSING,
    CAPABILITY_UNAVAILABLE,
    PRIVILEGE_NOT_GRANTED,
    HARDWARE_MISSING,
    BACKEND_NOT_ALLOWED,
    NO_PROVIDERS_DECLARED,
}

/** Why one declared provider was excluded from the resolution. */
@Serializable
data class ProviderExclusion(
    val providerId: String,
    val errorCode: CapabilityResolutionError,
    val reason: String,
)

/**
 * Stable sort key per provider: eligible providers compete in this order and
 * equal keys are broken by provider id, so the outcome is a total order.
 */
internal data class ProviderRankingKey(
    val eligible: Boolean,
    val pinned: Boolean,
    val preferred: Boolean,
    val privilegeCost: Int,
    val providerId: String,
)

/**
 * The pure resolver. Deterministic for an equivalent snapshot (ADR-007): the
 * inputs (requirement, policy, snapshot) fully determine the output — no time,
 * no randomness, no hidden mutable state.
 */
class CanonicalCapabilityResolver(
    private val requirements: Map<OperationId, OperationCapabilityRequirements>,
) {

    constructor(
        requirements: List<OperationCapabilityRequirements>,
    ) : this(requirements.associateBy { it.operation })

    init {
        requirements.values.forEach { entry ->
            require(entry.providers.isNotEmpty()) {
                "Operation ${entry.operation} must declare providers"
            }
        }
    }

    /**
     * Resolves one requirement against the snapshot. The returned resolution
     * explains every provider: selected, fallbacks, or exactly why excluded.
     */
    fun resolve(
        operation: OperationId,
        policy: CapabilitySelectionPolicy = CapabilitySelectionPolicy(),
        snapshot: CapabilityGraphSnapshot = CapabilityGraphSnapshot(),
    ): CapabilityResolution {
        val declared = requirements[operation]
            ?: return CapabilityResolution(
                status = CapabilityResolutionStatus.UNSUPPORTED,
                errorCode = CapabilityResolutionError.NO_PROVIDERS_DECLARED,
            )

        val candidates = declared.providers.map { provider ->
            evaluate(provider, policy, snapshot)
        }
        val eligible = candidates.filter { it.exclusion == null }

        if (eligible.isEmpty()) {
            return unsupportedOrPending(candidates)
        }

        val rankedKeys = eligible
            .map { candidate ->
                val provider = candidate.provider
                ProviderRankingKey(
                    eligible = true,
                    pinned = policy.pinnedBackend != null &&
                        provider.capabilities.any { it.backend == policy.pinnedBackend },
                    preferred = provider.capabilities.any { it.backend in policy.preferredBackends },
                    privilegeCost = provider.privilegeCost(),
                    providerId = provider.providerId,
                )
            }
            .sortedWith(
                compareBy<ProviderRankingKey> { !it.pinned }
                    .thenBy { !it.preferred }
                    .thenBy { it.privilegeCost }
                    .thenBy { it.providerId },
            )
        val rankedIds = rankedKeys.map { it.providerId }

        return CapabilityResolution(
            status = CapabilityResolutionStatus.RESOLVED,
            selectedProviderId = rankedIds.first(),
            fallbackProviderIds = rankedIds.drop(1),
            exclusions = candidates.mapNotNull { it.exclusion },
        )
    }

    /** Lists every declared operation id (diagnostics and conformance tests). */
    fun declaredOperations(): Set<OperationId> = requirements.keys

    private fun evaluate(
        provider: ProviderDescriptor,
        policy: CapabilitySelectionPolicy,
        snapshot: CapabilityGraphSnapshot,
    ): EvaluatedProvider {
        val backend = provider.capabilities.first().backend

        // Hardware must physically exist; no feature ⇒ hard exclusion.
        val missingFeature = provider.requiredFeatures.firstOrNull { it !in snapshot.deviceFeatures }
        if (missingFeature != null) {
            return EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.HARDWARE_MISSING,
                    reason = "device is missing ${missingFeature.name}",
                ),
            )
        }

        // Privilege must currently be granted; unknown/unavailable is not a
        // grant, so a privileged provider is excluded, not downgraded.
        val requiredPrivilege = provider.capabilities.mapNotNull { it.privilege }.firstOrNull()
        if (requiredPrivilege != null && requiredPrivilege !in snapshot.grantedPrivileges) {
            return EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.PRIVILEGE_NOT_GRANTED,
                    reason = "privilege ${requiredPrivilege.name} is not currently granted",
                ),
            )
        }

        // User policy gates. An empty allowedBackends allows every declared
        // backend, mirroring ExecutionPolicy.allowedBackends semantics.
        val backendAllowed = policy.allowedBackends.isEmpty() || backend in policy.allowedBackends
        if (!backendAllowed) {
            return EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.BACKEND_NOT_ALLOWED,
                    reason = "backend ${backend.name} is not allowed by the selection policy",
                ),
            )
        }

        // The pinned backend must be this provider's backend.
        val pinned = policy.pinnedBackend
        if (pinned != null && pinned != backend) {
            return EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.BACKEND_NOT_ALLOWED,
                    reason = "policy pinned ${pinned.name}; provider runs on ${backend.name}",
                ),
            )
        }

        // Privileged providers need explicit opt-in, exactly like the
        // ExecutionPolicy.allowPrivilegedBackends flag.
        val privileged = requiredPrivilege != null && requiredPrivilege != PrivilegeLevel.NONE
        if (privileged && !policy.allowPrivilegedBackends) {
            return EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.PRIVILEGE_NOT_GRANTED,
                    reason = "privileged backend ${backend.name} requires explicit opt-in",
                ),
            )
        }

        // Only explicit AVAILABLE admits execution. Unknown/PARTIAL/
        // PERMISSION_REQUIRED/UNAVAILABLE never upgrade to executable.
        val availability = snapshot.backendAvailability[backend]
        return when (availability) {
            CapabilityAvailability.AVAILABLE -> EvaluatedProvider(provider, null)
            CapabilityAvailability.PERMISSION_REQUIRED -> EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.CAPABILITY_MISSING,
                    reason = "backend ${backend.name} needs a permission grant first",
                ),
            )
            null -> EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.CAPABILITY_UNAVAILABLE,
                    reason = "backend ${backend.name} was not observed in the snapshot",
                ),
            )
            else -> EvaluatedProvider(
                provider,
                ProviderExclusion(
                    providerId = provider.providerId,
                    errorCode = CapabilityResolutionError.CAPABILITY_UNAVAILABLE,
                    reason = "backend ${backend.name} is ${availability.name}",
                ),
            )
        }
    }

    private fun unsupportedOrPending(
        candidates: List<EvaluatedProvider>,
    ): CapabilityResolution {
        val exclusions = candidates.mapNotNull { it.exclusion }
        val needsUserAction = candidates.any { it.exclusion?.errorCode == CapabilityResolutionError.CAPABILITY_MISSING }
        return CapabilityResolution(
            status = if (needsUserAction) {
                CapabilityResolutionStatus.PENDING_USER_ACTION
            } else {
                CapabilityResolutionStatus.UNSUPPORTED
            },
            errorCode = exclusions.firstOrNull()?.errorCode,
            exclusions = exclusions,
        )
    }

    private data class EvaluatedProvider(
        val provider: ProviderDescriptor,
        val exclusion: ProviderExclusion?,
    ) {
        val providerId: String
            get() = provider.providerId
    }
}

/** Least-privilege ordering; mirrors the semantic-layer privilege ladder. */
internal fun ProviderDescriptor.privilegeCost(): Int = when (
    capabilities.mapNotNull { it.privilege }.maxOrNull()
) {
    null -> 0
    PrivilegeLevel.NONE -> 0
    PrivilegeLevel.NORMAL -> 0
    PrivilegeLevel.ADB_SHELL -> 3
    PrivilegeLevel.SHIZUKU -> 4
    PrivilegeLevel.ROOT -> 5
    else -> 6 // legacy deprecated levels are deliberately the most expensive
}
