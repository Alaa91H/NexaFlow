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
import com.nexaflow.domain.capability.PrivilegeGrantState
import com.nexaflow.domain.capability.PrivilegeObservation
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.core.execution.verification.VerificationEngine
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
    fun `declared Android permission blocks capability before backend probing`() = runBlocking {
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
                    requiredPermissions = listOf("android.permission.CAMERA"),
                    supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
                )
            ),
            backends = listOf(backend)
        )
        val snapshot = PrivilegeSnapshot(
            observations = listOf(
                PrivilegeObservation(
                    surface = PrivilegeSurface.RUNTIME_PERMISSION,
                    key = "android.permission.CAMERA",
                    state = PrivilegeGrantState.NOT_GRANTED,
                    detailCode = "ANDROID_RUNTIME_NOT_GRANTED"
                )
            ),
            observedAtMs = 2_000L
        )
        val resolver = CapabilityResolver(
            registry = registry,
            privilegeSnapshotProvider = { snapshot }
        )

        val resolution = resolver.resolve(
            CapabilityRequest(capability = CapabilityId.PACKAGE_READ),
            state()
        )

        assertFalse(resolution.isResolved)
        assertEquals(CapabilityErrorCode.PERMISSION_DENIED, resolution.failure?.errorCode)
        assertTrue(
            resolution.failure?.metadata
                ?.get("missingPermissions")
                ?.contains("android.permission.CAMERA") == true
        )
        assertEquals(0, backend.availabilityCalls)
    }

    @Test
    fun `never-observed privilege snapshot never creates a false startup denial`() = runBlocking {
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
                    requiredPermissions = listOf("android.permission.CAMERA"),
                    supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
                )
            ),
            backends = listOf(backend)
        )
        val resolver = CapabilityResolver(
            registry = registry,
            privilegeSnapshotProvider = { PrivilegeSnapshot() }
        )

        val resolution = resolver.resolve(
            CapabilityRequest(capability = CapabilityId.PACKAGE_READ),
            state()
        )

        assertTrue(resolution.isResolved)
        assertEquals(1, backend.availabilityCalls)
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
                    supportedBackends = listOf(CapabilityBackendId.PACKAGE_MANAGER),
                    retrySafety = com.nexaflow.domain.capability.CapabilityRetrySafety.SAFE
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

    @Test
    fun `execution service with verification engine converts unverified REQUIRED write into VERIFICATION_FAILED`() = runBlocking {
        val backend = FakeBackend(
            id = CapabilityBackendId.ANDROID_API,
            supportedCapabilities = setOf(CapabilityId.PACKAGE_READ),
            result = CapabilityResult(status = CapabilityStatus.SUCCESS, message = "written"),
            verification = VerificationResult(attempted = true, verified = false, message = "Value mismatch")
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
        val engine = VerificationEngine(registry, defaultMaxRetries = 2, defaultBackoffMs = 5L)
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { state() },
            verificationEngine = engine
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_READ,
                verification = VerificationMode.REQUIRED
            )
        )

        assertEquals(CapabilityStatus.FAILED, result.status)
        assertEquals(CapabilityErrorCode.VERIFICATION_FAILED, result.errorCode)
        assertEquals("Value mismatch", result.message)
        assertFalse(result.verification?.verified ?: true)
    }

    @Test
    fun `execution service with verification engine succeeds when verification is verified`() = runBlocking {
        val backend = FakeBackend(
            id = CapabilityBackendId.ANDROID_API,
            supportedCapabilities = setOf(CapabilityId.PACKAGE_READ),
            result = CapabilityResult(status = CapabilityStatus.SUCCESS, message = "written"),
            verification = VerificationResult(attempted = true, verified = true, message = "Verified ok")
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
        val engine = VerificationEngine(registry, defaultMaxRetries = 2, defaultBackoffMs = 5L)
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { state() },
            verificationEngine = engine
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_READ,
                verification = VerificationMode.REQUIRED
            )
        )

        assertEquals(CapabilityStatus.SUCCESS, result.status)
        assertTrue(result.verification?.verified ?: false)
        assertEquals("Verified ok", result.verification?.message)
    }

    @Test
    fun `execution service seamlessly fails over to candidate backend when primary backend suffers transport failure`() = runBlocking {
        val failingShizuku = FakeBackend(
            id = CapabilityBackendId.SHIZUKU,
            supportedCapabilities = setOf(CapabilityId.PACKAGE_FORCE_STOP),
            result = CapabilityResult.failed(
                errorCode = CapabilityErrorCode.SHIZUKU_UNAVAILABLE,
                message = "Shizuku server is not connected",
                backend = CapabilityBackendId.SHIZUKU
            )
        )
        val healthyRoot = FakeBackend(
            id = CapabilityBackendId.ROOT,
            supportedCapabilities = setOf(CapabilityId.PACKAGE_FORCE_STOP),
            result = CapabilityResult(
                status = CapabilityStatus.SUCCESS,
                message = "Force stopped via root",
                backend = CapabilityBackendId.ROOT
            )
        )
        val registry = CapabilityRegistry.of(
            descriptors = listOf(
                CapabilityDescriptor(
                    id = CapabilityId.PACKAGE_FORCE_STOP,
                    displayName = "Force stop package",
                    description = "Terminates package processes",
                    supportedBackends = listOf(CapabilityBackendId.SHIZUKU, CapabilityBackendId.ROOT),
                    parameters = listOf(
                        CapabilityParameterSpec(
                            name = "packageName",
                            type = CapabilityParameterType.PACKAGE_NAME,
                            required = true
                        )
                    )
                )
            ),
            backends = listOf(failingShizuku, healthyRoot)
        )
        val service = CapabilityExecutionService(
            resolver = CapabilityResolver(registry),
            deviceStateProvider = { state() }
        )

        val result = service.execute(
            CapabilityRequest(
                capability = CapabilityId.PACKAGE_FORCE_STOP,
                parameters = mapOf("packageName" to "com.example.app"),
                policy = ExecutionPolicy(allowPrivilegedBackends = true)
            )
        )

        assertEquals(CapabilityStatus.SUCCESS, result.status)
        assertEquals(CapabilityBackendId.ROOT, result.backend)
        assertEquals("Force stopped via root", result.message)
        assertEquals(1, failingShizuku.executionCalls)
        assertEquals(1, healthyRoot.executionCalls)
    }
}
