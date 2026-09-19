package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.ExecutionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedCapabilityBackendsTest {

    @Test
    fun shizukuIsAvailableAfterExplicitPrivilegedOptInWithoutPinningAChannel() = runBlocking {
        val backend = ShizukuCapabilityBackend(
            running = { true },
            granted = { true },
            userServiceBound = { true }
        )

        val availability = backend.availability(forceStopRequest())

        assertEquals(CapabilityAvailability.AVAILABLE, availability.availability)
    }

    @Test
    fun privilegedBackendStillRejectsRequestsWithoutExplicitOptIn() = runBlocking {
        val backend = ShizukuCapabilityBackend(
            running = { true },
            granted = { true },
            userServiceBound = { true }
        )
        val request = CapabilityRequest(
            capability = CapabilityId.PACKAGE_FORCE_STOP,
            parameters = mapOf("packageName" to "com.example.app")
        )

        val availability = backend.availability(request)
        val result = backend.execute(request)

        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED, availability.availability)
        assertEquals(CapabilityErrorCode.POLICY_NOT_SATISFIED, result.errorCode)
    }

    @Test
    fun adaptiveShizukuMapsPackageCapabilityToTypedOperation() = runBlocking {
        var executed: PrivilegedOperation? = null
        val backend = ShizukuCapabilityBackend(
            running = { true },
            granted = { true },
            userServiceBound = { true },
            executeOperation = { operation ->
                executed = operation
                SystemControlResult.ok("stopped")
            }
        )

        val result = backend.execute(forceStopRequest())

        assertTrue(result.isSuccess)
        assertEquals(CapabilityBackendId.SHIZUKU, result.backend)
        assertEquals("package.force_stop", result.metadata["operation"])
        assertEquals(PrivilegedOperation.ForceStopPackage("com.example.app"), executed)
    }

    @Test
    fun explicitRootSelectionStillWorks() = runBlocking {
        var executed: PrivilegedOperation? = null
        val backend = RootCapabilityBackend(
            rootAvailable = { true },
            executeOperation = { operation ->
                executed = operation
                SystemControlResult.ok("stopped")
            }
        )

        val result = backend.execute(forceStopRequest(CapabilityBackendId.ROOT))

        assertTrue(result.isSuccess)
        assertEquals(CapabilityBackendId.ROOT, result.backend)
        assertEquals(PrivilegedOperation.ForceStopPackage("com.example.app"), executed)
    }

    @Test
    fun unavailableRootAndAdbReturnStructuredErrors() = runBlocking {
        val root = RootCapabilityBackend(rootAvailable = { false })
        val adb = AdbCapabilityBackend()
        val request = forceStopRequest(CapabilityBackendId.ROOT)

        assertEquals(CapabilityErrorCode.ROOT_UNAVAILABLE, root.execute(request).errorCode)
        assertEquals(CapabilityAvailability.UNAVAILABLE, adb.availability(request).availability)
        assertEquals(CapabilityErrorCode.ADB_UNAVAILABLE, adb.execute(request).errorCode)
    }

    @Test
    fun shizukuProbeFailureFailsClosedWithoutExecuting() = runBlocking {
        var executed = false
        val backend = ShizukuCapabilityBackend(
            running = { throw IllegalStateException("binder disappeared") },
            granted = { true },
            userServiceBound = { true },
            executeOperation = {
                executed = true
                SystemControlResult.ok("unexpected")
            }
        )

        val availability = backend.availability(forceStopRequest())
        val result = backend.execute(forceStopRequest())

        assertEquals(CapabilityAvailability.UNAVAILABLE, availability.availability)
        assertEquals(CapabilityErrorCode.SHIZUKU_UNAVAILABLE, result.errorCode)
        assertFalse(executed)
    }

    @Test
    fun rootProbeFailureFailsClosedWithoutExecuting() = runBlocking {
        var executed = false
        val backend = RootCapabilityBackend(
            rootAvailable = { throw IllegalStateException("su probe failed") },
            executeOperation = {
                executed = true
                SystemControlResult.ok("unexpected")
            }
        )

        val availability = backend.availability(forceStopRequest(CapabilityBackendId.ROOT))
        val result = backend.execute(forceStopRequest(CapabilityBackendId.ROOT))

        assertEquals(CapabilityAvailability.UNAVAILABLE, availability.availability)
        assertEquals(CapabilityErrorCode.ROOT_UNAVAILABLE, result.errorCode)
        assertFalse(executed)
    }

    @Test
    fun unallowlistedSettingCannotBecomeAnOperation() = runBlocking {
        val backend = RootCapabilityBackend(rootAvailable = { true })
        val request = CapabilityRequest(
            capability = CapabilityId.SYSTEM_SETTING_WRITE,
            parameters = mapOf(
                "namespace" to "GLOBAL",
                "key" to "not_allowlisted",
                "value" to "1"
            ),
            policy = explicitPolicy(CapabilityBackendId.ROOT)
        )

        val result = backend.execute(request)

        assertFalse(result.isSuccess)
        assertEquals(CapabilityErrorCode.INVALID_CONFIGURATION, result.errorCode)
    }

    @Test
    fun selectedShizukuMapsClearDataToTypedOperation() = runBlocking {
        var executed: PrivilegedOperation? = null
        val backend = ShizukuCapabilityBackend(
            running = { true },
            granted = { true },
            userServiceBound = { true },
            executeOperation = { operation ->
                executed = operation
                SystemControlResult.ok("cleared")
            }
        )

        val request = CapabilityRequest(
            capability = CapabilityId.PACKAGE_CLEAR_DATA,
            parameters = mapOf("packageName" to "com.example.app"),
            policy = explicitPolicy(CapabilityBackendId.SHIZUKU)
        )

        val result = backend.execute(request)
        assertTrue(result.isSuccess)
        assertEquals(PrivilegedOperation.ClearPackageData("com.example.app"), executed)

        val verification = backend.verify(request, result)
        assertTrue(verification.verified)
        assertTrue(verification.attempted)
    }

    @Test
    fun settingWriteVerificationSucceedsWhenActualMatchesExpected() = runBlocking {
        val backend = ShizukuCapabilityBackend(
            readSetting = { ns, key -> if (ns == "GLOBAL" && key == "airplane_mode_on") "1" else null }
        )
        val request = CapabilityRequest(
            capability = CapabilityId.SYSTEM_SETTING_WRITE,
            parameters = mapOf("namespace" to "GLOBAL", "key" to "airplane_mode_on", "value" to "1"),
            policy = explicitPolicy(CapabilityBackendId.SHIZUKU)
        )
        val result = com.nexaflow.domain.capability.CapabilityResult(
            status = com.nexaflow.domain.capability.CapabilityStatus.SUCCESS,
            backend = CapabilityBackendId.SHIZUKU,
            message = "ok"
        )

        val verification = backend.verify(request, result)
        assertTrue(verification.attempted)
        assertTrue(verification.verified)
        assertTrue(verification.message.contains("verified: 1"))
    }

    @Test
    fun settingWriteVerificationFailsWhenActualDiffersFromExpected() = runBlocking {
        val backend = RootCapabilityBackend(
            readSetting = { ns, key -> if (ns == "GLOBAL" && key == "airplane_mode_on") "0" else null }
        )
        val request = CapabilityRequest(
            capability = CapabilityId.SYSTEM_SETTING_WRITE,
            parameters = mapOf("namespace" to "GLOBAL", "key" to "airplane_mode_on", "value" to "1"),
            policy = explicitPolicy(CapabilityBackendId.ROOT)
        )
        val result = com.nexaflow.domain.capability.CapabilityResult(
            status = com.nexaflow.domain.capability.CapabilityStatus.SUCCESS,
            backend = CapabilityBackendId.ROOT,
            message = "ok"
        )

        val verification = backend.verify(request, result)
        assertTrue(verification.attempted)
        assertFalse(verification.verified)
        assertTrue(verification.message.contains("mismatch"))
    }

    @Test
    fun packageForceStopVerificationChecksProcessState() = runBlocking {
        var isRunning = true
        val backend = ShizukuCapabilityBackend(
            isPackageRunning = { isRunning }
        )
        val request = CapabilityRequest(
            capability = CapabilityId.PACKAGE_FORCE_STOP,
            parameters = mapOf("packageName" to "com.example.app"),
            policy = explicitPolicy(CapabilityBackendId.SHIZUKU)
        )
        val result = com.nexaflow.domain.capability.CapabilityResult(
            status = com.nexaflow.domain.capability.CapabilityStatus.SUCCESS,
            backend = CapabilityBackendId.SHIZUKU,
            message = "ok"
        )

        // Still running -> verification fails
        val vFail = backend.verify(request, result)
        assertTrue(vFail.attempted)
        assertFalse(vFail.verified)

        // Stopped -> verification passes
        isRunning = false
        val vPass = backend.verify(request, result)
        assertTrue(vPass.attempted)
        assertTrue(vPass.verified)
    }

    private fun forceStopRequest(backend: CapabilityBackendId? = null): CapabilityRequest = CapabilityRequest(
        capability = CapabilityId.PACKAGE_FORCE_STOP,
        parameters = mapOf("packageName" to "com.example.app"),
        policy = backend?.let(::explicitPolicy) ?: ExecutionPolicy(allowPrivilegedBackends = true)
    )

    private fun explicitPolicy(backend: CapabilityBackendId): ExecutionPolicy = ExecutionPolicy(
        allowedBackends = listOf(backend),
        preferredBackends = listOf(backend),
        allowPrivilegedBackends = true
    )
}
