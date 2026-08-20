package com.nexaflow.core.execution.capability

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.domain.capability.CapabilityEnvironmentId
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilityEnvironmentState

/**
 * Read-only detector for optional execution environments. It intentionally
 * separates installation, liveness, permission and service readiness so a
 * visible diagnostic never upgrades into an executable capability by itself.
 */
class CapabilityEnvironmentInspector(
    private val shizukuInstalled: () -> Boolean,
    private val shizukuRunning: () -> Boolean,
    private val shizukuGranted: () -> Boolean,
    private val shizukuUserServiceBound: () -> Boolean,
    private val suBinaryPresent: () -> Boolean,
    private val rootAvailable: () -> Boolean,
    private val deviceOwner: () -> Boolean
) {
    fun reports(): List<CapabilityEnvironmentReport> = listOf(
        standardReport(),
        shizukuReport(),
        rootReport(),
        managedDeviceReport(),
        CapabilityEnvironmentReport(
            environment = CapabilityEnvironmentId.ADB,
            state = CapabilityEnvironmentState.UNSUPPORTED,
            detailCode = "ADB_NOT_EXPOSED_TO_NORMAL_APP"
        )
    )

    private fun standardReport() = CapabilityEnvironmentReport(
        environment = CapabilityEnvironmentId.STANDARD,
        state = CapabilityEnvironmentState.AVAILABLE,
        detailCode = "ANDROID_PUBLIC_APIS"
    )

    private fun shizukuReport(): CapabilityEnvironmentReport = when {
        shizukuRunning() && shizukuGranted() && shizukuUserServiceBound() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.AVAILABLE,
            "SHIZUKU_USER_SERVICE_READY"
        )
        shizukuRunning() && !shizukuGranted() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.PERMISSION_REQUIRED,
            "SHIZUKU_PERMISSION_REQUIRED"
        )
        shizukuRunning() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.SERVICE_UNAVAILABLE,
            "SHIZUKU_USER_SERVICE_UNAVAILABLE"
        )
        shizukuInstalled() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.NOT_RUNNING,
            "SHIZUKU_SERVER_NOT_RUNNING"
        )
        else -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.SHIZUKU,
            CapabilityEnvironmentState.NOT_INSTALLED,
            "SHIZUKU_NOT_INSTALLED"
        )
    }

    private fun rootReport(): CapabilityEnvironmentReport = when {
        rootAvailable() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.ROOT,
            CapabilityEnvironmentState.AVAILABLE,
            "ROOT_UID_ZERO_VERIFIED"
        )
        suBinaryPresent() -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.ROOT,
            CapabilityEnvironmentState.PERMISSION_REQUIRED,
            "ROOT_GRANT_REQUIRED_OR_DENIED"
        )
        else -> CapabilityEnvironmentReport(
            CapabilityEnvironmentId.ROOT,
            CapabilityEnvironmentState.NOT_INSTALLED,
            "ROOT_BINARY_NOT_FOUND"
        )
    }

    private fun managedDeviceReport(): CapabilityEnvironmentReport = CapabilityEnvironmentReport(
        environment = CapabilityEnvironmentId.MANAGED_DEVICE,
        state = if (deviceOwner()) CapabilityEnvironmentState.AVAILABLE else CapabilityEnvironmentState.UNAVAILABLE,
        detailCode = if (deviceOwner()) "DEVICE_OWNER_ACTIVE" else "DEVICE_OWNER_REQUIRED"
    )

    companion object {
        fun forContext(context: Context): CapabilityEnvironmentInspector {
            val appContext = context.applicationContext
            return CapabilityEnvironmentInspector(
                shizukuInstalled = { SystemAppStatusDetector.isShizukuAvailable(appContext) },
                shizukuRunning = PrivilegedRunner::isShizukuRunning,
                shizukuGranted = PrivilegedRunner::isShizukuGranted,
                shizukuUserServiceBound = { ShizukuShellBridge.isUserServiceBound },
                suBinaryPresent = SystemAppStatusDetector::isSuBinaryAvailable,
                rootAvailable = SystemAppStatusDetector::isRootAvailable,
                deviceOwner = {
                    runCatching {
                        appContext.getSystemService(DevicePolicyManager::class.java)
                            .isDeviceOwnerApp(appContext.packageName)
                    }.getOrDefault(false)
                }
            )
        }
    }
}
