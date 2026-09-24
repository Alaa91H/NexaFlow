package com.nexaflow.core.execution.capability.semantic.strategies

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.nexaflow.core.common.HotspotStateReader
import com.nexaflow.core.execution.capability.semantic.CapabilityStrategy
import com.nexaflow.core.execution.capability.semantic.OperationOutcome
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.StrategyAvailability
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Public Android framework APIs only. This strategy never delegates to a
 * privileged runtime and never shells out; when a public API is not available
 * to the caller (behavioral restriction on modern Android), it reports
 * unavailable/unsupported honestly instead of pretending.
 */
class AndroidApiStateStrategy(private val context: Context) : CapabilityStrategy {
    override val id: StrategyId = StrategyId.ANDROID_PUBLIC_API

    override val supportedOperations: Set<SemanticOperationId> = setOf(
        SemanticOperationId.WIFI_GET_STATE,
        SemanticOperationId.WIFI_SET_STATE,
        SemanticOperationId.BLUETOOTH_GET_STATE,
        SemanticOperationId.BLUETOOTH_SET_STATE,
        SemanticOperationId.LOCATION_GET_STATE,
        SemanticOperationId.AIRPLANE_MODE_GET_STATE,
        SemanticOperationId.ROTATION_GET_STATE,
        SemanticOperationId.ROTATION_SET_STATE,
        SemanticOperationId.BRIGHTNESS_GET,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_GET,
        SemanticOperationId.SCREEN_TIMEOUT_SET,
        SemanticOperationId.DND_GET_STATE,
        SemanticOperationId.DND_SET_STATE,
        SemanticOperationId.NFC_GET_STATE,
        SemanticOperationId.HOTSPOT_GET_STATE,
        SemanticOperationId.MOBILE_DATA_GET_STATE,
        SemanticOperationId.DATA_SAVER_GET_STATE,
        SemanticOperationId.DATA_SAVER_SET_STATE,
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE
    )

