package com.nexaflow.core.capability

import androidx.lifecycle.ViewModel
import com.nexaflow.core.execution.capability.CapabilityRegistry
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilitySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Read-only Settings state; it never requests privileges or executes a capability. */
@HiltViewModel
class CapabilityCenterViewModel @Inject constructor(
    capabilityStateStore: CapabilityStateStore,
    registry: CapabilityRegistry
) : ViewModel() {
    val snapshot: StateFlow<CapabilitySnapshot> = capabilityStateStore.snapshot
    val environmentReports: StateFlow<List<CapabilityEnvironmentReport>> = capabilityStateStore.environmentReports
    val capabilityLabels: Map<com.nexaflow.domain.capability.CapabilityId, String> =
        registry.descriptors().associate { it.id to it.displayName }

    fun unavailable(snapshot: CapabilitySnapshot) = snapshot.reports.values
        .filter { it.availability != CapabilityAvailability.AVAILABLE }
        .sortedBy { capabilityLabels[it.capability] ?: it.capability.name }
}
