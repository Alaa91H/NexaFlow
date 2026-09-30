package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiProviderRegistryState(
    val providers: List<AiProviderDescriptor> = emptyList(),
    val routingPolicy: AiRoutingPolicy = AiRoutingPolicy(),
    val routeDecision: AiRouteDecision = AiRouteDecision(
        reason = AiRouteReason.NO_AVAILABLE_PROVIDER
    )
) {
    val selectedProviderId: String?
        get() = routeDecision.providerId

    val selectedProvider: AiProviderDescriptor?
        get() = providers.firstOrNull { it.id == routeDecision.providerId }
}

class AiProviderRegistry(
    providers: List<AiModelProvider> = emptyList()
) {
    @Volatile
    private var providerMap = providers.associateBy { it.descriptor.value.id }
    private val _state = MutableStateFlow(
        AiProviderRegistryState(
            providers = providers.map { it.descriptor.value }
                .sortedWith(DESCRIPTOR_ORDER)
        )
    )
    val state: StateFlow<AiProviderRegistryState> = _state.asStateFlow()

    fun selectedProvider(): AiModelProvider? =
        routeProvider(requireTools = true)

    fun routeProvider(requireTools: Boolean): AiModelProvider? {
        val state = _state.value
        val decision = decide(
            descriptors = state.providers,
            policy = state.routingPolicy,
            requireTools = requireTools
        )
        return decision.providerId?.let(providerMap::get)
    }

    fun updateRoutingPolicy(policy: AiRoutingPolicy) {
        val normalized = policy.copy(
            selectedProviderId = policy.selectedProviderId
                ?.takeIf { it.length in 1..MAX_PROVIDER_ID_LENGTH }
        )
        val descriptors = _state.value.providers
        _state.value = AiProviderRegistryState(
            providers = descriptors,
            routingPolicy = normalized,
            routeDecision = decide(
                descriptors = descriptors,
                policy = normalized,
                requireTools = true
            )
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
        val policy = _state.value.routingPolicy
        _state.value = AiProviderRegistryState(
            providers = descriptors,
            routingPolicy = policy,
            routeDecision = decide(
                descriptors = descriptors,
                policy = policy,
                requireTools = true
            )
        )
    }

    /** Replace runtime adapters after profile changes while keeping routing explicit. */
    @Synchronized
    fun replaceProviders(providers: List<AiModelProvider>) {
        providerMap = providers.associateBy { it.descriptor.value.id }
        refreshDescriptors()
        val selected = _state.value.routingPolicy.selectedProviderId
        if (selected != null && selected !in providerMap) {
            updateRoutingPolicy(_state.value.routingPolicy.copy(selectedProviderId = null))
        }
    }

    private fun decide(
        descriptors: List<AiProviderDescriptor>,
        policy: AiRoutingPolicy,
        requireTools: Boolean
    ): AiRouteDecision {
        val available = descriptors.filter(AiProviderDescriptor::available)
        if (available.isEmpty()) {
            return AiRouteDecision(reason = AiRouteReason.NO_AVAILABLE_PROVIDER)
        }

        val capable = available.filter { descriptor ->
            !requireTools ||
                descriptor.capabilities.toolCalling ||
                descriptor.capabilities.structuredOutput
        }
        val candidates = capable.ifEmpty { available }

        return when (policy.mode) {
            AiRoutingMode.LOCAL_ONLY -> choose(
                candidates.filter { it.capabilities.local },
                AiRouteReason.LOCAL_ONLY,
                AiRouteReason.NO_MODE_MATCH
            )
            AiRoutingMode.CLOUD_ONLY -> choose(
                candidates.filterNot { it.capabilities.local },
                AiRouteReason.CLOUD_ONLY,
                AiRouteReason.NO_MODE_MATCH
            )
            AiRoutingMode.SELECTED_PROVIDER -> {
                val id = policy.selectedProviderId
                val selected = candidates.firstOrNull { it.id == id }
                if (selected != null) {
                    AiRouteDecision(selected.id, AiRouteReason.SELECTED)
                } else {
                    AiRouteDecision(reason = AiRouteReason.SELECTED_UNAVAILABLE)
                }
            }
            AiRoutingMode.AUTOMATIC -> {
                val local = candidates.filter { it.capabilities.local }
                if (local.isNotEmpty()) {
                    choose(
                        local,
                        AiRouteReason.AUTOMATIC_LOCAL,
                        AiRouteReason.NO_AVAILABLE_PROVIDER
                    )
                } else if (policy.allowCloudFallback) {
                    choose(
                        candidates.filterNot { it.capabilities.local },
                        AiRouteReason.AUTOMATIC_CLOUD,
                        AiRouteReason.NO_AVAILABLE_PROVIDER
                    )
                } else {
                    AiRouteDecision(reason = AiRouteReason.CLOUD_FALLBACK_DISABLED)
                }
            }
        }
    }

    private fun choose(
        candidates: List<AiProviderDescriptor>,
        successReason: AiRouteReason,
        emptyReason: AiRouteReason
    ): AiRouteDecision {
        val chosen = candidates.sortedWith(DESCRIPTOR_ORDER).firstOrNull()
            ?: return AiRouteDecision(reason = emptyReason)
        return AiRouteDecision(chosen.id, successReason)
    }

    private companion object {
        const val MAX_PROVIDER_ID_LENGTH = 128

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
