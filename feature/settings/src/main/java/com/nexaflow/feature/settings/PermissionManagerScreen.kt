package com.nexaflow.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.nexaflow.core.execution.capability.AccessibilityCapabilityConsent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.rom.ElevatedAccessShortcuts
import com.nexaflow.core.rom.OemCompat
import com.nexaflow.core.rom.PermissionStatus
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow

internal data class PermissionEntry(
    val key: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val isGranted: (Context) -> Boolean,
    val openAction: (Context) -> Unit,
    /** Dangerous Android permissions this row can request directly. */
    val runtimePermissions: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }
    var showAccessibilityDisclosure by rememberSaveable { mutableStateOf(false) }
    var pendingRuntimePermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deniedRuntimePermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Re-read every row after Android's actual answer. A permission is only
        // shown as granted after the app process itself can verify it.
        val denied = result.filterValues { granted -> !granted }.keys
        deniedRuntimePermissions = deniedRuntimePermissions - pendingRuntimePermissions + denied
        pendingRuntimePermissions = emptySet()
        refreshTick++
    }

    // Re-check permissions whenever the screen resumes (after returning from settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val entries = remember(refreshTick) { buildPermissionEntries() }
    // Permission checks (the root probe can take up to ~3s) run off the main
    // thread; the map is recomputed whenever the screen resumes so a freshly
    // granted root is picked up.
    val grantedStates = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(entries, refreshTick) {
        val appContext = context.applicationContext
        val computed = withContext(Dispatchers.IO) {
            entries.associate { it.key to it.isGranted(appContext) }
        }
        grantedStates.clear()
        grantedStates.putAll(computed)
    }

    // OEM background-restriction guidance (MIUI/HyperOS, One UI, ColorOS,
    // OxygenOS): delivered through ONE channel per install. If the engine
    // notification already claimed the shared flag (OemCompat.isHintDelivered)
    // the card stays hidden; dismissing or acting on the card claims the flag
    // so the notification never fires later — the user is never alerted twice.
    val oemDeepLink = remember { OemCompat.autostartDeepLink(context) }
    val oemShown = remember { OemCompat.hasVendorAutostartGate() }
    var oemDismissed by rememberSaveable { mutableStateOf(false) }
    val showOemHint =
        oemShown && oemDeepLink != null && !OemCompat.isHintDelivered(context) && !oemDismissed

    // Only deliberate interaction (dismiss or acting) claims the shared hint:
    // marking it while merely rendering would flip showOemHint mid-frame on
    // the next recomposition and make the card vanish. A user who sees the
    // card without interacting keeps the hint available for the notification
    // channel later — never both at the same time.
    fun claimOemHint() {
        oemDismissed = true
        OemCompat.markHintDelivered(context)
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(text = stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(text = stringResource(R.string.accessibility_disclosure_message)) },
            confirmButton = {
                TextButton(onClick = {
                    // Explicit in-app acknowledgement is required in addition
                    // to the platform accessibility-service toggle.
                    AccessibilityCapabilityConsent.grant(context)
                    showAccessibilityDisclosure = false
                    PermissionStatus.openAccessibilitySettings(context)
                }) {
                    Text(text = stringResource(R.string.accessibility_disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    fun openPermission(entry: PermissionEntry) {
        if (entry.key == "root") {
            // Root availability only proves an elevated shell exists. Run the
            // complete verified grant pipeline every time the user taps this
            // row, so a previously approved Root grant also repairs any
            // Android runtime permissions still missing from the app UID.
            ElevatedAccessShortcuts.requestRootAccess(context) { granted ->
                if (granted) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            RootPermissionGranter.requestAndGrantAll(context.applicationContext)
                        }
                        refreshTick++
                    }
                } else {
                    ElevatedAccessShortcuts.openRootManager(context)
                }
            }
            return
        }
        if (entry.runtimePermissions.isEmpty() && !entry.isGranted(context) &&
            RootPermissionGranter.canAutoGrant()
        ) {
            // Special capabilities (app-ops, secure settings and battery
            // exemption) can also be repaired by the verified elevated
            // pipeline. Only fall back to a settings page if the OEM rejects
            // that privileged operation.
            scope.launch {
                withContext(Dispatchers.IO) {
                    RootPermissionGranter.grantAll(context.applicationContext)
                }
                refreshTick++
                if (!entry.isGranted(context)) {
                    entry.openAction(context)
                }
            }
            return
        }
        val missingRuntimePermissions = entry.runtimePermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingRuntimePermissions.isNotEmpty()) {
            if (RootPermissionGranter.canAutoGrant()) {
                // Root/Shizuku commands run as an elevated shell, whereas
                // framework APIs run as NexaFlow's UID. `pm grant` bridges that
                // boundary, then RootPermissionGranter verifies the UID state.
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        RootPermissionGranter.grantRuntimePermissions(
                            context.applicationContext,
                            missingRuntimePermissions
                        )
                    }
                    refreshTick++
                    if (result.remaining.isNotEmpty()) {
                        // A ROM may block an elevated `pm grant`; fall back to
                        // Android's own dialog only when it has not already
                        // been denied during this screen visit.
                        if (result.remaining.any { it in deniedRuntimePermissions }) {
                            entry.openAction(context)
                        } else {
                            pendingRuntimePermissions = result.remaining.toSet()
                            runtimePermissionLauncher.launch(result.remaining.toTypedArray())
                        }
                    }
                }
            } else if (missingRuntimePermissions.any { it in deniedRuntimePermissions }) {
                // Android suppressed a repeat dialog after denial; App info is
                // the reliable recovery path for the affected permission group.
                entry.openAction(context)
            } else {
                pendingRuntimePermissions = missingRuntimePermissions.toSet()
                runtimePermissionLauncher.launch(missingRuntimePermissions.toTypedArray())
            }
        } else if (entry.key == "accessibility" && !isAccessibilityEnabled(context)) {
            showAccessibilityDisclosure = true
        } else {
            entry.openAction(context)
        }
    }

    Scaffold(
        topBar = { NexaFlowTopBar(title = stringResource(R.string.permission_manager), onBack = { navController.popBackStack() }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.permission_manager_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (showOemHint) {
                item {
                    NexaFlowCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.oem_compat_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.oem_compat_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { claimOemHint() }) {
                                Text(text = stringResource(R.string.dismiss))
                            }
                            TextButton(onClick = {
                                claimOemHint()
                                context.startActivity(
                                    oemDeepLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }) {
                                Text(
                                    text = stringResource(R.string.oem_compat_action),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_automation))
            }
            item {
                NexaFlowCard(alternatingIndex = 0) {
                    entries.forEachIndexed { index, entry ->
                        val granted = grantedStates[entry.key] ?: false
                        SettingRow(
                            icon = entry.icon,
                            title = stringResource(entry.titleRes),
                            subtitle = stringResource(entry.subtitleRes),
                            alternatingIndex = index,
                            trailing = {
                                TextButton(onClick = { openPermission(entry) }) {
                                    Text(
                                        text = stringResource(if (granted) R.string.granted else R.string.grant),
                                        color = if (granted) Color(0xFF006D3C) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = { openPermission(entry) }
                        )
                    }
                }
            }
        }
    }
}

internal fun buildPermissionEntries(): List<PermissionEntry> {
    return listOf(
        PermissionEntry(
            key = "accessibility",
            titleRes = R.string.accessibility_service,
            subtitleRes = R.string.accessibility_disabled,
            icon = Icons.Filled.Accessibility,
            isGranted = { context -> isAccessibilityEnabled(context) },
            openAction = { context -> PermissionStatus.openAccessibilitySettings(context) }
        ),
        PermissionEntry(
            key = "camera",
            titleRes = R.string.camera_permission,
            subtitleRes = R.string.camera_permission_sub,
            icon = Icons.Filled.Info,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = listOf(Manifest.permission.CAMERA)
        ),
        PermissionEntry(
            key = "notifications",
            titleRes = R.string.notifications_permission,
            subtitleRes = R.string.notifications_permission_sub,
            icon = Icons.Filled.Notifications,
            isGranted = { context ->
                if (Build.VERSION.SDK_INT >= 33) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else emptyList()
        ),
        PermissionEntry(
            key = "notification_access",
            titleRes = R.string.notification_access,
            subtitleRes = R.string.notification_access_sub,
            icon = Icons.Filled.NotificationsActive,
            isGranted = { context -> PermissionStatus.isNotificationListenerGranted(context) },
            openAction = { context -> PermissionStatus.openNotificationAccessSettings(context) }
        ),
        PermissionEntry(
            key = "sms",
            titleRes = R.string.sms_permission,
            subtitleRes = R.string.sms_permission_sub,
            icon = Icons.Filled.Sms,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        ),
        PermissionEntry(
            key = "bluetooth",
            titleRes = R.string.bluetooth_permission,
            subtitleRes = R.string.bluetooth_permission_sub,
            icon = Icons.Filled.Bluetooth,
            isGranted = { context ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else emptyList()
        ),
        PermissionEntry(
            key = "location",
            titleRes = R.string.location_permission,
            subtitleRes = R.string.location_permission_sub,
            icon = Icons.Filled.LocationOn,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ),
        PermissionEntry(
            key = "phone_state",
            titleRes = R.string.phone_state_permission,
            subtitleRes = R.string.phone_state_permission_sub,
            icon = Icons.Filled.SignalCellularAlt,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
            },
            // App details remains the correct destination once Android has
            // permanently denied a runtime permission at platform level.
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = listOf(Manifest.permission.READ_PHONE_STATE)
        ),
        PermissionEntry(
            key = "calendar",
            titleRes = R.string.calendar_permission,
            subtitleRes = R.string.calendar_permission_sub,
            icon = Icons.Filled.DateRange,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = listOf(Manifest.permission.READ_CALENDAR)
        ),
        PermissionEntry(
            key = "activity_recognition",
            titleRes = R.string.activity_recognition_permission,
            subtitleRes = R.string.activity_recognition_permission_sub,
            icon = Icons.Filled.Accessibility,
            isGranted = { context ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                    PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(Manifest.permission.ACTIVITY_RECOGNITION)
            } else emptyList()
        ),
        PermissionEntry(
            key = "local_network",
            titleRes = R.string.local_network_permission,
            subtitleRes = R.string.local_network_permission_sub,
            icon = Icons.Filled.SignalCellularAlt,
            isGranted = { context ->
                Build.VERSION.SDK_INT < 37 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                    PackageManager.PERMISSION_GRANTED
            },
            openAction = { context -> PermissionStatus.openAppDetails(context) },
            runtimePermissions = if (Build.VERSION.SDK_INT >= 37) {
                listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else emptyList()
        ),
        PermissionEntry(
            key = "root",
            titleRes = R.string.root_permission,
            subtitleRes = R.string.root_permission_sub,
            icon = Icons.Filled.Terminal,
            isGranted = { PrivilegedRunner.isRootAvailable() },
            // Request root through the root manager's grant dialog (one tap),
            // never through app info. Once granted, every permission the app
            // needs is granted automatically through the elevated shell — the
            // dedicated "grant all" card is gone because this is now the flow.
            openAction = { context ->
                ElevatedAccessShortcuts.requestRootAccess(context) { granted ->
                    if (granted) {
                        Thread {
                            RootPermissionGranter.requestAndGrantAll(context.applicationContext)
                        }.start()
                    } else {
                        ElevatedAccessShortcuts.openRootManager(context)
                    }
                }
            }
        ),
        PermissionEntry(
            key = "shizuku",
            titleRes = R.string.shizuku_permission,
            subtitleRes = R.string.shizuku_permission_sub,
            icon = Icons.Filled.Terminal,
            isGranted = { PrivilegedRunner.isShizukuGranted() },
            openAction = { context -> ElevatedAccessShortcuts.openShizuku(context) }
        ),
        PermissionEntry(
            key = "write_settings",
            titleRes = R.string.write_settings_permission,
            subtitleRes = R.string.write_settings_permission_sub,
            icon = Icons.Filled.Tune,
            isGranted = { context -> Settings.System.canWrite(context) },
            openAction = { context -> PermissionStatus.openWriteSettings(context) }
        ),
        PermissionEntry(
            key = "dnd",
            titleRes = R.string.dnd_access,
            subtitleRes = R.string.dnd_access_sub,
            icon = Icons.Filled.Notifications,
            isGranted = { context ->
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.isNotificationPolicyAccessGranted
            },
            openAction = { context -> PermissionStatus.openNotificationPolicy(context) }
        ),
        PermissionEntry(
            key = "exact_alarms",
            titleRes = R.string.exact_alarms,
            subtitleRes = R.string.exact_alarms_sub,
            icon = Icons.Filled.Schedule,
            isGranted = { context ->
                if (Build.VERSION.SDK_INT >= 31) {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.canScheduleExactAlarms()
                } else true
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        ),
        PermissionEntry(
            key = "battery_opt",
            titleRes = R.string.battery_optimization,
            subtitleRes = R.string.battery_optimization_sub,
            icon = Icons.Filled.BatteryAlert,
            isGranted = { context ->
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            },
            openAction = { context -> ElevatedAccessShortcuts.requestBatteryOptimizationExemption(context) }
        )
    )
}

private fun isAccessibilityEnabled(context: Context): Boolean =
    PermissionStatus.isAccessibilityServiceEnabled(context)
