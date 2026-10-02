package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiProviderRegistryState(
    val providers: List<AiProviderDescriptor> = emptyList(),
    val providerHealth: Map<String, AiProviderHealthSnapshot> = emptyMap(),
    val routingPolicy: AiRoutingPolicy = AiRoutingPolicy(),
    val routeDecision: AiRouteDecision = AiRouteDecision(
        reason = AiRouteReason.NO_AVAILABLE_PROVIDER
    )
) {
    val selectedProviderId: String?
        get() = routeDecision.providerId

    val selectedProvider: AiProviderDescriptor?
        get() = providers.firstOrNull { it.id == routeDecision.providerId }

    val selectedProviderHealth: AiProviderHealthSnapshot?
        get() = routeDecision.providerId?.let(providerHealth::get)
}

class AiProviderRegistry(
    providers: List<AiModelProvider> = emptyList()
) {
    @Volatile
    private var providerMap = providers.associateBy { it.descriptor.value.id }

    private val initialDescriptors = providers
        .map { it.descriptor.value }
        .sortedWith(DESCRIPTOR_ORDER)
    private val initialHealth = initialDescriptors.associate { descriptor ->
        descriptor.id to AiProviderHealthSnapshot(providerId = descriptor.id)
    }

    private val _state = MutableStateFlow(
        AiProviderRegistryState(
            providers = initialDescriptors,
            providerHealth = initialHealth
        )
    )
    val state: StateFlow<AiProviderRegistryState> = _state.asStateFlow()

    fun selectedProvider(): AiModelProvider? =
        routeProvider(AiRoutingRequirements(requireTools = true))

    fun routeProvider(requireTools: Boolean): AiModelProvider? =
        routeProvider(AiRoutingRequirements(requireTools = requireTools))

    fun routeProvider(
        requirements: AiRoutingRequirements,
        nowMillis: Long = System.currentTimeMillis()
    ): AiModelProvider? {
        val current = _state.value
        val decision = decide(
            descriptors = current.providers,
            health = current.providerHealth,
            policy = current.routingPolicy,
            requirements = requirements,
            nowMillis = nowMillis
        )
        return decision.providerId?.let(providerMap::get)
    }

    fun routeDecision(
        requirements: AiRoutingRequirements,
        nowMillis: Long = System.currentTimeMillis()
    ): AiRouteDecision {
        val current = _state.value
        return decide(
            descriptors = current.providers,
            health = current.providerHealth,
            policy = current.routingPolicy,
            requirements = requirements,
            nowMillis = nowMillis
        )
    }

    fun updateRoutingPolicy(policy: AiRoutingPolicy) {
        val normalized = policy.copy(
            selectedProviderId = policy.selectedProviderId
                ?.takeIf { it.length in 1..MAX_PROVIDER_ID_LENGTH }
        )
        publish(
            descriptors = _state.value.providers,
            health = _state.value.providerHealth,
            policy = normalized
        )
    }

    fun select(providerId: String?): Boolean {
        if (providerId != null && providerId !in providerMap) return false
        updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.SELECTED_PROVIDER,
                selectedProviderId = providerId
            )
        )
        return true
    }

    fun refreshDescriptors() {
        val descriptors = providerMap.values
            .map { it.descriptor.value }
            .sortedWith(DESCRIPTOR_ORDER)
        val health = healthForCurrentProviders(_state.value.providerHealth, descriptors)
        publish(descriptors, health, _state.value.routingPolicy)
    }

    @Synchronized
    fun replaceProviders(providers: List<AiModelProvider>) {
        providerMap = providers.associateBy { it.descriptor.value.id }
        val descriptors = providerMap.values
            .map { it.descriptor.value }
            .sortedWith(DESCRIPTOR_ORDER)
        val health = healthForCurrentProviders(_state.value.providerHealth, descriptors)
        val selected = _state.value.routingPolicy.selectedProviderId
        val policy = if (selected != null && selected !in providerMap) {
            _state.value.routingPolicy.copy(selectedProviderId = null)
        } else {
            _state.value.routingPolicy
        }
        publish(descriptors, health, policy)
    }

    fun recordConnectionTest(
        result: AiConnectionTestResult,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (result.providerId !in providerMap) return
        val current = healthSnapshot(result.providerId)
        val updated = if (result.success) {
            current.copy(
                state = AiProviderHealthState.HEALTHY,
                verified = true,
                consecutiveFailures = 0,
                lastSuccessAtMillis = nowMillis,
                lastFailure = null,
                cooldownUntilMillis = null
            )
        } else {
            failedHealth(
                current = current,
                failure = result.failure ?: AiConnectionFailure.UNKNOWN,
                retryAfterMs = result.retryAfterMs,
                nowMillis = nowMillis,
                verificationFailure = true
            )
        }
        updateHealth(updated)
    }

    fun recordProviderSuccess(
        providerId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (providerId !in providerMap) return
        val current = healthSnapshot(providerId)
        updateHealth(
            current.copy(
                state = AiProviderHealthState.HEALTHY,
                verified = true,
                consecutiveFailures = 0,
                lastSuccessAtMillis = nowMillis,
                lastFailure = null,
                cooldownUntilMillis = null
            )
        )
    }

    fun recordProviderFailure(
        providerId: String,
        failure: AiConnectionFailure,
        retryAfterMs: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (providerId !in providerMap) return
        updateHealth(
            failedHealth(
                current = healthSnapshot(providerId),
                failure = failure,
                retryAfterMs = retryAfterMs,
                nowMillis = nowMillis,
                verificationFailure = false
            )
        )
    }

    fun clearProviderHealth(providerId: String) {
        if (providerId !in providerMap) return
        updateHealth(AiProviderHealthSnapshot(providerId = providerId))
    }

    private fun updateHealth(snapshot: AiProviderHealthSnapshot) {
        val current = _state.value
        publish(
            descriptors = current.providers,
            health = current.providerHealth + (snapshot.providerId to snapshot),
            policy = current.routingPolicy
        )
    }

    private fun healthSnapshot(providerId: String): AiProviderHealthSnapshot =
        _state.value.providerHealth[providerId]
            ?: AiProviderHealthSnapshot(providerId = providerId)

    private fun failedHealth(
        current: AiProviderHealthSnapshot,
        failure: AiConnectionFailure,
        retryAfterMs: Long?,
        nowMillis: Long,
        verificationFailure: Boolean
    ): AiProviderHealthSnapshot {
        val fatal = failure in FATAL_FAILURES
        val throttled = failure == AiConnectionFailure.RATE_LIMITED ||
            failure == AiConnectionFailure.QUOTA_EXCEEDED
        val cooldown = if (throttled) {
            nowMillis + (retryAfterMs ?: DEFAULT_RATE_LIMIT_COOLDOWN_MS)
        } else {
            null
        }
        return current.copy(
            state = when {
                fatal -> AiProviderHealthState.UNAVAILABLE
                throttled -> AiProviderHealthState.COOLDOWN
                else -> AiProviderHealthState.DEGRADED
            },
            verified = if (verificationFailure || fatal) false else current.verified,
            consecutiveFailures = current.consecutiveFailures + 1,
            lastFailureAtMillis = nowMillis,
            lastFailure = failure,
            cooldownUntilMillis = cooldown
        )
    }

    private fun publish(
        descriptors: List<AiProviderDescriptor>,
        health: Map<String, AiProviderHealthSnapshot>,
        policy: AiRoutingPolicy
    ) {
        _state.value = AiProviderRegistryState(
            providers = descriptors,
            providerHealth = health,
            routingPolicy = policy,
            routeDecision = decide(
                descriptors = descriptors,
                health = health,
                policy = policy,
                requirements = AiRoutingRequirements(requireTools = true),
                nowMillis = System.currentTimeMillis()
            )
        )
    }

    private fun decide(
        descriptors: List<AiProviderDescriptor>,
        health: Map<String, AiProviderHealthSnapshot>,
        policy: AiRoutingPolicy,
        requirements: AiRoutingRequirements,
        nowMillis: Long
    ): AiRouteDecision {
        val available = descriptors.filter(AiProviderDescriptor::available)
        if (available.isEmpty()) {
            return AiRouteDecision(reason = AiRouteReason.NO_AVAILABLE_PROVIDER)
        }

        val capable = available.filter(requirements::matches)
        if (capable.isEmpty()) {
            return AiRouteDecision(
                reason = AiRouteReason.CAPABILITY_UNAVAILABLE,
                excludedProviderIds = available.map(AiProviderDescriptor::id).sorted()
            )
        }

        val healthy = capable.filter { descriptor ->
            health[descriptor.id]
                ?.isRoutable(nowMillis, requirements.requireVerified)
                ?: !requirements.requireVerified
        }
        if (healthy.isEmpty()) {
            val reason = if (policy.mode == AiRoutingMode.SELECTED_PROVIDER) {
                AiRouteReason.SELECTED_UNHEALTHY
            } else {
                AiRouteReason.HEALTH_UNAVAILABLE
            }
            return AiRouteDecision(
                reason = reason,
                excludedProviderIds = capable.map(AiProviderDescriptor::id).sorted()
            )
        }

        return when (policy.mode) {
            AiRoutingMode.LOCAL_ONLY -> choose(
                candidates = healthy.filter { it.capabilities.local },
                health = health,
                nowMillis = nowMillis,
                successReason = AiRouteReason.LOCAL_ONLY,
                emptyReason = AiRouteReason.NO_MODE_MATCH
            )
            AiRoutingMode.CLOUD_ONLY -> choose(
                candidates = healthy.filterNot { it.capabilities.local },
                health = health,
                nowMillis = nowMillis,
                successReason = AiRouteReason.CLOUD_ONLY,
                emptyReason = AiRouteReason.NO_MODE_MATCH
            )
            AiRoutingMode.SELECTED_PROVIDER -> {
                val id = policy.selectedProviderId
                    ?: return AiRouteDecision(reason = AiRouteReason.SELECTED_UNAVAILABLE)
                val selectedDescriptor = capable.firstOrNull { it.id == id }
                if (selectedDescriptor == null) {
                    AiRouteDecision(reason = AiRouteReason.SELECTED_UNAVAILABLE)
                } else {
                    val selectedHealth = health[id]
                    if (
                        selectedHealth?.isRoutable(nowMillis, requirements.requireVerified)
                        ?: !requirements.requireVerified
                    ) {
                        AiRouteDecision(
                            providerId = selectedDescriptor.id,
                            reason = AiRouteReason.SELECTED,
                            healthState = selectedHealth?.state
                        )
                    } else {
                        AiRouteDecision(
                            reason = AiRouteReason.SELECTED_UNHEALTHY,
                            healthState = selectedHealth?.state,
                            excludedProviderIds = listOf(selectedDescriptor.id)
                        )
                    }
                }
            }
            AiRoutingMode.AUTOMATIC -> {
                val allLocal = capable.filter { it.capabilities.local }
                val healthyLocal = healthy.filter { it.capabilities.local }
                if (healthyLocal.isNotEmpty()) {
                    choose(
                        healthyLocal,
                        health,
                        nowMillis,
                        AiRouteReason.AUTOMATIC_LOCAL,
                        AiRouteReason.NO_AVAILABLE_PROVIDER
                    )
                } else if (policy.allowCloudFallback) {
                    val healthyCloud = healthy.filterNot { it.capabilities.local }
                    val reason = if (allLocal.isNotEmpty()) {
                        AiRouteReason.AUTOMATIC_HEALTH_FALLBACK
                    } else {
                        AiRouteReason.AUTOMATIC_CLOUD
                    }
                    choose(
                        healthyCloud,
                        health,
                        nowMillis,
                        reason,
                        AiRouteReason.NO_AVAILABLE_PROVIDER
                    )
                } else {
                    AiRouteDecision(
                        reason = AiRouteReason.CLOUD_FALLBACK_DISABLED,
                        excludedProviderIds = allLocal
                            .filterNot { it in healthyLocal }
                            .map(AiProviderDescriptor::id)
                            .sorted()
                    )
                }
            }
        }
    }

    private fun choose(
        candidates: List<AiProviderDescriptor>,
        health: Map<String, AiProviderHealthSnapshot>,
        nowMillis: Long,
        successReason: AiRouteReason,
        emptyReason: AiRouteReason
    ): AiRouteDecision {
        val chosen = candidates.sortedWith(
            compareBy<AiProviderDescriptor> {
                health[it.id]?.routingRank(nowMillis) ?: UNKNOWN_HEALTH_RANK
            }.thenByDescending {
                it.capabilities.toolCalling || it.capabilities.structuredOutput
            }.thenByDescending {
                it.capabilities.streaming
            }.thenBy {
                it.displayName.lowercase()
            }.thenBy {
                it.id
            }
        ).firstOrNull() ?: return AiRouteDecision(reason = emptyReason)

        return AiRouteDecision(
            providerId = chosen.id,
            reason = successReason,
            healthState = health[chosen.id]?.state
        )
    }

    private fun healthForCurrentProviders(
        current: Map<String, AiProviderHealthSnapshot>,
        descriptors: List<AiProviderDescriptor>
    ): Map<String, AiProviderHealthSnapshot> = descriptors.associate { descriptor ->
        descriptor.id to (
            current[descriptor.id]
                ?: AiProviderHealthSnapshot(providerId = descriptor.id)
            )
    }

    private companion object {
        const val MAX_PROVIDER_ID_LENGTH = 128
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 60_000L
        const val UNKNOWN_HEALTH_RANK = 1

        val FATAL_FAILURES = setOf(
            AiConnectionFailure.AUTHENTICATION,
            AiConnectionFailure.PERMISSION,
            AiConnectionFailure.ENDPOINT_NOT_FOUND,
            AiConnectionFailure.MODEL_NOT_FOUND,
            AiConnectionFailure.UNSUPPORTED_PROTOCOL
        )

        val DESCRIPTOR_ORDER = compareByDescending<AiProviderDescriptor> {
            it.capabilities.toolCalling || it.capabilities.structuredOutput
        }.thenByDescending {
            it.capabilities.streaming
        }.thenBy {
            it.displayName.lowercase()
        }.thenBy {
            it.id
        }
    }
}