    override suspend fun availability(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): StrategyAvailability = when (operation) {
        SemanticOperationId.WIFI_GET_STATE ->
            if (service(WifiManager::class.java) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "Wi-Fi service is unavailable")

        SemanticOperationId.WIFI_SET_STATE -> when {
            service(WifiManager::class.java) == null ->
                StrategyAvailability(false, "Wi-Fi service is unavailable")
            !publicWifiToggleAllowed(
                Build.VERSION.SDK_INT,
                isFrameworkPrivilegedCaller()
            ) ->
                StrategyAvailability(
                    false,
                    "Public Wi-Fi toggling is restricted for normal apps on this Android version"
                )
            else -> StrategyAvailability(true)
        }

        SemanticOperationId.BLUETOOTH_GET_STATE -> {
            val adapter = service(BluetoothManager::class.java)?.adapter
            when {
                adapter == null -> StrategyAvailability(false, "Bluetooth adapter is unavailable")
                !hasBluetoothConnectPermission() -> StrategyAvailability(
                    available = false,
                    reason = "BLUETOOTH_CONNECT has not been granted",
                    permissionRequired = true
                )
                else -> StrategyAvailability(true)
            }
        }

        SemanticOperationId.BLUETOOTH_SET_STATE -> {
            val adapter = service(BluetoothManager::class.java)?.adapter
            when {
                adapter == null -> StrategyAvailability(false, "Bluetooth adapter is unavailable")
                !hasBluetoothConnectPermission() -> StrategyAvailability(
                    available = false,
                    reason = "BLUETOOTH_CONNECT has not been granted",
                    permissionRequired = true
                )
                !publicBluetoothToggleAllowed(
                    Build.VERSION.SDK_INT,
                    isFrameworkPrivilegedCaller()
                ) ->
                    StrategyAvailability(
                        false,
                        "Public Bluetooth toggling is restricted for normal apps on this Android version"
                    )
                else -> StrategyAvailability(true)
            }
        }

        SemanticOperationId.LOCATION_GET_STATE ->
            if (service(LocationManager::class.java) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "Location manager is unavailable")

        SemanticOperationId.AIRPLANE_MODE_GET_STATE ->
            StrategyAvailability(true)

        SemanticOperationId.ROTATION_GET_STATE,
        SemanticOperationId.ROTATION_SET_STATE,
        SemanticOperationId.BRIGHTNESS_GET,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_GET,
        SemanticOperationId.SCREEN_TIMEOUT_SET ->
            if (canWriteSystemSettings()) StrategyAvailability(true)
            else StrategyAvailability(false, "WRITE_SETTINGS has not been granted", permissionRequired = true)

        SemanticOperationId.DND_GET_STATE ->
            if (service(NotificationManager::class.java) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "Notification manager is unavailable")

        SemanticOperationId.DND_SET_STATE ->
            if (notificationPolicyAccessGranted()) StrategyAvailability(true)
            else StrategyAvailability(false, "Notification policy access is required", permissionRequired = true)

        SemanticOperationId.NFC_GET_STATE ->
            if (NfcAdapter.getDefaultAdapter(context) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "NFC hardware is absent")

        SemanticOperationId.HOTSPOT_GET_STATE ->
            if (service(WifiManager::class.java) != null) {
                StrategyAvailability(true)
            } else StrategyAvailability(false, "Wi-Fi service is unavailable")

        SemanticOperationId.MOBILE_DATA_GET_STATE ->
            if (service(TelephonyManager::class.java) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "Telephony is unavailable")

        SemanticOperationId.DATA_SAVER_GET_STATE ->
            if (service(ConnectivityManager::class.java) != null) StrategyAvailability(true)
            else StrategyAvailability(false, "Connectivity manager is unavailable")

        SemanticOperationId.DATA_SAVER_SET_STATE -> when {
            service(ConnectivityManager::class.java) == null ->
                StrategyAvailability(false, "Connectivity manager is unavailable")
            !hasManageNetworkPolicyPermission() ->
                StrategyAvailability(
                    false,
                    "Changing Data Saver requires privileged network-policy access",
                    permissionRequired = true
                )
            else -> StrategyAvailability(true)
        }

        else -> StrategyAvailability(false, "Operation is not implemented by the public-API strategy")
    }

    override suspend fun execute(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): OperationOutcome {
        val enable = request.parameters["enabled"]?.toBooleanStrictOrNull()
        return when (operation) {
            SemanticOperationId.WIFI_SET_STATE -> setWifi(
                enable ?: return missingEnabled(operation)
            )
            SemanticOperationId.BLUETOOTH_SET_STATE -> setBluetooth(
                enable ?: return missingEnabled(operation)
            )
            SemanticOperationId.ROTATION_SET_STATE -> setRotation(
                enable ?: return missingEnabled(operation)
            )
            SemanticOperationId.BRIGHTNESS_SET -> setBrightness(request.parameters["value"])
            SemanticOperationId.SCREEN_TIMEOUT_SET -> setScreenTimeout(request.parameters["seconds"])
            SemanticOperationId.DND_SET_STATE -> setDnd(
                enable ?: return missingEnabled(operation)
            )
            SemanticOperationId.DATA_SAVER_SET_STATE -> setDataSaver(
                enable ?: return missingEnabled(operation)
            )
            else -> OperationOutcome.unsupported(operation, "Write is not implemented by the public-API strategy")
        }
    }

    private fun missingEnabled(operation: SemanticOperationId): OperationOutcome =
        OperationOutcome.failed(
            operation,
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            "The 'enabled' parameter is missing or not a boolean",
            strategy = id
        )

