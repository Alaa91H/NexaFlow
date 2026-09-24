package com.nexaflow.core.execution.capability

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.domain.capability.CapabilityEnvironmentId
import com.nexaflow.domain.capability.CapabilityEnvironmentReport
import com.nexaflow.domain.capability.CapabilityEnvironmentState
import com.nexaflow.domain.capability.PrivilegeGrantState
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface

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
    fun reports(snapshot: PrivilegeSnapshot? = null): List<CapabilityEnvironmentReport> {
        if (snapshot != null && !snapshot.neverObserved) {
            return listOf(
                standardReport(),
                snapshotEnvironmentReport(
                    snapshot,
                    surface = PrivilegeSurface.SHIZUKU,
                    key = PrivilegeSnapshot.ENV_SHIZUKU,
                    environment = CapabilityEnvironmentId.SHIZUKU
                ),
                snapshotEnvironmentReport(
                    snapshot,
                    surface = PrivilegeSurface.ROOT,
                    key = PrivilegeSnapshot.ENV_ROOT,
                    environment = CapabilityEnvironmentId.ROOT
                ),
                snapshotEnvironmentReport(
                    snapshot,
                    surface = PrivilegeSurface.DEVICE_OWNER,
                    key = PrivilegeSnapshot.ENV_DEVICE_OWNER,
                    environment = CapabilityEnvironmentId.MANAGED_DEVICE
                ),
                adbUnsupportedReport()
            )
        }

        return listOf(
            standardReport(),
            shizukuReport(),
            rootReport(),
            managedDeviceReport(),
            adbUnsupportedReport()
        )
    }

    private fun snapshotEnvironmentReport(
        snapshot: PrivilegeSnapshot,
        surface: PrivilegeSurface,
        key: String,
        environment: CapabilityEnvironmentId
    ): CapabilityEnvironmentReport {
        val observation = snapshot.observation(surface, key)
            ?: return CapabilityEnvironmentReport(
                environment,
                CapabilityEnvironmentState.UNAVAILABLE,
                "PRIVILEGE_OBSERVATION_MISSING"
            )
        val state = when (observation.state) {
            PrivilegeGrantState.GRANTED -> CapabilityEnvironmentState.AVAILABLE
            PrivilegeGrantState.PERMISSION_REQUIRED,
            PrivilegeGrantState.NOT_GRANTED -> CapabilityEnvironmentState.PERMISSION_REQUIRED
            PrivilegeGrantState.NOT_INSTALLED -> CapabilityEnvironmentState.NOT_INSTALLED
            PrivilegeGrantState.NOT_RUNNING -> CapabilityEnvironmentState.NOT_RUNNING
            PrivilegeGrantState.SERVICE_UNAVAILABLE,
            PrivilegeGrantState.PARTIAL -> CapabilityEnvironmentState.SERVICE_UNAVAILABLE
            PrivilegeGrantState.UNSUPPORTED -> CapabilityEnvironmentState.UNSUPPORTED
            PrivilegeGrantState.UNKNOWN -> CapabilityEnvironmentState.UNAVAILABLE
        }
        return CapabilityEnvironmentReport(
            environment = environment,
            state = state,
            detailCode = observation.detailCode
        )
    }

    private fun adbUnsupportedReport() = CapabilityEnvironmentReport(
        environment = CapabilityEnvironmentId.ADB,
        state = CapabilityEnvironmentState.UNSUPPORTED,
        detailCode = "ADB_NOT_EXPOSED_TO_NORMAL_APP"
    )

    private fun standardReport() = CapabilityEnvironmentReport(
        environment = CapabilityEnvironmentId.STANDARD,
        state = CapabilityEnvironmentState.AVAILABLE,
        detailCode = "ANDROID_PUBLIC_APIS"
    )

    private fun shizukuReport(): CapabilityEnvironmentReport {
        val running = probe(shizukuRunning)
        if (running == null) {
            return CapabilityEnvironmentReport(
                CapabilityEnvironmentId.SHIZUKU,
                CapabilityEnvironmentState.SERVICE_UNAVAILABLE,
                "SHIZUKU_STATE_PROBE_FAILED"
            )
        }
        if (running) {
            val granted = probe(shizukuGranted)
            if (granted == null) {
                return CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.SHIZUKU,
                    CapabilityEnvironmentState.SERVICE_UNAVAILABLE,
                    "SHIZUKU_PERMISSION_PROBE_FAILED"
                )
            }
            if (!granted) {
                return CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.SHIZUKU,
                    CapabilityEnvironmentState.PERMISSION_REQUIRED,
                    "SHIZUKU_PERMISSION_REQUIRED"
                )
            }
            val userServiceBound = probe(shizukuUserServiceBound)
            if (userServiceBound == null) {
                return CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.SHIZUKU,
                    CapabilityEnvironmentState.SERVICE_UNAVAILABLE,
                    "SHIZUKU_USER_SERVICE_PROBE_FAILED"
                )
            }
            return CapabilityEnvironmentReport(
                CapabilityEnvironmentId.SHIZUKU,
                if (userServiceBound) CapabilityEnvironmentState.AVAILABLE else CapabilityEnvironmentState.SERVICE_UNAVAILABLE,
                if (userServiceBound) "SHIZUKU_USER_SERVICE_READY" else "SHIZUKU_USER_SERVICE_UNAVAILABLE"
            )
        }

        return when (probe(shizukuInstalled)) {
            true -> CapabilityEnvironmentReport(
                CapabilityEnvironmentId.SHIZUKU,
                CapabilityEnvironmentState.NOT_RUNNING,
                "SHIZUKU_SERVER_NOT_RUNNING"
            )
            false -> CapabilityEnvironmentReport(
                CapabilityEnvironmentId.SHIZUKU,
                CapabilityEnvironmentState.NOT_INSTALLED,
                "SHIZUKU_NOT_INSTALLED"
            )
            null -> CapabilityEnvironmentReport(
                CapabilityEnvironmentId.SHIZUKU,
                CapabilityEnvironmentState.UNAVAILABLE,
                "SHIZUKU_INSTALLATION_PROBE_FAILED"
            )
        }
    }

    private fun rootReport(): CapabilityEnvironmentReport {
        return when (probe(rootAvailable)) {
            true -> CapabilityEnvironmentReport(
                CapabilityEnvironmentId.ROOT,
                CapabilityEnvironmentState.AVAILABLE,
                "ROOT_UID_ZERO_VERIFIED"
            )
            null -> CapabilityEnvironmentReport(
                CapabilityEnvironmentId.ROOT,
                CapabilityEnvironmentState.UNAVAILABLE,
                "ROOT_STATE_PROBE_FAILED"
            )
            false -> when (probe(suBinaryPresent)) {
                true -> CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.ROOT,
                    CapabilityEnvironmentState.PERMISSION_REQUIRED,
                    "ROOT_GRANT_REQUIRED_OR_DENIED"
                )
                false -> CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.ROOT,
                    CapabilityEnvironmentState.NOT_INSTALLED,
                    "ROOT_BINARY_NOT_FOUND"
                )
                null -> CapabilityEnvironmentReport(
                    CapabilityEnvironmentId.ROOT,
                    CapabilityEnvironmentState.UNAVAILABLE,
                    "ROOT_BINARY_PROBE_FAILED"
                )
            }
        }
    }

    private fun managedDeviceReport(): CapabilityEnvironmentReport {
        val owner = probe(deviceOwner)
        return CapabilityEnvironmentReport(
            environment = CapabilityEnvironmentId.MANAGED_DEVICE,
            state = when (owner) {
                true -> CapabilityEnvironmentState.AVAILABLE
                false -> CapabilityEnvironmentState.UNAVAILABLE
                null -> CapabilityEnvironmentState.UNAVAILABLE
            },
            detailCode = when (owner) {
                true -> "DEVICE_OWNER_ACTIVE"
                false -> "DEVICE_OWNER_REQUIRED"
                null -> "DEVICE_OWNER_PROBE_FAILED"
            }
        )
    }

    private fun probe(block: () -> Boolean): Boolean? = try {
        block()
    } catch (_: Exception) {
        null
    }

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
