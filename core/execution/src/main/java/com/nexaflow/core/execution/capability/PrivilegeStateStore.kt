package com.nexaflow.core.execution.capability

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.database.ContentObserver
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexaflow.core.rom.PermissionStatus
import com.nexaflow.core.rom.PrivilegeStateEvents
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.domain.capability.PrivilegeGrantState
import com.nexaflow.domain.capability.PrivilegeObservation
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Read-only source for one coherent authorization snapshot. */
fun interface PrivilegeStateProbe {
    suspend fun capture(observedAtMs: Long): PrivilegeSnapshot
}

/**
 * Event source for permission/authority changes. Events are invalidation hints
 * only; the store always re-reads the real platform state before publishing.
 */
interface PrivilegeStateEventSource {
    fun start(onChanged: () -> Unit)
    fun stop()
}

/**
 * Unified live state for Android runtime permissions, AppOps, special access,
 * Shizuku, Root and Device Owner authority.
 *
 * Expensive probes are coalesced and run off-main. The flow never fabricates a
 * grant from an event: every event schedules a complete read-back.
 */
class PrivilegeStateStore(
    private val scope: CoroutineScope,
    private val probe: PrivilegeStateProbe,
    private val eventSource: PrivilegeStateEventSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val minRefreshIntervalMs: Long = DEFAULT_MIN_REFRESH_INTERVAL_MS
) {
    constructor(
        context: Context,
        scope: CoroutineScope
    ) : this(
        scope = scope,
        probe = AndroidPrivilegeStateProbe(context.applicationContext),
        eventSource = AndroidPrivilegeStateEventSource(context.applicationContext)
    )
    private val refreshMutex = Mutex()
    private val schedulerLock = Any()
    private val _snapshot = MutableStateFlow(PrivilegeSnapshot())
    private var refreshQueued = false
    private var workerJob: Job? = null
    @Volatile private var lastRefreshCompletedAtMs = Long.MIN_VALUE

    val snapshot: StateFlow<PrivilegeSnapshot> = _snapshot.asStateFlow()

    init {
        eventSource.start(::invalidate)
        invalidate()
    }

    fun invalidate() {
        synchronized(schedulerLock) {
            refreshQueued = true
            if (workerJob != null) return
            workerJob = scope.launch {
                try {
                    while (true) {
                        synchronized(schedulerLock) {
                            if (!refreshQueued) return@launch
                            refreshQueued = false
                        }
                        val wait = refreshDelayMs(nowMs())
                        if (wait > 0L) delay(wait)
                        try {
                            refreshNow()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // Keep the last coherent snapshot. One broken OEM
                            // probe must not erase previously verified state.
                        }
                    }
                } finally {
                    val restart = synchronized(schedulerLock) {
                        workerJob = null
                        refreshQueued
                    }
                    if (restart) invalidate()
                }
            }
        }
    }

    fun refresh() = invalidate()

    suspend fun freshSnapshot(
        budgetMs: Long = DEFAULT_FRESH_SNAPSHOT_BUDGET_MS
    ): PrivilegeSnapshot {
        val before = _snapshot.value.observedAtMs
        invalidate()
        val deadline = nowMs() + budgetMs
        var current = _snapshot.value
        while (current.observedAtMs == before && nowMs() < deadline) {
            delay(FRESH_SNAPSHOT_POLL_MS)
            current = _snapshot.value
        }
        return current
    }

    fun close() {
        eventSource.stop()
        synchronized(schedulerLock) {
            workerJob?.cancel()
            workerJob = null
            refreshQueued = false
        }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val observedAt = nowMs()
        val captured = withContext(dispatcher) { probe.capture(observedAt) }
        _snapshot.value = if (captured.observedAtMs == observedAt) {
            captured
        } else {
            captured.copy(observedAtMs = observedAt)
        }
        lastRefreshCompletedAtMs = observedAt
    }

    private fun refreshDelayMs(now: Long): Long {
        if (lastRefreshCompletedAtMs == Long.MIN_VALUE) return 0L
        val elapsed = (now - lastRefreshCompletedAtMs).coerceAtLeast(0L)
        return (minRefreshIntervalMs - elapsed).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_MIN_REFRESH_INTERVAL_MS = 500L
        const val DEFAULT_FRESH_SNAPSHOT_BUDGET_MS = 4_000L
        private const val FRESH_SNAPSHOT_POLL_MS = 25L
    }
}

