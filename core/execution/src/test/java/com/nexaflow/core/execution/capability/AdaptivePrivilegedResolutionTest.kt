package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.ExecutionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePrivilegedResolutionTest {

    private val state = CapabilityDeviceState(
        capturedAt = 1_000L,
        wifiConnected = true,
        batteryPercent = 80,
        charging = true,
        screenInteractive = false
    )

    @Test
    fun adaptivePolicyPrefersShizukuWhenBothBackendsAreHealthy() = runBlocking {
        val resolver = resolver(shizukuHealthy = true, rootHealthy = true)

        val resolution = resolver.resolve(adaptiveRequest(), state)

        assertEquals(CapabilityBackendId.SHIZUKU, resolution.selectedBackend?.id)
    }

    @Test
    fun adaptivePolicyFallsBackToRootWhenShizukuIsUnavailable() = runBlocking {
        val resolver = resolver(shizukuHealthy = false, rootHealthy = true)

        val resolution = resolver.resolve(adaptiveRequest(), state)

        assertEquals(CapabilityBackendId.ROOT, resolution.selectedBackend?.id)
    }

    @Test
    fun callerCanExplicitlyPreferRootWithoutDisablingAdaptiveOptIn() = runBlocking {
        val resolver = resolver(shizukuHealthy = true, rootHealthy = true)
        val request = adaptiveRequest().copy(
            policy = ExecutionPolicy(
                allowPrivilegedBackends = true,
                preferredBackends = listOf(CapabilityBackendId.ROOT)
            )
        )

        val resolution = resolver.resolve(request, state)

        assertEquals(CapabilityBackendId.ROOT, resolution.selectedBackend?.id)
    }

    @Test
    fun allowedBackendsCanStillPinExecutionToShizuku() = runBlocking {
        val resolver = resolver(shizukuHealthy = true, rootHealthy = true)
        val request = adaptiveRequest().copy(
            policy = ExecutionPolicy(
                allowPrivilegedBackends = true,
                allowedBackends = listOf(CapabilityBackendId.SHIZUKU)
            )
        )

        val resolution = resolver.resolve(request, state)

        assertEquals(CapabilityBackendId.SHIZUKU, resolution.selectedBackend?.id)
    }

    private fun adaptiveRequest() = CapabilityRequest(
        capability = CapabilityId.PACKAGE_FORCE_STOP,
        parameters = mapOf("packageName" to "com.example.app"),
        policy = ExecutionPolicy(allowPrivilegedBackends = true)
    )

    private fun resolver(shizukuHealthy: Boolean, rootHealthy: Boolean): CapabilityResolver {
        val shizuku = ShizukuCapabilityBackend(
            running = { shizukuHealthy },
            granted = { shizukuHealthy },
            userServiceBound = { shizukuHealthy },
            executeOperation = ::successfulOperation
        )
        val root = RootCapabilityBackend(
            rootAvailable = { rootHealthy },
            executeOperation = ::successfulOperation
        )
        return CapabilityResolver(
            CapabilityRegistry.of(
                descriptors = PrivilegedCapabilityCatalog.descriptors(),
                backends = listOf(shizuku, root)
            )
        )
    }

    private fun successfulOperation(operation: PrivilegedOperation): SystemControlResult =
        SystemControlResult.ok(operation.wireId.wireValue)
}
