package com.nexaflow.core.rom

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Auto-grants every permission the app needs through the elevated shell
 * (root, falling back to Shizuku when only that is granted).
 *
 * On a rooted device the user never has to tap through system permission
 * dialogs: runtime permissions go through `pm grant`, special-app-op
 * capabilities through `appops set ... allow`, battery exemption through
 * `dumpsys deviceidle whitelist`, and the accessibility + notification
 * listener services through `settings put secure` (plain root can write
 * secure settings via the `settings` binary).
 *
 * [requestAndGrantAll] is the entry point the app uses: when a root manager
 * is installed but root was never granted yet, it first pops the manager's
 * one-tap allow dialog (30s grant window, the same mechanism Tasker/libsu
 * use), then runs the grant pipeline once the user answers. This is what
 * makes "root granted → everything granted" actually happen on a fresh
 * install instead of silently skipping because the availability probe timed
 * out while the dialog was still up.
 *
 * Everything is best-effort: a permission that cannot be granted (already
 * granted, unsupported on this API level, denied by the system) is skipped
 * without failing the rest, and a final verification pass reports what still
 * needs manual action via [Result.remaining].
 */
object RootPermissionGranter {

    /** Package-qualified accessibility service component. */
    private const val ACCESSIBILITY_SERVICE =
        "${PermissionStatus.ACCESSIBILITY_SERVICE_CLASS}"

    /** Package-qualified notification listener service component. */
    private const val NOTIFICATION_LISTENER =
        "${PermissionStatus.NOTIFICATION_LISTENER_CLASS}"

    /** Result of one grant run. */
    data class Result(
        val runtimeGranted: List<String> = emptyList(),
        val appOpsGranted: List<String> = emptyList(),
        val secureSettingsWritten: List<String> = emptyList(),
        val batteryExempted: Boolean = false,
        val notificationListenerGranted: Boolean = false,
        /** Verified still-missing capabilities after the grant pass. */
        val remaining: List<String> = emptyList(),
        val failures: List<String> = emptyList()
    ) {
        val anyGranted: Boolean
            get() = runtimeGranted.isNotEmpty() || appOpsGranted.isNotEmpty() ||
                secureSettingsWritten.isNotEmpty() || batteryExempted ||
                notificationListenerGranted

        val allGranted: Boolean
            get() = remaining.isEmpty() && failures.isEmpty()
    }

    /** True when an elevated shell (root or Shizuku) is available. */
    fun canAutoGrant(): Boolean =
        PrivilegedRunner.isRootAvailable() || PrivilegedRunner.isShizukuGranted()

    /**
     * Requests superuser access first when a root manager is installed but
     * root was never granted (the grant dialog shows and waits up to 30s for
     * the user), then grants every permission. On a device where root (or
     * Shizuku) is already available this runs [grantAll] immediately.
     *
     * Runs shell commands synchronously (each bounded by PrivilegedRunner's
     * timeout, the prompt itself up to 30s) — call from a background
     * coroutine, never the main thread.
     */
    fun requestAndGrantAll(context: Context): Result =
        requestAndGrantAllInternal { context.applicationContext.packageName }

    /**
     * Test seam: injects the package name (real: [Context.packageName]) so the
     * prompt-then-grant flow is exercisable on a plain JVM.
     */
    internal fun requestAndGrantAllInternal(packageNameProvider: () -> String): Result {
        // Root manager installed but not yet granted → pop the one-tap allow
        // dialog first so the launch-time auto-grant actually lands on a
        // fresh install instead of skipping silently.
        if (!PrivilegedRunner.isShizukuGranted() &&
            !PrivilegedRunner.isRootAvailable() &&
            SystemAppStatusDetector.isSuBinaryAvailable()
        ) {
            PrivilegedRunner.triggerSuPrompt()
            SystemAppStatusDetector.refreshRootAvailability()
        }
        return grantAllInternal(packageNameProvider())
    }

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

    /** Test seam: injects the notification-listener check. */
    internal var notificationListenerChecker: (() -> Boolean)? = null

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
        var notificationListenerGranted = false

        val declared = permissionsProvider?.invoke() ?: context?.let { declaredRuntimePermissions(it) }.orEmpty()

        // 1) Runtime (dangerous) permissions — `pm grant`.
        for (permission in declared) {
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
                ?: context?.let { isAppOpGranted(it, permission, op) } ?: false
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
                "$current:$packageName/$ACCESSIBILITY_SERVICE"
            } else "$packageName/$ACCESSIBILITY_SERVICE"
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

