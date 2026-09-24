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
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityStateStoreTest {

    @Test
    fun `initial callback and later invalidation publish fresh capability snapshots`() = runTest {
        val backend = MutableAvailabilityBackend(CapabilityAvailability.AVAILABLE)
        val registry = CapabilityRegistry.of(
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
        var eventListener: (() -> Unit)? = null
        var now = 1_000L
        val store = CapabilityStateStore(
            registry = registry,
            environmentInspector = inspector(),
            scope = this,
            nowMs = { now++ },
            registerShizukuStateListener = { listener ->
                eventListener = listener
                listener()
            }
        )

        advanceUntilIdle()
        assertEquals("the first capability refresh must never be throttled", 0L, currentTime)
        assertEquals(CapabilityAvailability.AVAILABLE, store.snapshot.value.availabilityOf(CapabilityId.DEVICE_STATE_READ))
        assertTrue(store.snapshot.value.observedAtMs > 0L)

        backend.availability = CapabilityAvailability.UNAVAILABLE
        checkNotNull(eventListener).invoke()
        advanceUntilIdle()

        assertEquals(CapabilityAvailability.UNAVAILABLE, store.snapshot.value.availabilityOf(CapabilityId.DEVICE_STATE_READ))
        assertTrue(store.environmentReports.value.isNotEmpty())
    }

    @Test
    fun `burst of invalidations coalesces into a single trailing refresh`() = runTest {
        val backend = CountingBackend(MutableAvailabilityBackend(CapabilityAvailability.AVAILABLE))
        val registry = CapabilityRegistry.of(
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
        var now = 1_000L
        val store = CapabilityStateStore(
            registry = registry,
            environmentInspector = inspector(),
            scope = this,
            nowMs = { now += 1_000L; now },
            registerShizukuStateListener = { listener -> listener() },
            minRefreshIntervalMs = 10_000L
        )

        advanceUntilIdle()
        val afterInitial = backend.availabilityCalls
        assertEquals(1, afterInitial)

        repeat(5) { store.invalidate() }
        advanceUntilIdle()

        assertEquals(
            "5 invalidations inside one window must yield exactly one trailing refresh",
            afterInitial + 1,
            backend.availabilityCalls
        )
    }

    @Test
    fun `explicit refresh bypasses passive backoff after a grant change`() = runTest {
        val backend = MutableAvailabilityBackend(CapabilityAvailability.AVAILABLE)
        val registry = CapabilityRegistry.of(
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
        val store = CapabilityStateStore(
            registry = registry,
            environmentInspector = inspector(),
            scope = this,
            nowMs = { 1_000L },
            registerShizukuStateListener = { listener -> listener() },
            minRefreshIntervalMs = 30_000L
        )

        advanceUntilIdle()
        assertEquals(0L, currentTime)

        backend.availability = CapabilityAvailability.UNAVAILABLE
        store.refresh()
        advanceUntilIdle()

        assertEquals(
            "explicit permission refresh must not sleep through the passive backoff",
            0L,
            currentTime
        )
        assertEquals(
            CapabilityAvailability.UNAVAILABLE,
            store.snapshot.value.availabilityOf(CapabilityId.DEVICE_STATE_READ)
        )
    }

    @Test
    fun `explicit refresh wakes a worker sleeping in passive backoff`() = runTest {
        val backend = MutableAvailabilityBackend(CapabilityAvailability.AVAILABLE)
        val registry = CapabilityRegistry.of(
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
        val store = CapabilityStateStore(
            registry = registry,
            environmentInspector = inspector(),
            scope = this,
            nowMs = { 1_000L },
            registerShizukuStateListener = { listener -> listener() },
            minRefreshIntervalMs = 30_000L
        )
        advanceUntilIdle()
        assertEquals(0L, currentTime)

        backend.availability = CapabilityAvailability.UNAVAILABLE
        store.invalidate()
        runCurrent() // worker is now suspended in the passive 30s delay
        assertEquals(0L, currentTime)

        store.refresh()
        runCurrent()

        assertEquals(0L, currentTime)
        assertEquals(
            CapabilityAvailability.UNAVAILABLE,
            store.snapshot.value.availabilityOf(CapabilityId.DEVICE_STATE_READ)
        )
    }

    @Test
    fun `probe failure does not block a later capability refresh`() = runTest {
        val backend = FailOnceBackend()
        val registry = CapabilityRegistry.of(
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
        var eventListener: (() -> Unit)? = null
        var now = 1_000L
        val store = CapabilityStateStore(
            registry = registry,
            environmentInspector = inspector(),
            scope = this,
            nowMs = { now++ },
            registerShizukuStateListener = { listener ->
                eventListener = listener
                listener()
            },
            minRefreshIntervalMs = 0L
        )

        advanceUntilIdle()
        checkNotNull(eventListener).invoke()
        advanceUntilIdle()

        assertEquals(
            CapabilityAvailability.AVAILABLE,
            store.snapshot.value.availabilityOf(CapabilityId.DEVICE_STATE_READ)
        )
        assertEquals(2, backend.availabilityCalls)
    }

    private fun inspector() = CapabilityEnvironmentInspector(
        shizukuInstalled = { false },
        shizukuRunning = { false },
        shizukuGranted = { false },
        shizukuUserServiceBound = { false },
        suBinaryPresent = { false },
        rootAvailable = { false },
        deviceOwner = { false }
    )

    private class CountingBackend(
        private val delegate: MutableAvailabilityBackend
    ) : CapabilityBackend by delegate {
        var availabilityCalls = 0

        override suspend fun availability(request: CapabilityRequest): BackendAvailability {
            availabilityCalls++
            return delegate.availability(request)
        }
    }

    private class FailOnceBackend : CapabilityBackend {
        override val id: CapabilityBackendId = CapabilityBackendId.ANDROID_API
        override val supportedCapabilities: Set<CapabilityId> = setOf(CapabilityId.DEVICE_STATE_READ)
        var availabilityCalls = 0

        override suspend fun availability(request: CapabilityRequest): BackendAvailability {
            availabilityCalls++
            if (availabilityCalls == 1) error("transient capability probe failure")
            return BackendAvailability(
                backend = id,
                availability = CapabilityAvailability.AVAILABLE
            )
        }

        override suspend fun execute(request: CapabilityRequest): CapabilityResult =
            CapabilityResult.unsupported("Not used by state-store test")
    }

    private class MutableAvailabilityBackend(
        var availability: CapabilityAvailability
    ) : CapabilityBackend {
        override val id: CapabilityBackendId = CapabilityBackendId.ANDROID_API
        override val supportedCapabilities: Set<CapabilityId> = setOf(CapabilityId.DEVICE_STATE_READ)

        override suspend fun availability(request: CapabilityRequest): BackendAvailability = BackendAvailability(
            backend = id,
            availability = availability
        )

        override suspend fun execute(request: CapabilityRequest): CapabilityResult =
            CapabilityResult.unsupported("Not used by state-store test")
    }
}
