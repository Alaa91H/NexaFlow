package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityEnvironmentId
import com.nexaflow.domain.capability.CapabilityEnvironmentState
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityEnvironmentInspectorTest {

    @Test
    fun `reports Shizuku available only with live binder grant and UserService`() {
        val inspector = inspector(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuGranted = true,
            userServiceBound = true
        )

        assertReport(inspector, CapabilityEnvironmentId.SHIZUKU, CapabilityEnvironmentState.AVAILABLE, "SHIZUKU_USER_SERVICE_READY")
    }

    @Test
    fun `reports Shizuku permission required before UserService readiness`() {
        val inspector = inspector(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuGranted = false,
            userServiceBound = false
        )

        assertReport(inspector, CapabilityEnvironmentId.SHIZUKU, CapabilityEnvironmentState.PERMISSION_REQUIRED, "SHIZUKU_PERMISSION_REQUIRED")
    }

    @Test
    fun `reports Shizuku service unavailable after grant until UserService binds`() {
        val inspector = inspector(
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuGranted = true,
            userServiceBound = false
        )

        assertReport(inspector, CapabilityEnvironmentId.SHIZUKU, CapabilityEnvironmentState.SERVICE_UNAVAILABLE, "SHIZUKU_USER_SERVICE_UNAVAILABLE")
    }

    @Test
    fun `distinguishes Shizuku not running from not installed`() {
        assertReport(
            inspector(shizukuInstalled = true, shizukuRunning = false),
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.NOT_RUNNING,
            "SHIZUKU_SERVER_NOT_RUNNING"
        )
        assertReport(
            inspector(shizukuInstalled = false, shizukuRunning = false),
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.NOT_INSTALLED,
            "SHIZUKU_NOT_INSTALLED"
        )
    }

    @Test
    fun `does not treat root binary presence as an available root environment`() {
        val inspector = inspector(suBinaryPresent = true, rootAvailable = false)

        assertReport(inspector, CapabilityEnvironmentId.ROOT, CapabilityEnvironmentState.PERMISSION_REQUIRED, "ROOT_GRANT_REQUIRED_OR_DENIED")
    }

    @Test
    fun `reports root available only after verification and device owner separately`() {
        val inspector = inspector(suBinaryPresent = true, rootAvailable = true, deviceOwner = true)

        assertReport(inspector, CapabilityEnvironmentId.ROOT, CapabilityEnvironmentState.AVAILABLE, "ROOT_UID_ZERO_VERIFIED")
        assertReport(inspector, CapabilityEnvironmentId.MANAGED_DEVICE, CapabilityEnvironmentState.AVAILABLE, "DEVICE_OWNER_ACTIVE")
        assertReport(inspector, CapabilityEnvironmentId.ADB, CapabilityEnvironmentState.UNSUPPORTED, "ADB_NOT_EXPOSED_TO_NORMAL_APP")
    }

    @Test
    fun `each environment probe is sampled at most once per report snapshot`() {
        var runningCalls = 0
        var grantedCalls = 0
        var serviceCalls = 0
        var rootCalls = 0
        var ownerCalls = 0
        var installedCalls = 0
        var suBinaryCalls = 0
        val inspector = CapabilityEnvironmentInspector(
            shizukuInstalled = { installedCalls++; true },
            shizukuRunning = { runningCalls++; true },
            shizukuGranted = { grantedCalls++; true },
            shizukuUserServiceBound = { serviceCalls++; true },
            suBinaryPresent = { suBinaryCalls++; true },
            rootAvailable = { rootCalls++; true },
            deviceOwner = { ownerCalls++; true }
        )

        inspector.reports()

        assertEquals(1, runningCalls)
        assertEquals(1, grantedCalls)
        assertEquals(1, serviceCalls)
        assertEquals(1, rootCalls)
        assertEquals(1, ownerCalls)
        assertEquals(0, installedCalls)
        assertEquals(0, suBinaryCalls)
    }

    @Test
    fun `probe failures fail closed without aborting other environment reports`() {
        val inspector = CapabilityEnvironmentInspector(
            shizukuInstalled = { true },
            shizukuRunning = { error("binder disappeared") },
            shizukuGranted = { true },
            shizukuUserServiceBound = { true },
            suBinaryPresent = { true },
            rootAvailable = { throw IllegalStateException("su probe failed") },
            deviceOwner = { true }
        )

        val reports = inspector.reports()

        val shizuku = reports.first { it.environment == CapabilityEnvironmentId.SHIZUKU }
        assertEquals(CapabilityEnvironmentState.SERVICE_UNAVAILABLE, shizuku.state)
        assertEquals("SHIZUKU_STATE_PROBE_FAILED", shizuku.detailCode)
        val root = reports.first { it.environment == CapabilityEnvironmentId.ROOT }
        assertEquals(CapabilityEnvironmentState.UNAVAILABLE, root.state)
        assertEquals("ROOT_STATE_PROBE_FAILED", root.detailCode)
        val managed = reports.first { it.environment == CapabilityEnvironmentId.MANAGED_DEVICE }
        assertEquals(CapabilityEnvironmentState.AVAILABLE, managed.state)
    }

    private fun inspector(
        shizukuInstalled: Boolean = false,
        shizukuRunning: Boolean = false,
        shizukuGranted: Boolean = false,
        userServiceBound: Boolean = false,
        suBinaryPresent: Boolean = false,
        rootAvailable: Boolean = false,
        deviceOwner: Boolean = false
    ) = CapabilityEnvironmentInspector(
        shizukuInstalled = { shizukuInstalled },
        shizukuRunning = { shizukuRunning },
        shizukuGranted = { shizukuGranted },
        shizukuUserServiceBound = { userServiceBound },
        suBinaryPresent = { suBinaryPresent },
        rootAvailable = { rootAvailable },
        deviceOwner = { deviceOwner }
    )

    private fun assertReport(
        inspector: CapabilityEnvironmentInspector,
        environment: CapabilityEnvironmentId,
        state: CapabilityEnvironmentState,
        detailCode: String
    ) {
        val report = inspector.reports().first { it.environment == environment }
        assertEquals(state, report.state)
        assertEquals(detailCode, report.detailCode)
    }
}
