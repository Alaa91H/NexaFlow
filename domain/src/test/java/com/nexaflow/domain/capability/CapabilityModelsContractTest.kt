package com.nexaflow.domain.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CapabilityModelsContractTest {

    @Test
    fun `device state accepts unknowns and validates battery bounds`() {
        val state = CapabilityDeviceState(
            capturedAt = 10L,
            wifiConnected = true,
            batteryPercent = 80,
            charging = false,
            screenInteractive = true,
            thermalState = ThermalState.MODERATE
        )

        assertEquals(80, state.batteryPercent)
        assertEquals(ThermalState.MODERATE, state.thermalState)

        val unknown = CapabilityDeviceState(capturedAt = 11L)
        assertNull(unknown.batteryPercent)
        assertEquals(ThermalState.UNKNOWN, unknown.thermalState)

        assertThrows(IllegalArgumentException::class.java) {
            CapabilityDeviceState(capturedAt = 0L, batteryPercent = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityDeviceState(capturedAt = 0L, batteryPercent = 101)
        }
    }

    @Test
    fun `parameter schema validates names lengths and integer ranges`() {
        val spec = CapabilityParameterSpec(
            name = "packageName",
            type = CapabilityParameterType.PACKAGE_NAME,
            required = true,
            maximumLength = 256,
            minimumInteger = 1,
            maximumInteger = 10,
            allowedValues = listOf("one", "two")
        )

        assertTrue(spec.required)
        assertEquals(256, spec.maximumLength)
        assertEquals(listOf("one", "two"), spec.allowedValues)

        assertThrows(IllegalArgumentException::class.java) {
            CapabilityParameterSpec("Bad-name", CapabilityParameterType.STRING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityParameterSpec("value", CapabilityParameterType.STRING, maximumLength = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityParameterSpec("value", CapabilityParameterType.STRING, maximumLength = 4_097)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityParameterSpec(
                name = "value",
                type = CapabilityParameterType.INTEGER,
                minimumInteger = 5,
                maximumInteger = 4
            )
        }
    }

    @Test
    fun `descriptor requires unique parameter names and retains recovery metadata`() {
        val parameter = CapabilityParameterSpec(
            name = "enabled",
            type = CapabilityParameterType.BOOLEAN,
            required = true
        )
        val descriptor = CapabilityDescriptor(
            id = CapabilityId.SYSTEM_SETTING_WRITE,
            displayName = "Setting",
            description = "Write one setting",
            supportedBackends = listOf(CapabilityBackendId.SHIZUKU, CapabilityBackendId.ROOT),
            idempotency = CapabilityIdempotency.IDEMPOTENT,
            retrySafety = CapabilityRetrySafety.SAFE,
            verificationMode = VerificationMode.REQUIRED,
            compensation = CapabilityCompensationSupport.SUPPORTED,
            sideEffectLevel = CapabilitySideEffectLevel.REVERSIBLE,
            parameters = listOf(parameter)
        )

        assertEquals(CapabilityIdempotency.IDEMPOTENT, descriptor.idempotency)
        assertEquals(CapabilityRetrySafety.SAFE, descriptor.retrySafety)
        assertEquals(listOf(parameter), descriptor.parameters)

        assertThrows(IllegalArgumentException::class.java) {
            descriptor.copy(parameters = listOf(parameter, parameter))
        }
    }

    @Test
    fun `execution policy helpers authorize only their requested privileged routing`() {
        val adaptive = ExecutionPolicy.adaptivePrivileged(
            preferredBackends = listOf(CapabilityBackendId.SHIZUKU, CapabilityBackendId.ROOT)
        )
        assertTrue(adaptive.allowPrivilegedBackends)
        assertTrue(adaptive.allowedBackends.isEmpty())
        assertEquals(
            listOf(CapabilityBackendId.SHIZUKU, CapabilityBackendId.ROOT),
            adaptive.preferredBackends
        )

        val pinned = ExecutionPolicy.pinnedPrivileged(CapabilityBackendId.ROOT)
        assertTrue(pinned.allowPrivilegedBackends)
        assertEquals(listOf(CapabilityBackendId.ROOT), pinned.allowedBackends)
        assertEquals(listOf(CapabilityBackendId.ROOT), pinned.preferredBackends)

        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPolicy(minimumBatteryPercent = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPolicy(minimumBatteryPercent = 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPolicy(timeoutMs = 999L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPolicy(timeoutMs = 300_001L)
        }
    }

    @Test
    fun `retry policy rejects impossible attempt and delay bounds`() {
        val policy = CapabilityRetryPolicy(
            maxAttempts = 3,
            baseDelayMs = 100L,
            capDelayMs = 1_000L,
            retryableErrors = listOf(CapabilityErrorCode.TIMEOUT)
        )
        assertEquals(3, policy.maxAttempts)
        assertEquals(listOf(CapabilityErrorCode.TIMEOUT), policy.retryableErrors)

        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRetryPolicy(maxAttempts = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRetryPolicy(baseDelayMs = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRetryPolicy(baseDelayMs = 2_000L, capDelayMs = 1_000L)
        }
    }

    @Test
    fun `capability result factories preserve machine readable failure contracts`() {
        val success = CapabilityResult(
            status = CapabilityStatus.SUCCESS,
            backend = CapabilityBackendId.ANDROID_API,
            message = "done",
            verification = VerificationResult(
                attempted = true,
                verified = true,
                message = "read back"
            )
        )
        assertTrue(success.isSuccess)

        val unsupported = CapabilityResult.unsupported("not supported")
        assertFalse(unsupported.isSuccess)
        assertEquals(CapabilityStatus.UNSUPPORTED, unsupported.status)
        assertEquals(CapabilityErrorCode.UNSUPPORTED_CAPABILITY, unsupported.errorCode)

        val customUnsupported = CapabilityResult.unsupported(
            message = "backend missing",
            errorCode = CapabilityErrorCode.BACKEND_UNAVAILABLE
        )
        assertEquals(CapabilityErrorCode.BACKEND_UNAVAILABLE, customUnsupported.errorCode)

        val failed = CapabilityResult.failed(
            errorCode = CapabilityErrorCode.PERMISSION_DENIED,
            message = "denied",
            backend = CapabilityBackendId.SHIZUKU,
            durationMs = 42L
        )
        assertEquals(CapabilityStatus.FAILED, failed.status)
        assertEquals(CapabilityBackendId.SHIZUKU, failed.backend)
        assertEquals(42L, failed.durationMs)
        assertFalse(failed.isSuccess)
    }

    @Test
    fun `availability projections retain backend evidence`() {
        val backend = BackendAvailability(
            backend = CapabilityBackendId.ROOT,
            availability = CapabilityAvailability.PERMISSION_REQUIRED,
            reason = "grant required",
            grantedPrivileges = listOf(PrivilegeLevel.NONE)
        )
        val report = CapabilityAvailabilityReport(
            capability = CapabilityId.PACKAGE_FORCE_STOP,
            availability = CapabilityAvailability.PARTIAL,
            backends = listOf(backend),
            reason = "one backend needs permission"
        )

        assertEquals(CapabilityBackendId.ROOT, report.backends.single().backend)
        assertEquals(CapabilityAvailability.PARTIAL, report.availability)
        assertEquals("grant required", report.backends.single().reason)
    }
}
