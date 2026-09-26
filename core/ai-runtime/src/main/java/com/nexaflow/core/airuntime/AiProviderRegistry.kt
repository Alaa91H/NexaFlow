package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiProviderRegistryState(
    val providers: List<AiProviderDescriptor> = emptyList(),
    val selectedProviderId: String? = null
) {
    val selectedProvider: AiProviderDescriptor?
        get() = providers.firstOrNull { it.id == selectedProviderId }
}

class AiProviderRegistry(
    providers: List<AiModelProvider> = emptyList()
) {
    private val providerMap = providers.associateBy { it.descriptor.value.id }
    private val _state = MutableStateFlow(
        AiProviderRegistryState(
            providers = providers.map { it.descriptor.value },
            selectedProviderId = providers.firstOrNull {
                it.descriptor.value.available
            }?.descriptor?.value?.id
        )
    )
    val state: StateFlow<AiProviderRegistryState> = _state.asStateFlow()

    fun selectedProvider(): AiModelProvider? =
        _state.value.selectedProviderId?.let(providerMap::get)

    fun select(providerId: String?): Boolean {
        if (providerId != null && providerId !in providerMap) return false
        _state.value = _state.value.copy(selectedProviderId = providerId)
        return true
    }

    fun refreshDescriptors() {
        val descriptors = providerMap.values
            .map { it.descriptor.value }
            .sortedBy { it.displayName.lowercase() }
        val selected = _state.value.selectedProviderId
            ?.takeIf { id -> descriptors.any { it.id == id && it.available } }
            ?: descriptors.firstOrNull { it.available }?.id
        _state.value = AiProviderRegistryState(descriptors, selected)
    }
}
