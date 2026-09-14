package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.capability.VerificationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test

class CapabilityCancellationTest {

    @Test
    fun `resolver preserves cancellation from live availability probe`() = runBlocking {
        val backend = CancellingBackend(cancelAvailability = true)
        val registry = registry(backend)

        expectCancellation {
            CapabilityResolver(registry).resolve(request(), deviceState())
        }
    }

    @Test
    fun `diagnostics preserves cancellation from live availability probe`() = runBlocking {
        val backend = CancellingBackend(cancelAvailability = true)
        val registry = registry(backend)

        expectCancellation {
            CapabilityDiagnostics(registry).report(request())
        }
    }

    @Test
    fun `execution service preserves cancellation from backend execution`() = runBlocking {
        val backend = CancellingBackend(cancelExecution = true)
        val registry = registry(backend)
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { deviceState() }
        )

        expectCancellation {
            service.execute(request())
        }
    }

    @Test
    fun `execution service preserves cancellation from post condition verification`() = runBlocking {
        val backend = CancellingBackend(cancelVerification = true)
        val registry = registry(backend)
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { deviceState() }
        )

        expectCancellation {
            service.execute(request().copy(verification = VerificationMode.REQUIRED))
        }
    }

    private suspend fun expectCancellation(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Cancellation is coroutine control flow and must escape capability wrappers.
        }
    }

    private fun registry(backend: CapabilityBackend): CapabilityRegistry = CapabilityRegistry.of(
        descriptors = listOf(
            CapabilityDescriptor(
                id = CapabilityId.PACKAGE_READ,
                displayName = "Read packages",
                description = "Reads package metadata",
                supportedBackends = listOf(CapabilityBackendId.PACKAGE_MANAGER)
            )
        ),
        backends = listOf(backend)
    )

    private fun request() = CapabilityRequest(capability = CapabilityId.PACKAGE_READ)

    private fun deviceState() = CapabilityDeviceState(
        capturedAt = 1_000L,
        wifiConnected = true,
        batteryPercent = 80,
        charging = true,
        screenInteractive = false
    )

    private class CancellingBackend(
        private val cancelAvailability: Boolean = false,
        private val cancelExecution: Boolean = false,
        private val cancelVerification: Boolean = false
    ) : CapabilityBackend {
        override val id = CapabilityBackendId.PACKAGE_MANAGER
        override val supportedCapabilities = setOf(CapabilityId.PACKAGE_READ)

        override suspend fun availability(request: CapabilityRequest): BackendAvailability {
            if (cancelAvailability) throw CancellationException("probe cancelled")
            return BackendAvailability(id, CapabilityAvailability.AVAILABLE)
        }

        override suspend fun execute(request: CapabilityRequest): CapabilityResult {
            if (cancelExecution) throw CancellationException("execution cancelled")
            return CapabilityResult(status = CapabilityStatus.SUCCESS, backend = id, message = "read")
        }

        override suspend fun verify(
            request: CapabilityRequest,
            result: CapabilityResult
        ): VerificationResult {
            if (cancelVerification) throw CancellationException("verification cancelled")
            return VerificationResult(attempted = true, verified = true, message = "verified")
        }
    }
}