/** Android implementation of the unified authorization probe. */
class AndroidPrivilegeStateProbe(
    context: Context
) : PrivilegeStateProbe {
    private val appContext = context.applicationContext

    override suspend fun capture(observedAtMs: Long): PrivilegeSnapshot {
        val declaredPermissions = declaredPermissions()
        val runtimePermissions = declaredPermissions.filter(::isDangerousPermission)
        val nonRuntimePermissions = declaredPermissions.filterNot(::isDangerousPermission)
        val observations = buildList {
            addAll(androidPermissionObservations(nonRuntimePermissions))
            addAll(runtimePermissionObservations(runtimePermissions))
            addAll(appOpObservations(runtimePermissions))
            addAll(specialAccessObservations())
            add(shizukuObservation())
            add(rootObservation())
            add(deviceOwnerObservation())
        }
        return PrivilegeSnapshot(
            observations = observations.distinctBy { it.surface to it.key },
            observedAtMs = observedAtMs
        )
    }

    private fun androidPermissionObservations(
        permissions: List<String>
    ): List<PrivilegeObservation> = permissions.map { permission ->
        val granted = runCatching {
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        observation(
            PrivilegeSurface.ANDROID_PERMISSION,
            permission,
            if (granted) PrivilegeGrantState.GRANTED else PrivilegeGrantState.NOT_GRANTED,
            if (granted) "ANDROID_PERMISSION_GRANTED" else "ANDROID_PERMISSION_NOT_GRANTED"
        )
    }

    private fun runtimePermissionObservations(
        permissions: List<String>
    ): List<PrivilegeObservation> = permissions.map { permission ->
        val granted = runCatching {
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        observation(
            PrivilegeSurface.RUNTIME_PERMISSION,
            permission,
            if (granted) PrivilegeGrantState.GRANTED else PrivilegeGrantState.NOT_GRANTED,
            if (granted) "ANDROID_RUNTIME_GRANTED" else "ANDROID_RUNTIME_NOT_GRANTED"
        )
    }

    private fun appOpObservations(
        runtimePermissions: List<String>
    ): List<PrivilegeObservation> {
        val manager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return watchedAppOps(runtimePermissions).map { op ->
            val mode = runCatching {
                @Suppress("DEPRECATION")
                manager.checkOpNoThrow(op, Process.myUid(), appContext.packageName)
            }.getOrNull()
            val state = when (mode) {
                AppOpsManager.MODE_ALLOWED -> PrivilegeGrantState.GRANTED
                AppOpsManager.MODE_FOREGROUND -> PrivilegeGrantState.PARTIAL
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED -> PrivilegeGrantState.NOT_GRANTED
                AppOpsManager.MODE_DEFAULT -> PrivilegeGrantState.UNKNOWN
                else -> PrivilegeGrantState.UNKNOWN
            }
            observation(
                PrivilegeSurface.APP_OP,
                op,
                state,
                when (mode) {
                    AppOpsManager.MODE_ALLOWED -> "APP_OP_ALLOWED"
                    AppOpsManager.MODE_FOREGROUND -> "APP_OP_FOREGROUND_ONLY"
                    AppOpsManager.MODE_IGNORED -> "APP_OP_IGNORED"
                    AppOpsManager.MODE_ERRORED -> "APP_OP_ERRORED"
                    AppOpsManager.MODE_DEFAULT -> "APP_OP_DEFAULT"
                    null -> "APP_OP_PROBE_FAILED"
                    else -> "APP_OP_UNKNOWN_MODE"
                }
            )
        }
    }

    private fun specialAccessObservations(): List<PrivilegeObservation> {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        return listOf(
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_ACCESSIBILITY_SERVICE,
                runCatching { PermissionStatus.isAccessibilityServiceEnabled(appContext) }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_NOTIFICATION_LISTENER,
                runCatching { PermissionStatus.isNotificationListenerGranted(appContext) }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS,
                runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_DRAW_OVERLAYS,
                runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_DND_POLICY,
                runCatching { notificationManager.isNotificationPolicyAccessGranted }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_EXACT_ALARM,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_INSTALL_PACKAGES,
                runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
            ),
            booleanObservation(
                PrivilegeSnapshot.SPECIAL_BATTERY_OPTIMIZATION,
                runCatching {
                    powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
                }.getOrDefault(false)
            )
        )
    }

    private fun shizukuObservation(): PrivilegeObservation {
        val installed = runCatching {
            SystemAppStatusDetector.isShizukuAvailable(appContext)
        }.getOrDefault(false)
        if (!installed) {
            return observation(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU,
                PrivilegeGrantState.NOT_INSTALLED,
                "SHIZUKU_NOT_INSTALLED"
            )
        }

        val running = runCatching { PrivilegedRunner.isShizukuRunning() }.getOrDefault(false)
        if (!running) {
            return observation(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU,
                PrivilegeGrantState.NOT_RUNNING,
                "SHIZUKU_SERVER_NOT_RUNNING"
            )
        }

        val granted = runCatching { PrivilegedRunner.isShizukuGranted() }.getOrDefault(false)
        if (!granted) {
            return observation(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU,
                PrivilegeGrantState.PERMISSION_REQUIRED,
                "SHIZUKU_PERMISSION_REQUIRED"
            )
        }

        val bound = runCatching { ShizukuShellBridge.isUserServiceBound }.getOrDefault(false)
        return observation(
            PrivilegeSurface.SHIZUKU,
            PrivilegeSnapshot.ENV_SHIZUKU,
            if (bound) PrivilegeGrantState.GRANTED else PrivilegeGrantState.SERVICE_UNAVAILABLE,
            if (bound) "SHIZUKU_USER_SERVICE_READY" else "SHIZUKU_USER_SERVICE_UNAVAILABLE"
        )
    }

    private fun rootObservation(): PrivilegeObservation {
        val available = runCatching { PrivilegedRunner.isRootAvailable() }.getOrDefault(false)
        if (available) {
            return observation(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT,
                PrivilegeGrantState.GRANTED,
                "ROOT_UID_ZERO_VERIFIED"
            )
        }
        val binary = runCatching {
            SystemAppStatusDetector.isSuBinaryAvailable()
        }.getOrDefault(false)
        return observation(
            PrivilegeSurface.ROOT,
            PrivilegeSnapshot.ENV_ROOT,
            if (binary) PrivilegeGrantState.PERMISSION_REQUIRED else PrivilegeGrantState.NOT_INSTALLED,
            if (binary) "ROOT_GRANT_REQUIRED_OR_DENIED" else "ROOT_BINARY_NOT_FOUND"
        )
    }

    private fun deviceOwnerObservation(): PrivilegeObservation {
        val manager = appContext.getSystemService(DevicePolicyManager::class.java)
        val owner = runCatching {
            manager.isDeviceOwnerApp(appContext.packageName)
        }.getOrDefault(false)
        return observation(
            PrivilegeSurface.DEVICE_OWNER,
            PrivilegeSnapshot.ENV_DEVICE_OWNER,
            if (owner) PrivilegeGrantState.GRANTED else PrivilegeGrantState.NOT_GRANTED,
            if (owner) "DEVICE_OWNER_ACTIVE" else "DEVICE_OWNER_NOT_ACTIVE"
        )
    }

    private fun booleanObservation(key: String, granted: Boolean): PrivilegeObservation =
        observation(
            PrivilegeSurface.SPECIAL_ACCESS,
            key,
            if (granted) PrivilegeGrantState.GRANTED else PrivilegeGrantState.NOT_GRANTED,
            if (granted) "SPECIAL_ACCESS_GRANTED" else "SPECIAL_ACCESS_NOT_GRANTED"
        )

    private fun observation(
        surface: PrivilegeSurface,
        key: String,
        state: PrivilegeGrantState,
        detailCode: String
    ) = PrivilegeObservation(surface, key, state, detailCode)

    private fun declaredPermissions(): List<String> = runCatching {
        appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty().distinct()
    }.getOrDefault(emptyList())

    private fun isDangerousPermission(permission: String): Boolean = runCatching {
        val permissionInfo = appContext.packageManager.getPermissionInfo(permission, 0)
        val protection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionInfo.protection
        } else {
            @Suppress("DEPRECATION")
            permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        protection == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)

    companion object {
        private val SPECIAL_APP_OPS = setOf(
            "android:write_settings",
            "android:system_alert_window",
            "android:get_usage_stats",
            "android:schedule_exact_alarm",
            "android:request_install_packages",
            "android:access_notification_policy"
        )

        fun watchedAppOps(runtimePermissions: Collection<String>): Set<String> = buildSet {
            addAll(SPECIAL_APP_OPS)
            runtimePermissions.forEach { permission ->
                AppOpsManager.permissionToOp(permission)?.let(::add)
            }
        }
    }
}

/** Real Android invalidation hooks for [PrivilegeStateStore]. */
class AndroidPrivilegeStateEventSource(
    context: Context
) : PrivilegeStateEventSource {
    private val appContext = context.applicationContext
    private var onChanged: (() -> Unit)? = null
    private var started = false

    private val permissionListener = PackageManager.OnPermissionsChangedListener { uid ->
        if (uid == Process.myUid()) onChanged?.invoke()
    }

    private val appOpsListener = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == null || packageName == appContext.packageName) {
            onChanged?.invoke()
        }
    }

    private val secureSettingsObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            onChanged?.invoke()
        }
    }

    private val permissionBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onChanged?.invoke()
        }
    }

    private val shizukuListener: () -> Unit = { onChanged?.invoke() }
    private val internalPrivilegeListener: () -> Unit = { onChanged?.invoke() }

    override fun start(onChanged: () -> Unit) {
        if (started) return
        started = true
        this.onChanged = onChanged

        runCatching {
            appContext.packageManager.addOnPermissionsChangeListener(permissionListener)
        }

        runCatching {
            val permissions = appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_PERMISSIONS
            ).requestedPermissions.orEmpty()
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            AndroidPrivilegeStateProbe.watchedAppOps(permissions).forEach { op ->
                runCatching {
                    appOps.startWatchingMode(op, appContext.packageName, appOpsListener)
                }
            }
        }

        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                secureSettingsObserver
            )
            appContext.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("enabled_notification_listeners"),
                false,
                secureSettingsObserver
            )
        }

        runCatching {
            val filter = IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                filter.addAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                appContext,
                permissionBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        ShizukuShellBridge.addStateListener(shizukuListener)
        PrivilegeStateEvents.addListener(internalPrivilegeListener)
    }

    override fun stop() {
        if (!started) return
        started = false

        runCatching {
            appContext.packageManager.removeOnPermissionsChangeListener(permissionListener)
        }
        runCatching {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.stopWatchingMode(appOpsListener)
        }
        runCatching {
            appContext.contentResolver.unregisterContentObserver(secureSettingsObserver)
        }
        runCatching {
            appContext.unregisterReceiver(permissionBroadcastReceiver)
        }
        ShizukuShellBridge.removeStateListener(shizukuListener)
        PrivilegeStateEvents.removeListener(internalPrivilegeListener)
        onChanged = null
    }
}
