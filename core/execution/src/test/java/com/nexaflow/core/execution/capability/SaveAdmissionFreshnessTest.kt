package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the first-save race: `saveAutomation` used to read
 * `capabilityStateStore.snapshot.value` synchronously while the refresh it
 * (or ON_RESUME) triggered was still in flight — the task was classified
 * inadmissible from the PRE-refresh observation and silently saved disabled
 * even though the device could run it. [CapabilityStateStore.freshSnapshot]
 * must return an observation made at or after the refresh call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveAdmissionFreshnessTest {

    private class MutableBackend(
        var availability: CapabilityAvailability
    ) : CapabilityBackend {
        override val id: CapabilityBackendId = CapabilityBackendId.ANDROID_API
        override val supportedCapabilities: Set<CapabilityId> = setOf(CapabilityId.DEVICE_STATE_READ)

        override suspend fun availability(request: CapabilityRequest): BackendAvailability =
            BackendAvailability(backend = id, availability = availability)

        override suspend fun execute(request: CapabilityRequest): CapabilityResult =
            CapabilityResult.unsupported("Not used by state-store test")
    }

    private fun registry(backend: MutableBackend) = CapabilityRegistry.of(
        descriptors = listOf(
            CapabilityDescriptor(
                id = CapabilityId.DEVICE_STATE_READ,
                displayName = "Device state",
                description = "Test descriptor",
                supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
            )
        ),
        backends = listOf(backend)
    )

    private fun inspector() = CapabilityEnvironmentInspector(
        shizukuInstalled = { false },
        shizukuRunning = { false },
        shizukuGranted = { false },
        shizukuUserServiceBound = { false },
        suBinaryPresent = { false },
        rootAvailable = { false },
        deviceOwner = { false }
    )

    @Test
    fun `freshSnapshot returns an observation newer than the pre-refresh one`() = runTest {
        val backend = MutableBackend(CapabilityAvailability.UNAVAILABLE)
        var now = 1_000L
        val store = CapabilityStateStore(
            registry = registry(backend),
            environmentInspector = inspector(),
            scope = this,
            nowMs = { now += 500L; now },
            registerShizukuStateListener = { listener -> listener() },
            minRefreshIntervalMs = 0L
        )
        advanceUntilIdle()
        val beforeSave = store.snapshot.value.observedAtMs
        assertTrue("initial scan must have completed (observedAtMs=$beforeSave)", beforeSave > 0L)

        backend.availability = CapabilityAvailability.AVAILABLE
        // The store worker runs on backgroundScope so advanceUntilIdle inside
        // freshSnapshot's suspension points can drive it to completion.
        val fresh = store.freshSnapshot()

        assertTrue(
            "the save decision must be based on an observation made after the refresh request (fresh=${fresh.observedAtMs}, before=$beforeSave)",
            fresh.observedAtMs > beforeSave
        )
        assertEquals(
            "the refreshed observation must carry the new device reality",
            CapabilityAvailability.AVAILABLE,
            fresh.availabilityOf(CapabilityId.DEVICE_STATE_READ)
        )
    }

    @Test
    fun `freshSnapshot bypasses passive min-refresh backoff and still returns promptly`() = runTest {
        val backend = MutableBackend(CapabilityAvailability.AVAILABLE)
        var now = 1_000_000L
        val store = CapabilityStateStore(
            registry = registry(backend),
            environmentInspector = inspector(),
            scope = this,
            nowMs = { now += 100L; now },
            registerShizukuStateListener = { listener -> listener() },
            minRefreshIntervalMs = 30_000L
        )
        advanceUntilIdle()
        val observedAt = store.snapshot.value.observedAtMs
        assertTrue(observedAt > 0L)

        val fresh = store.freshSnapshot(budgetMs = 200L)

        assertTrue(
            "an explicit freshness request must bypass passive backoff (fresh=${fresh.observedAtMs}, before=$observedAt)",
            fresh.observedAtMs > observedAt
        )
    }
}
