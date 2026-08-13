package com.nexaflow.core.rom

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.provider.Settings
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Auto-grants every permission the app needs through the elevated shell
 * (root, falling back to Shizuku when only that is granted).
 *
 * On a rooted device the user never has to tap through system permission
 * dialogs: runtime permissions go through `pm grant`, special-app-op
 * capabilities through `appops set ... allow`, battery exemption through
 * `dumpsys deviceidle whitelist`, and the accessibility service through
 * `settings put secure` (plain root can write secure settings via the
 * `settings` binary).
 *
 * Everything is best-effort: a permission that cannot be granted (already
 * granted, unsupported on this API level, denied by the system) is skipped
 * without failing the rest.
 */
object RootPermissionGranter {

    /** Package-qualified accessibility service component. */
    private const val ACCESSIBILITY_SERVICE =
        "com.nexaflow.app/com.nexaflow.core.engine.AppTriggerAccessibilityService"

    /** Result of one grant run. */
    data class Result(
        val runtimeGranted: List<String> = emptyList(),
        val appOpsGranted: List<String> = emptyList(),
        val secureSettingsWritten: List<String> = emptyList(),
        val batteryExempted: Boolean = false,
        val failures: List<String> = emptyList()
    ) {
        val anyGranted: Boolean
            get() = runtimeGranted.isNotEmpty() || appOpsGranted.isNotEmpty() ||
                secureSettingsWritten.isNotEmpty() || batteryExempted
    }

    /** True when an elevated shell (root or Shizuku) is available. */
    fun canAutoGrant(): Boolean =
        PrivilegedRunner.isRootAvailable() || PrivilegedRunner.isShizukuGranted()

    /**
     * Test seam: replaces [PrivilegedRunner.runShell] so unit tests can stub
     * shell responses without a real root/Shizuku environment.
     */
    internal var shellRunner: ((String) -> SystemControlResult)? = null

    /** Test seam: injects the package name (real: [Context.packageName]). */
    internal var packageNameProvider: (() -> String)? = null

    /** Test seam: injects the declared runtime permissions (real: manifest). */
    internal var permissionsProvider: (() -> List<String>)? = null

    /** Test seam: injects "is already granted" for a permission. */
    internal var grantedChecker: ((String) -> Boolean)? = null

    /** Test seam: injects the special app-ops list (real: [specialAppOps]). */
    internal var appOpsProvider: (() -> List<Pair<String, String>>)? = null

    /** Test seam: injects the battery-exemption check. */
    internal var batteryExemptChecker: (() -> Boolean)? = null

    /** Test seam: injects the accessibility-enabled check. */
    internal var accessibilityChecker: (() -> Boolean)? = null

    private fun runShell(command: String): SystemControlResult =
        shellRunner?.invoke(command) ?: PrivilegedRunner.runShell(command)

    /**
     * Grants everything the app declares. Runs shell commands synchronously
     * (each bounded by PrivilegedRunner's timeout) — call from a background
     * coroutine, never the main thread.
     */
    fun grantAll(context: Context): Result =
        grantAllInternal(packageNameProvider?.invoke() ?: context.packageName, context)

