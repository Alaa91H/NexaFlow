package com.nexaflow.core.execution.capability

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.net.toUri
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.PrivilegeLevel
import com.nexaflow.domain.capability.ThermalState
import com.nexaflow.domain.capability.VerificationResult

/**
 * Declarative public-API catalog. Operations outside this catalog must be
 * supplied by an explicitly registered backend and policy; they never inherit
 * a generic shell fallback.
 */
object AndroidPublicCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CapabilityId.PACKAGE_READ,
            displayName = "Read installed package metadata",
            description = "Reads visible Android package metadata through PackageManager",
            risk = CapabilityRiskLevel.LOW,
            supportedBackends = listOf(CapabilityBackendId.ANDROID_API),
            parameters = listOf(
                CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true)
            )
        ),
        CapabilityDescriptor(
            id = CapabilityId.INTENT_LAUNCH,
            displayName = "Open HTTPS link",
            description = "Hands an HTTPS URL to an Android activity chosen by the system",
            risk = CapabilityRiskLevel.MODERATE,
            supportedBackends = listOf(CapabilityBackendId.INTENT),
            parameters = listOf(
                CapabilityParameterSpec("url", CapabilityParameterType.HTTPS_URL, required = true, maximumLength = 2_048)
            )
        ),
        CapabilityDescriptor(
            id = CapabilityId.DEVICE_STATE_READ,
            displayName = "Read device execution state",
            description = "Reads non-sensitive power, connectivity, screen and thermal state",
            risk = CapabilityRiskLevel.LOW,
            supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
        )
    )
}

/**
 * Public Android APIs only. It intentionally does not delegate a failed call
 * to SystemController because that object may choose a privileged integration.
 */
class AndroidPublicCapabilityBackend(private val context: Context) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.ANDROID_API
    override val supportedCapabilities: Set<CapabilityId> = setOf(
        CapabilityId.PACKAGE_READ,
        CapabilityId.DEVICE_STATE_READ
    )

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = when (request.capability) {
        CapabilityId.PACKAGE_READ -> packageAvailability(request.parameters["packageName"])
        CapabilityId.DEVICE_STATE_READ -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
        else -> BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Capability is not implemented by Android API backend")
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult = when (request.capability) {
        CapabilityId.PACKAGE_READ -> readPackage(checkNotNull(request.parameters["packageName"]))
        CapabilityId.DEVICE_STATE_READ -> readDeviceState()
        else -> CapabilityResult.unsupported("Capability is not implemented by Android API backend")
    }

    override suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult = when (request.capability) {
        CapabilityId.PACKAGE_READ -> {
            val packageName = result.packageName ?: request.parameters["packageName"]
            val visible = packageName != null && runCatching { packageInfo(packageName) }.isSuccess
            VerificationResult(true, visible, if (visible) "Package metadata remains visible" else "Package is no longer visible")
        }
        CapabilityId.DEVICE_STATE_READ -> VerificationResult(true, true, "Device state was read from Android services")
        else -> super.verify(request, result)
    }

    private fun packageAvailability(packageName: String?): BackendAvailability {
        if (packageName == null) return BackendAvailability(id, CapabilityAvailability.AVAILABLE)
        return runCatching { packageInfo(packageName) }
            .fold(
                onSuccess = { BackendAvailability(id, CapabilityAvailability.AVAILABLE) },
                onFailure = { BackendAvailability(id, CapabilityAvailability.PARTIAL, "Package is not installed or is not visible") }
            )
    }

    private fun readPackage(packageName: String): CapabilityResult = runCatching {
        val info = packageInfo(packageName)
        CapabilityResult(
            status = CapabilityStatus.SUCCESS,
            backend = id,
            message = "Package metadata read",
            packageName = packageName,
            newVersion = versionName(info),
            metadata = mapOf("versionCode" to versionCode(info).toString())
        )
    }.getOrElse {
        CapabilityResult.failed(
            com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
            "Package is not installed or is not visible",
            id
        )
    }

    private fun readDeviceState(): CapabilityResult {
        val state = AndroidCapabilityDeviceStateReader(context).capture(System.currentTimeMillis())
        return CapabilityResult(
            status = CapabilityStatus.SUCCESS,
            backend = id,
            message = "Device state read",
            metadata = mapOf(
                "wifiConnected" to state.wifiConnected.toString(),
                "batteryPercent" to state.batteryPercent.toString(),
                "charging" to state.charging.toString(),
                "screenInteractive" to state.screenInteractive.toString(),
                "thermalState" to state.thermalState.name
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun versionName(info: android.content.pm.PackageInfo): String? = info.versionName
}

/** Separate intent backend because startActivity is a user-visible handoff, not a verified side effect. */
class AndroidIntentCapabilityBackend(private val context: Context) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.INTENT
    override val supportedCapabilities: Set<CapabilityId> = setOf(CapabilityId.INTENT_LAUNCH)

    override suspend fun availability(request: CapabilityRequest): BackendAvailability {
        val url = request.parameters["url"] ?: return BackendAvailability(id, CapabilityAvailability.AVAILABLE)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolvable = intent.resolveActivity(context.packageManager) != null
        return BackendAvailability(
            id,
            if (resolvable) CapabilityAvailability.AVAILABLE else CapabilityAvailability.UNAVAILABLE,
            if (resolvable) null else "No activity can handle the URL"
        )
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult = runCatching {
        val url = checkNotNull(request.parameters["url"])
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        CapabilityResult(
            status = CapabilityStatus.PENDING_USER_ACTION,
            backend = id,
            message = "Android activity was launched; completion is controlled by the target app",
            metadata = mapOf("handoff" to "intent")
        )
    }.getOrElse { error ->
        CapabilityResult.failed(
            com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
            error.message ?: "No activity can handle the URL",
            id
        )
    }
}

/** One best-effort Android snapshot used by policy and read-only diagnostics. */
class AndroidCapabilityDeviceStateReader(private val context: Context) {
    fun capture(nowMs: Long): CapabilityDeviceState {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        val power = context.getSystemService(PowerManager::class.java)
        return CapabilityDeviceState(
            capturedAt = nowMs,
            wifiConnected = wifi,
            batteryPercent = level,
            charging = battery.isCharging,
            screenInteractive = power.isInteractive,
            thermalState = thermalState(power)
        )
    }

    private fun thermalState(power: PowerManager): ThermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
            else -> ThermalState.UNKNOWN
        }
    } else ThermalState.UNKNOWN
}
