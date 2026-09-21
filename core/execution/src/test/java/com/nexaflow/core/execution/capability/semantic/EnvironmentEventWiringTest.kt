package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B wiring contract: the real Shizuku lifecycle listener must publish
 * the targeted [EnvironmentEvent.ShizukuStateChanged] event, and the
 * invalidator must erase ONLY Shizuku-backed evidence — Root evidence and
 * health must survive a Shizuku binder death (targeted invalidation, never a
 * full rescan).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentEventWiringTest {

    @Test
    fun bridgeListenerPublishesTargetedShizukuEvent() = runTest {
        val bus = EnvironmentEventBus()
        var registeredListener: (() -> Unit)? = null
        val wiring = EnvironmentEventWiring(bus) { listener ->
            registeredListener = listener
            listener()
        }
        val received = mutableListOf<EnvironmentEvent>()
        val collector = launch { bus.events.collect { received += it } }
        // Let the collector subscribe before any publish: SharedFlow has no replay.
        advanceUntilIdle()

        wiring.wireShizukuLifecycle()
        // The registration seam invokes the listener immediately (sticky state).
        advanceUntilIdle()
        assertTrue(received.isNotEmpty())
        assertTrue(received.all { it is EnvironmentEvent.ShizukuStateChanged })

        // Every subsequent real transition publishes again.
        registeredListener?.invoke()
        advanceUntilIdle()
        assertEquals(2, received.size)
        collector.cancel()
    }

    @Test
    fun wiringIsIdempotent() = runTest {
        val bus = EnvironmentEventBus()
        var registrations = 0
        val wiring = EnvironmentEventWiring(bus) {
            registrations += 1
            it()
        }
        wiring.wireShizukuLifecycle()
        wiring.wireShizukuLifecycle()
        wiring.wireShizukuLifecycle()
        assertEquals("Duplicate wiring must not re-register listeners", 1, registrations)
    }

    @Test
    fun shizukuEventInvalidatesOnlyShizukuEvidence() = runTest {
        val evidenceStore = CapabilityEvidenceStore()
        val healthTracker = StrategyHealthTracker()
        val deviceKey = testDeviceKey()

        evidenceStore.recordSuccess(SemanticOperationId.WIFI_SET_STATE, StrategyId.SHIZUKU_USER_SERVICE, deviceKey, verified = true, latencyMs = 10)
        evidenceStore.recordSuccess(SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, deviceKey, verified = true, latencyMs = 10)

        EnvironmentInvalidator(evidenceStore, healthTracker)
            .apply(EnvironmentEvent.ShizukuStateChanged, deviceKey)

        assertEquals(
            0L,
            evidenceStore.evidenceFor(SemanticOperationId.WIFI_SET_STATE, StrategyId.SHIZUKU_USER_SERVICE, deviceKey).score(System.currentTimeMillis())
        )
        assertTrue(
            "Root evidence must survive a Shizuku binder death",
            evidenceStore.evidenceFor(SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, deviceKey).verifiedSuccesses > 0
        )
    }

    @Test
    fun rootGrantEventInvalidatesOnlyRootEvidence() = runTest {
        val evidenceStore = CapabilityEvidenceStore()
        val healthTracker = StrategyHealthTracker()
        val deviceKey = testDeviceKey()

        evidenceStore.recordSuccess(SemanticOperationId.WIFI_SET_STATE, StrategyId.SHIZUKU_USER_SERVICE, deviceKey, verified = true, latencyMs = 10)
        evidenceStore.recordSuccess(SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, deviceKey, verified = true, latencyMs = 10)

        EnvironmentInvalidator(evidenceStore, healthTracker)
            .apply(EnvironmentEvent.RootGrantChanged, deviceKey)

        assertTrue(
            "Shizuku evidence must survive a root-grant event (targeted scope)",
            evidenceStore.evidenceFor(SemanticOperationId.WIFI_SET_STATE, StrategyId.SHIZUKU_USER_SERVICE, deviceKey).verifiedSuccesses == 1L
        )
        assertTrue(
            evidenceStore.evidenceFor(SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, deviceKey).verifiedSuccesses == 0L
        )
        // Health was untouched by the event (targeted scope resets only the
        // evidence of the affected strategy).
        assertEquals(
            StrategyHealthTracker.State.UNKNOWN,
            healthTracker.healthFor(StrategyId.SHIZUKU_USER_SERVICE, deviceKey).state
        )
    }

    /** JVM-safe device key: Build.MANUFACTURER is null on the host. */
    private fun testDeviceKey(): String = TEST_DEVICE_KEY

    private companion object {
        /** JVM-safe device key: Build.MANUFACTURER is null on the host. */
        const val TEST_DEVICE_KEY = "test/oem/model/34/patch"
    }
}