    /**
     * Pure grant pipeline: [packageName] is injected, and every Context-backed
     * lookup goes through the seams when set, so tests exercise the whole flow
     * on a plain JVM without Android.
     */
    internal fun grantAllInternal(
        packageName: String,
        context: Context? = null
    ): Result {
        if (!canAutoGrant()) {
            return Result(failures = listOf("No elevated runtime available"))
        }
        val runtimeGranted = mutableListOf<String>()
        val appOpsGranted = mutableListOf<String>()
        val secureSettingsWritten = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var batteryExempted = false

        // 1) Runtime (dangerous) permissions — `pm grant`.
        for (permission in permissionsProvider?.invoke() ?: context?.let { declaredRuntimePermissions(it) }.orEmpty()) {
            val alreadyGranted = grantedChecker?.invoke(permission)
                ?: context?.let { isRuntimeGranted(it, permission) } ?: false
            if (alreadyGranted) continue
            val outcome = runShell("pm grant $packageName $permission")
            if (outcome.success) runtimeGranted += permission
            else failures += "pm grant $permission"
        }

        // 2) Special capabilities exposed as app-ops.
        for ((permission, op) in appOpsProvider?.invoke() ?: specialAppOps()) {
            val alreadyGranted = grantedChecker?.invoke(permission)
                ?: context?.let { isRuntimeGranted(it, permission) } ?: false
            if (alreadyGranted) continue
            val outcome = runShell(
                "appops set $packageName $op allow"
            )
            if (outcome.success) appOpsGranted += permission
            else failures += "appops $op"
        }

        // 3) Battery optimization exemption.
        val batteryExemptedAlready = batteryExemptChecker?.invoke()
            ?: context?.let { isIgnoringBatteryOptimizations(it) } ?: false
        if (!batteryExemptedAlready) {
            val outcome = runShell(
                "dumpsys deviceidle whitelist +$packageName"
            )
            batteryExempted = outcome.success
            if (!outcome.success) failures += "deviceidle whitelist"
        }

        // 4) Accessibility service — needs secure settings (root can write
        //    them via the `settings` binary even without WRITE_SECURE_SETTINGS).
        val accessibilityAlreadyEnabled = accessibilityChecker?.invoke()
            ?: context?.let { isAccessibilityEnabled(it) } ?: false
        if (!accessibilityAlreadyEnabled) {
            val current = runShell(
                "settings get secure enabled_accessibility_services"
            ).message.trim().trimEnd(':', '\n')
            val newValue = if (current.isNotBlank() && current != "null") {
                "$current:$ACCESSIBILITY_SERVICE"
            } else ACCESSIBILITY_SERVICE
            val set = runShell(
                "settings put secure enabled_accessibility_services $newValue"
            )
            val enable = runShell(
                "settings put secure accessibility_enabled 1"
            )
            if (set.success && enable.success) {
                secureSettingsWritten += "enabled_accessibility_services"
            } else {
                failures += "accessibility settings"
            }
        }

        return Result(
            runtimeGranted = runtimeGranted,
            appOpsGranted = appOpsGranted,
            secureSettingsWritten = secureSettingsWritten,
            batteryExempted = batteryExempted,
            failures = failures
        )
    }

    /** All `dangerous` permissions the app declares in its merged manifest. */
    private fun declaredRuntimePermissions(context: Context): List<String> {
        return runCatching {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions
                ?.filter { p ->
                    val pi = context.packageManager.getPermissionInfo(p, 0)
                    (pi.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
                        PermissionInfo.PROTECTION_DANGEROUS
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun isRuntimeGranted(context: Context, permission: String): Boolean =
        runCatching {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** Special permissions granted through `appops set ... allow`. */
    internal fun specialAppOps(): List<Pair<String, String>> {
        val ops = mutableListOf<Pair<String, String>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ops += android.Manifest.permission.WRITE_SETTINGS to "android:write_settings"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ops += android.Manifest.permission.SYSTEM_ALERT_WINDOW to "android:system_alert_window"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ops += android.Manifest.permission.PACKAGE_USAGE_STATS to "android:get_usage_stats"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ops += android.Manifest.permission.ACCESS_NOTIFICATION_POLICY to
                "android:access_notification_policy"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ops += android.Manifest.permission.SCHEDULE_EXACT_ALARM to
                "android:schedule_exact_alarm"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ops += android.Manifest.permission.REQUEST_INSTALL_PACKAGES to
                "android:request_install_packages"
        }
        return ops
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    private fun isAccessibilityEnabled(context: Context): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        enabled.contains(ACCESSIBILITY_SERVICE)
    }.getOrDefault(false)
}
