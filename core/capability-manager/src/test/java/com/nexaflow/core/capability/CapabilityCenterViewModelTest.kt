package com.nexaflow.core.capability

import com.nexaflow.core.execution.capability.CapabilityEnvironmentInspector
import com.nexaflow.core.execution.capability.CapabilityRegistry
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilitySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityCenterViewModelTest {
    @Test
    fun labelsComeFromRegistryAndUnavailableCapabilitiesAreSortedByLabel() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            val registry = CapabilityRegistry.of(
                descriptors = listOf(
                    descriptor(CapabilityId.DEVICE_STATE_READ, "Device state"),
                    descriptor(CapabilityId.PACKAGE_READ, "Applications")
                ),
                backends = emptyList()
            )
            val store = CapabilityStateStore(
                registry = registry,
                environmentInspector = CapabilityEnvironmentInspector(
                    shizukuInstalled = { false },
                    shizukuRunning = { false },
                    shizukuGranted = { false },
                    shizukuUserServiceBound = { false },
                    suBinaryPresent = { false },
                    rootAvailable = { false },
                    deviceOwner = { false }
                ),
                scope = scope,
                registerShizukuStateListener = {}
            )
            val viewModel = CapabilityCenterViewModel(store, registry)
            val snapshot = CapabilitySnapshot(
                reports = mapOf(
                    CapabilityId.DEVICE_STATE_READ to report(CapabilityId.DEVICE_STATE_READ),
                    CapabilityId.PACKAGE_READ to report(CapabilityId.PACKAGE_READ)
                ),
                observedAtMs = 1L
            )

            assertEquals("Applications", viewModel.capabilityLabels[CapabilityId.PACKAGE_READ])
            assertEquals(
                listOf(CapabilityId.PACKAGE_READ, CapabilityId.DEVICE_STATE_READ),
                viewModel.unavailable(snapshot).map { it.capability }
            )
            assertEquals(emptyList<com.nexaflow.domain.capability.CapabilityEnvironmentReport>(), viewModel.environmentReports.value)
        } finally {
            scope.cancel()
        }
    }

    private fun descriptor(id: CapabilityId, name: String) = CapabilityDescriptor(
        id = id,
        displayName = name,
        description = "test descriptor"
    )

    private fun report(id: CapabilityId) = CapabilityAvailabilityReport(
        capability = id,
        availability = CapabilityAvailability.UNAVAILABLE,
        backends = emptyList()
    )
}