    override suspend fun readState(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): Boolean? = when (operation) {
        SemanticOperationId.WIFI_GET_STATE ->
            service(WifiManager::class.java)?.isWifiEnabled
        SemanticOperationId.BLUETOOTH_GET_STATE ->
            service(BluetoothManager::class.java)?.adapter?.isEnabled
        SemanticOperationId.LOCATION_GET_STATE ->
            service(LocationManager::class.java)?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { it.isLocationEnabled }.getOrNull()
                } else {
                    // API 26/27 documented fallback: location providers are
                    // enabled through the secure location-mode setting.
                    @Suppress("DEPRECATION")
                    Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.LOCATION_MODE,
                        Settings.Secure.LOCATION_MODE_OFF
                    ) != Settings.Secure.LOCATION_MODE_OFF
                }
            }
        SemanticOperationId.AIRPLANE_MODE_GET_STATE ->
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        SemanticOperationId.ROTATION_GET_STATE ->
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        SemanticOperationId.DND_GET_STATE ->
            service(NotificationManager::class.java)?.currentInterruptionFilter
                ?.let { it == NotificationManager.INTERRUPTION_FILTER_NONE ||
                    it == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                    it == NotificationManager.INTERRUPTION_FILTER_PRIORITY }
        SemanticOperationId.NFC_GET_STATE ->
            NfcAdapter.getDefaultAdapter(context)?.isEnabled
        SemanticOperationId.HOTSPOT_GET_STATE ->
            HotspotStateReader.currentState(context)
        SemanticOperationId.DATA_SAVER_GET_STATE ->
            service(ConnectivityManager::class.java)?.restrictBackgroundStatus?.let {
                it == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ||
                    it == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
            }
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE -> {
            // Public PackageManager read: the enabled state observable by the
            // caller. DEFAULT/ENABLED both count as enabled; anything else
            // (DISABLED_USER, DISABLED until used…) reads as not-enabled.
            val pkg = request.parameters["packageName"]
            pkg?.let {
                runCatching {
                    context.packageManager.getApplicationEnabledSetting(it)
                }.getOrNull()?.let { state ->
                    state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                        state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
            }
        }
        else -> null
    }

    override suspend fun readStateValue(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): String? = when (operation) {
        // Effective-value reads for strict write verification: the actually
        // applied system value, in the same unit the write uses.
        SemanticOperationId.BRIGHTNESS_GET ->
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrNull()?.toString()
        SemanticOperationId.SCREEN_TIMEOUT_GET ->
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            }.getOrNull()?.let { (it / 1000).toString() } // write unit is seconds
        else -> null
    }

    // ---- writers -----------------------------------------------------------

    private fun setWifi(enabled: Boolean): OperationOutcome {
        val wifi = service(WifiManager::class.java)
            ?: return OperationOutcome.failed(
                SemanticOperationId.WIFI_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Wi-Fi service is unavailable"
            )
        @Suppress("DEPRECATION")
        val changed = runCatching { wifi.setWifiEnabled(enabled) }.getOrDefault(false)
        return if (changed) {
            wifiWriteOutcome(SemanticOperationId.WIFI_SET_STATE, enabled, "Wi-Fi")
        } else {
            // Modern Android removed app-side toggling for normal callers; the
            // router will advance to a privileged strategy. This is a
            // transport-safe, no-side-effect refusal.
            OperationOutcome.failed(
                SemanticOperationId.WIFI_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Public Wi-Fi toggle was rejected on this Android version",
                strategy = id,
                transportFailure = true
            )
        }
    }

    private fun wifiWriteOutcome(operation: SemanticOperationId, enabled: Boolean, name: String) =
        OperationOutcome(
            operation = operation,
            status = OperationOutcomeStatus.SUCCESS,
            strategy = id,
            message = "$name ${if (enabled) "enabled" else "disabled"}",
            metadata = mapOf("requestedEnabled" to enabled.toString())
        )

    // BLUETOOTH_CONNECT is checked at runtime below (required only from API
    // 31+); when missing we return an honest permission failure. Lint's
    // dataflow cannot prove the SDK-conditional guard, so the suppression is
    // scoped here — the same reviewed pattern SystemController uses.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // adapter.enable()/disable() are the only direct toggles.
    private fun setBluetooth(enabled: Boolean): OperationOutcome {
        val adapter = service(BluetoothManager::class.java)?.adapter
            ?: return OperationOutcome.failed(
                SemanticOperationId.BLUETOOTH_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Bluetooth adapter is unavailable"
            )
        // BLUETOOTH_CONNECT is required from API 31+ for adapter calls; the
        // guard mirrors the reviewed SystemController behavior. Without the
        // runtime grant the call throws SecurityException, so it is checked
        // explicitly instead of relying on a catch-all.
        if (!hasBluetoothConnectPermission()) {
            return OperationOutcome.failed(
                SemanticOperationId.BLUETOOTH_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                "BLUETOOTH_CONNECT is required to toggle Bluetooth through the public API",
                strategy = id,
                transportFailure = false
            )
        }
        val changed = runCatching { if (enabled) adapter.enable() else adapter.disable() }
            .getOrDefault(false)
        return if (changed) {
            OperationOutcome(
                operation = SemanticOperationId.BLUETOOTH_SET_STATE,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = id,
                message = "Bluetooth ${if (enabled) "enabled" else "disabled"}",
                metadata = mapOf("requestedEnabled" to enabled.toString())
            )
        } else {
            OperationOutcome.failed(
                SemanticOperationId.BLUETOOTH_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Public Bluetooth toggle was rejected on this Android version",
                strategy = id,
                transportFailure = true
            )
        }
    }

    private fun setRotation(enabled: Boolean): OperationOutcome =
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
        }.fold(
            onSuccess = {
                OperationOutcome(
                    operation = SemanticOperationId.ROTATION_SET_STATE,
                    status = OperationOutcomeStatus.SUCCESS,
                    strategy = id,
                    message = "Auto-rotate ${if (enabled) "enabled" else "disabled"}",
                    metadata = mapOf("requestedEnabled" to enabled.toString())
                )
            },
            onFailure = {
                OperationOutcome.failed(
                    SemanticOperationId.ROTATION_SET_STATE,
                    com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                    "Writing rotation state requires WRITE_SETTINGS",
                    strategy = id
                )
            }
        )

    private fun setBrightness(value: String?): OperationOutcome {
        val level = value?.toIntOrNull()
            ?: return OperationOutcome.failed(
                SemanticOperationId.BRIGHTNESS_SET,
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                "Brightness value is missing or not an integer",
                strategy = id
            )
        if (level !in 0..255) {
            return OperationOutcome.failed(
                SemanticOperationId.BRIGHTNESS_SET,
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                "Brightness value is outside 0..255",
                strategy = id
            )
        }
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        }.fold(
            onSuccess = {
                OperationOutcome(
                    operation = SemanticOperationId.BRIGHTNESS_SET,
                    status = OperationOutcomeStatus.SUCCESS,
                    strategy = id,
                    message = "Brightness set to $level",
                    metadata = mapOf("requestedEnabled" to "true", "value" to level.toString())
                )
            },
            onFailure = {
                OperationOutcome.failed(
                    SemanticOperationId.BRIGHTNESS_SET,
                    com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                    "Writing brightness requires WRITE_SETTINGS",
                    strategy = id
                )
            }
        )
    }

    private fun setScreenTimeout(seconds: String?): OperationOutcome {
        val timeout = seconds?.toIntOrNull()
            ?: return OperationOutcome.failed(
                SemanticOperationId.SCREEN_TIMEOUT_SET,
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                "Screen timeout is missing or not an integer",
                strategy = id
            )
        if (timeout !in 1..86_400) {
            return OperationOutcome.failed(
                SemanticOperationId.SCREEN_TIMEOUT_SET,
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                "Screen timeout is outside 1..86400 seconds",
                strategy = id
            )
        }
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                timeout * 1000
            )
        }.fold(
            onSuccess = {
                OperationOutcome(
                    operation = SemanticOperationId.SCREEN_TIMEOUT_SET,
                    status = OperationOutcomeStatus.SUCCESS,
                    strategy = id,
                    message = "Screen timeout set to ${timeout}s",
                    metadata = mapOf("requestedEnabled" to "true", "seconds" to timeout.toString())
                )
            },
            onFailure = {
                OperationOutcome.failed(
                    SemanticOperationId.SCREEN_TIMEOUT_SET,
                    com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                    "Writing screen timeout requires WRITE_SETTINGS",
                    strategy = id
                )
            }
        )
    }

    private fun setDnd(enabled: Boolean): OperationOutcome {
        val notificationManager = service(NotificationManager::class.java)
            ?: return OperationOutcome.failed(
                SemanticOperationId.DND_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Notification manager is unavailable"
            )
        return runCatching {
            notificationManager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }.fold(
            onSuccess = {
                OperationOutcome(
                    operation = SemanticOperationId.DND_SET_STATE,
                    status = OperationOutcomeStatus.SUCCESS,
                    strategy = id,
                    message = "Do-Not-Disturb ${if (enabled) "enabled" else "disabled"}",
                    metadata = mapOf("requestedEnabled" to enabled.toString())
                )
            },
            onFailure = {
                OperationOutcome.failed(
                    SemanticOperationId.DND_SET_STATE,
                    com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                    "Notification policy access is required to change Do-Not-Disturb",
                    strategy = id
                )
            }
        )
    }

    private fun setDataSaver(enabled: Boolean): OperationOutcome {
        val connectivity = service(ConnectivityManager::class.java)
            ?: return OperationOutcome.failed(
                SemanticOperationId.DATA_SAVER_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Connectivity manager is unavailable"
            )
        val ok = runCatching {
            // The restrict-background toggle requires a system permission on
            // most builds; the call is attempted through the reviewed binder
            // wrapper and honestly reports failure instead of pretending.
            val method = ConnectivityManager::class.java
                .getMethod("setRestrictBackground", Boolean::class.javaPrimitiveType)
            method.invoke(connectivity, enabled) as Boolean
        }.getOrDefault(false)
        return if (ok) {
            OperationOutcome(
                operation = SemanticOperationId.DATA_SAVER_SET_STATE,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = id,
                message = "Data Saver ${if (enabled) "enabled" else "disabled"}",
                metadata = mapOf("requestedEnabled" to enabled.toString())
            )
        } else {
            OperationOutcome.failed(
                SemanticOperationId.DATA_SAVER_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "Data Saver toggle is unavailable to this caller",
                strategy = id
            )
        }
    }

    // ---- helpers -----------------------------------------------------------

    private fun <T> service(clazz: Class<T>): T? = context.getSystemService(clazz)

    private fun canWriteSystemSettings(): Boolean =
        android.provider.Settings.System.canWrite(context)

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun notificationPolicyAccessGranted(): Boolean =
        service(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    private fun hasManageNetworkPolicyPermission(): Boolean =
        context.checkSelfPermission("android.permission.MANAGE_NETWORK_POLICY") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Public framework toggles retain exemptions for managed/system callers.
     * This check is intentionally authorization-only and never probes Root or
     * Shizuku, keeping public-strategy availability side-effect-free.
     */
    private fun isFrameworkPrivilegedCaller(): Boolean {
        val flags = context.applicationInfo.flags
        val systemApp =
            flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 ||
                flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        val policy = service(DevicePolicyManager::class.java)
        val managed = runCatching {
            policy?.isDeviceOwnerApp(context.packageName) == true ||
                policy?.isProfileOwnerApp(context.packageName) == true
        }.getOrDefault(false)
        return systemApp || managed
    }
}


internal fun publicWifiToggleAllowed(
    sdk: Int,
    frameworkPrivileged: Boolean
): Boolean = sdk < 29 || frameworkPrivileged

internal fun publicBluetoothToggleAllowed(
    sdk: Int,
    frameworkPrivileged: Boolean
): Boolean = sdk < 33 || frameworkPrivileged