        // 5) Notification listener access — same secure-settings mechanism.
        val listenerAlreadyGranted = notificationListenerChecker?.invoke()
            ?: context?.let { isNotificationListenerGranted(it) } ?: false
        if (!listenerAlreadyGranted) {
            val current = runShell(
                "settings get secure enabled_notification_listeners"
            ).message.trim().trimEnd(':', '\n')
            val newValue = if (current.isNotBlank() && current != "null") {
                "$current:$packageName/$NOTIFICATION_LISTENER"
            } else "$packageName/$NOTIFICATION_LISTENER"
            val set = runShell(
                "settings put secure enabled_notification_listeners $newValue"
            )
            if (set.success) {
                notificationListenerGranted = true
                secureSettingsWritten += "enabled_notification_listeners"
            } else {
                failures += "notification listener settings"
            }
        }

        // 6) Verification pass — re-read the real state so the caller knows
        //    what actually landed and what still needs manual action.
        val remaining = mutableListOf<String>()
        for (permission in declared) {
            val granted = grantedChecker?.invoke(permission)
                ?: context?.let { isRuntimeGranted(it, permission) } ?: false
            if (!granted) remaining += "permission:$permission"
        }
        for ((permission, op) in appOpsProvider?.invoke() ?: specialAppOps()) {
            val granted = grantedChecker?.invoke(permission)
                ?: context?.let { isAppOpGranted(it, permission, op) } ?: false
            if (!granted) remaining += "appop:$op"
        }
        if (!(batteryExemptChecker?.invoke()
                ?: context?.let { isIgnoringBatteryOptimizations(it) } ?: false)
        ) {
            remaining += "battery_optimization"
        }
        if (!(accessibilityChecker?.invoke()
                ?: context?.let { isAccessibilityEnabled(it) } ?: false)
        ) {
            remaining += "accessibility_service"
        }
        if (!(notificationListenerChecker?.invoke()
                ?: context?.let { isNotificationListenerGranted(it) } ?: false)
        ) {
            remaining += "notification_listener"
        }

        return Result(
            runtimeGranted = runtimeGranted,
            appOpsGranted = appOpsGranted,
            secureSettingsWritten = secureSettingsWritten,
            batteryExempted = batteryExempted,
            notificationListenerGranted = notificationListenerGranted,
            remaining = remaining,
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
                    context.packageManager.getPermissionInfo(p, 0)
                        .isDangerousRuntimePermission()
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun PermissionInfo.isDangerousRuntimePermission(): Boolean {
        val protection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            protection
        } else {
            @Suppress("DEPRECATION")
            protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        return protection == PermissionInfo.PROTECTION_DANGEROUS
    }

    private fun isRuntimeGranted(context: Context, permission: String): Boolean =
        runCatching {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /**
     * True when a special permission (granted through app-ops by the shell)
     * is actually active, read back through the platform API.
     */
    private fun isAppOpGranted(context: Context, permission: String, op: String): Boolean =
        when (permission) {
            android.Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(context)
            android.Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
            android.Manifest.permission.ACCESS_NOTIFICATION_POLICY ->
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .isNotificationPolicyAccessGranted
            android.Manifest.permission.SCHEDULE_EXACT_ALARM -> {
                // Pre-S this permission doesn't exist (exact alarms are always
                // allowed); only check on S+.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms()
            }
            android.Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                context.packageManager.canRequestPackageInstalls()
            android.Manifest.permission.PACKAGE_USAGE_STATS ->
                isUsageStatsOpAllowed(context, op)
            else -> isRuntimeGranted(context, permission)
        }

    private fun isUsageStatsOpAllowed(context: Context, op: String): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 36) {
                checkOpNoThrowWithAttribution(appOps, context, op)
            } else {
                // The three-argument overload remains the compatible API through
                // Android 15. It is retained solely for pre-36 devices.
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, Process.myUid(), context.packageName)
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    @RequiresApi(36)
    private fun checkOpNoThrowWithAttribution(
        appOps: android.app.AppOpsManager,
        context: Context,
        op: String
    ): Int = appOps.checkOpNoThrow(
        op,
        Process.myUid(),
        context.packageName,
        context.attributionTag
    )

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

    private fun isAccessibilityEnabled(context: Context): Boolean =
        PermissionStatus.isAccessibilityServiceEnabled(context)

    private fun isNotificationListenerGranted(context: Context): Boolean =
        PermissionStatus.isNotificationListenerGranted(context)
}
