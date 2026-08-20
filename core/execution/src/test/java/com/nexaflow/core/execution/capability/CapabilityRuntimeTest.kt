package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.NetworkRequirement
import com.nexaflow.domain.capability.PolicyBlockReason
import com.nexaflow.domain.capability.PrivilegeLevel
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.capability.VerificationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRuntimeTest {

    private class FakeBackend(
        override val id: CapabilityBackendId,
        override val supportedCapabilities: Set<CapabilityId>,
        private val liveAvailability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
        private val result: CapabilityResult = CapabilityResult(
            status = CapabilityStatus.SUCCESS,
            message = "completed"
        ),
        private val verification: VerificationResult = VerificationResult(true, true, "verified")
    ) : CapabilityBackend {
        var availabilityCalls = 0
        var executionCalls = 0

        override suspend fun availability(request: CapabilityRequest): BackendAvailability {
            availabilityCalls++
            return BackendAvailability(id, liveAvailability, reason = "fake-$id")
        }

        override suspend fun execute(request: CapabilityRequest): CapabilityResult {
            executionCalls++
            return result
        }

        override suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult = verification
    }

    private fun descriptor(vararg backends: CapabilityBackendId) = CapabilityDescriptor(
        id = CapabilityId.PACKAGE_FORCE_STOP,
        displayName = "Force stop",
        description = "Stops a package",
        minimumPrivilege = PrivilegeLevel.SYSTEM,
        risk = CapabilityRiskLevel.HIGH,
        supportedBackends = backends.toList()
    )

    private fun state(
        wifi: Boolean? = true,
        battery: Int? = 80,
        charging: Boolean? = true,
        screenInteractive: Boolean? = false
    ) = CapabilityDeviceState(
        capturedAt = 1_000L,
        wifiConnected = wifi,
        batteryPercent = battery,
        charging = charging,
        screenInteractive = screenInteractive
    )

    @Test
    fun `resolver prefers public Android API over available privileged backend`() = runBlocking {
        val android = FakeBackend(
            CapabilityBackendId.ANDROID_API,
            setOf(CapabilityId.PACKAGE_FORCE_STOP)
        )
        val root = FakeBackend(
            CapabilityBackendId.ROOT,
            setOf(CapabilityId.PACKAGE_FORCE_STOP)
        )
        val resolver = CapabilityResolver(
            CapabilityRegistry.of(
                descriptors = listOf(descriptor(CapabilityBackendId.ANDROID_API, CapabilityBackendId.ROOT)),
                backends = listOf(android, root)
            )
        )

        val resolution = resolver.resolve(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_FORCE_STOP,
                policy = ExecutionPolicy(allowPrivilegedBackends = true)
            ),
            state()
        )

        assertEquals(CapabilityBackendId.ANDROID_API, resolution.selectedBackend?.id)
        assertEquals(1, android.availabilityCalls)
        assertEquals(1, root.availabilityCalls)
    }

    @Test
    fun `wifi-only policy blocks before invoking any backend`() = runBlocking {
        val backend = FakeBackend(
            CapabilityBackendId.ANDROID_API,
            setOf(CapabilityId.PACKAGE_READ)
        )
        val registry = CapabilityRegistry.of(
            descriptors = listOf(
                CapabilityDescriptor(
                    id = CapabilityId.PACKAGE_READ,
                    displayName = "Read packages",
                    description = "Reads package metadata",
                    supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
                )
            ),
            backends = listOf(backend)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { state(wifi = false) },
            nowMs = { 2_000L }
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_READ,
                policy = ExecutionPolicy(network = NetworkRequirement.WIFI_ONLY)
            )
        )

        assertEquals(CapabilityStatus.FAILED, result.status)
        assertEquals(CapabilityErrorCode.POLICY_NOT_SATISFIED, result.errorCode)
        assertEquals(0, backend.availabilityCalls)
        assertEquals(0, backend.executionCalls)
    }

    @Test
    fun `root backend is blocked until policy explicitly authorizes privileged execution`() = runBlocking {
        val root = FakeBackend(
            CapabilityBackendId.ROOT,
            setOf(CapabilityId.PACKAGE_CLEAR_DATA)
        )
        val resolver = CapabilityResolver(
            CapabilityRegistry.of(
                descriptors = listOf(
                    CapabilityDescriptor(
                        id = CapabilityId.PACKAGE_CLEAR_DATA,
                        displayName = "Clear app data",
                        description = "Clears app data",
                        minimumPrivilege = PrivilegeLevel.ROOT,
                        risk = CapabilityRiskLevel.DESTRUCTIVE,
                        supportedBackends = listOf(CapabilityBackendId.ROOT)
                    )
                ),
                backends = listOf(root)
            )
        )

        val resolution = resolver.resolve(
            CapabilityRequest(capability = CapabilityId.PACKAGE_CLEAR_DATA),
            state()
        )

        assertFalse(resolution.isResolved)
        assertTrue(PolicyBlockReason.PRIVILEGE_NOT_ALLOWED in resolution.policy.reasons)
        assertEquals(CapabilityErrorCode.POLICY_NOT_SATISFIED, resolution.failure?.errorCode)
        assertEquals(0, root.availabilityCalls)
    }

    @Test
    fun `execution service rejects unknown parameters before backend selection`() = runBlocking {
        val backend = FakeBackend(CapabilityBackendId.PACKAGE_MANAGER, setOf(CapabilityId.PACKAGE_READ))
        val registry = CapabilityRegistry.of(
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
        val result = CapabilityExecutionService(CapabilityResolver(registry), { state() }).execute(
            CapabilityRequest(CapabilityId.PACKAGE_READ, parameters = mapOf("command" to "rm -rf /"))
        )

        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, result.errorCode)
        assertEquals(0, backend.availabilityCalls)
        assertEquals(0, backend.executionCalls)
    }

    @Test
    fun `plugin capability accepts only an opaque persisted instance reference`() = runBlocking {
        val backend = FakeBackend(CapabilityBackendId.PLUGIN, setOf(CapabilityId.PLUGIN_ACTION))
        val registry = CapabilityRegistry.of(
            descriptors = listOf(
                CapabilityDescriptor(
                    id = CapabilityId.PLUGIN_ACTION,
                    displayName = "External plug-in action",
                    description = "Invokes a persisted plug-in instance",
                    supportedBackends = listOf(CapabilityBackendId.PLUGIN),
                    parameters = listOf(
                        CapabilityParameterSpec(
                            name = "pluginInstance",
                            type = CapabilityParameterType.OPAQUE_REFERENCE,
                            required = true,
                            maximumLength = 192
                        )
                    )
                )
            ),
            backends = listOf(backend)
        )
        val service = CapabilityExecutionService(CapabilityResolver(registry), { state() })

        val accepted = service.execute(
            CapabilityRequest(CapabilityId.PLUGIN_ACTION, parameters = mapOf("pluginInstance" to "locale:com.example.plugin:abc-123"))
        )
        val rejected = service.execute(
            CapabilityRequest(CapabilityId.PLUGIN_ACTION, parameters = mapOf("pluginInstance" to "{\\\"bundle\\\":\\\"arbitrary\\\"}"))
        )

        assertEquals(CapabilityStatus.SUCCESS, accepted.status)
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, rejected.errorCode)
        assertEquals(1, backend.executionCalls)
    }

    @Test
    fun `required verification turns unverified success into structured failure`() = runBlocking {
        val backend = FakeBackend(
            CapabilityBackendId.PACKAGE_MANAGER,
            setOf(CapabilityId.PACKAGE_READ),
            verification = VerificationResult(true, false, "Package state could not be observed")
        )
        val registry = CapabilityRegistry.of(
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
        val result = CapabilityExecutionService(CapabilityResolver(registry), { state() }).execute(
            CapabilityRequest(CapabilityId.PACKAGE_READ, verification = VerificationMode.REQUIRED)
        )

        assertEquals(CapabilityStatus.FAILED, result.status)
        assertEquals(CapabilityErrorCode.VERIFICATION_FAILED, result.errorCode)
        assertEquals(1, backend.executionCalls)
    }

    @Test
    fun `execution service retries only configured structured errors`() = runBlocking {
        val backend = object : CapabilityBackend {
            override val id = CapabilityBackendId.PACKAGE_MANAGER
            override val supportedCapabilities = setOf(CapabilityId.PACKAGE_READ)
            var attempts = 0

            override suspend fun availability(request: CapabilityRequest) =
                BackendAvailability(id, CapabilityAvailability.AVAILABLE)

            override suspend fun execute(request: CapabilityRequest): CapabilityResult {
                attempts++
                return if (attempts == 1) {
                    CapabilityResult.failed(CapabilityErrorCode.NETWORK_ERROR, "temporary", id)
                } else {
                    CapabilityResult(CapabilityStatus.SUCCESS, id, message = "recovered")
                }
            }

            override suspend fun verify(request: CapabilityRequest, result: CapabilityResult) =
                VerificationResult(true, true, "verified")
        }
        val registry = CapabilityRegistry.of(
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
        val result = CapabilityExecutionService(CapabilityResolver(registry), { state() }).execute(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_READ,
                policy = ExecutionPolicy(
                    retry = com.nexaflow.domain.capability.CapabilityRetryPolicy(
                        maxAttempts = 2,
                        baseDelayMs = 0,
                        capDelayMs = 0,
                        retryableErrors = listOf(CapabilityErrorCode.NETWORK_ERROR)
                    )
                )
            )
        )

        assertEquals(CapabilityStatus.SUCCESS, result.status)
        assertEquals(2, backend.attempts)
        assertEquals("2", result.metadata["attempts"])
    }

    @Test
    fun `execution service attaches selected backend and measured duration to successful result`() = runBlocking {
        val backend = FakeBackend(
            id = CapabilityBackendId.PACKAGE_MANAGER,
            supportedCapabilities = setOf(CapabilityId.PACKAGE_READ),
            result = CapabilityResult(status = CapabilityStatus.SUCCESS, message = "read")
        )
        val registry = CapabilityRegistry.of(
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
        var now = 100L
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { state() },
            nowMs = { now.also { now += 25L } }
        )

        val result = service.execute(CapabilityRequest(capability = CapabilityId.PACKAGE_READ))

        assertEquals(CapabilityStatus.SUCCESS, result.status)
        assertEquals(CapabilityBackendId.PACKAGE_MANAGER, result.backend)
        assertTrue(result.durationMs > 0L)
        assertEquals(1, backend.executionCalls)
    }
}
