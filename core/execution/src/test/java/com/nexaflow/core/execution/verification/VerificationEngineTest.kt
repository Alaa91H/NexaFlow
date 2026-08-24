package com.nexaflow.core.execution.verification

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.core.execution.capability.CapabilityBackend
import com.nexaflow.core.execution.capability.CapabilityRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VerificationEngineTest {

    private val fakeBackendId = CapabilityBackendId.ANDROID_API
    private val fakeCapability = CapabilityId.DEVICE_STATE_READ

    private val request = CapabilityRequest(capability = fakeCapability)
    private val result = CapabilityResult.success(backend = fakeBackendId, message = "OK")

    @Test
    fun `verification succeeds on first attempt`() = runBlocking {
        val backend = object : CapabilityBackend {
            override val id = fakeBackendId
            override val supportedCapabilities = setOf(fakeCapability)
            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            override suspend fun execute(request: CapabilityRequest) = result
            override suspend fun verify(request: CapabilityRequest, result: CapabilityResult) = VerificationResult(true, true, "Verified")
        }
        val registry = CapabilityRegistry.of(emptyList(), listOf(backend))
        val engine = VerificationEngine(registry)

        val vr = engine.verify(request, result)
        assertTrue(vr.attempted)
        assertTrue(vr.verified)
    }

    @Test
    fun `verification retries and succeeds`() = runBlocking {
        var attempts = 0
        val backend = object : CapabilityBackend {
            override val id = fakeBackendId
            override val supportedCapabilities = setOf(fakeCapability)
            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            override suspend fun execute(request: CapabilityRequest) = result
            override suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult {
                attempts++
                return if (attempts == 2) VerificationResult(true, true, "Verified")
                else VerificationResult(true, false, "Not yet")
            }
        }
        val registry = CapabilityRegistry.of(emptyList(), listOf(backend))
        val engine = VerificationEngine(registry, maxRetries = 3, backoffMs = 10L)

        val vr = engine.verify(request, result)
        assertTrue(vr.attempted)
        assertTrue(vr.verified)
        assertEquals(2, attempts)
    }

    @Test
    fun `verification fails after max retries`() = runBlocking {
        var attempts = 0
        val backend = object : CapabilityBackend {
            override val id = fakeBackendId
            override val supportedCapabilities = setOf(fakeCapability)
            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            override suspend fun execute(request: CapabilityRequest) = result
            override suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult {
                attempts++
                return VerificationResult(true, false, "Failed")
            }
        }
        val registry = CapabilityRegistry.of(emptyList(), listOf(backend))
        val engine = VerificationEngine(registry, maxRetries = 2, backoffMs = 10L)

        val vr = engine.verify(request, result)
        assertTrue(vr.attempted)
        assertFalse(vr.verified)
        assertEquals(2, attempts) // Exhausted retries
    }

    @Test
    fun `returns unattempted if backend does not support verification`() = runBlocking {
        val backend = object : CapabilityBackend {
            override val id = fakeBackendId
            override val supportedCapabilities = setOf(fakeCapability)
            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            override suspend fun execute(request: CapabilityRequest) = result
            // Defaults to unattempted
        }
        val registry = CapabilityRegistry.of(emptyList(), listOf(backend))
        val engine = VerificationEngine(registry)

        val vr = engine.verify(request, result)
        assertFalse(vr.attempted)
        assertFalse(vr.verified)
    }
}
