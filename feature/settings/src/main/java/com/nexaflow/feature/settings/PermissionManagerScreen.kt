package com.nexaflow.feature.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.nexaflow.core.engine.AppTriggerAccessibilityService
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow

private data class PermissionEntry(
    val key: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val isGranted: (Context) -> Boolean,
    val openAction: (Context) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }

    // Re-check permissions whenever the screen resumes (after returning from settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val entries = remember(refreshTick) { buildPermissionEntries() }

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
            item {
                SectionHeader(text = stringResource(R.string.section_automation))
            }
            item {
                NexaFlowCard {
                    entries.forEach { entry ->
                        val granted = entry.isGranted(context)
                        SettingRow(
                            icon = entry.icon,
                            title = stringResource(entry.titleRes),
                            subtitle = stringResource(entry.subtitleRes),
                            trailing = {
                                TextButton(onClick = { entry.openAction(context) }) {
                                    Text(
                                        text = stringResource(if (granted) R.string.granted else R.string.grant),
                                        color = if (granted) Color(0xFF2FA84F) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = { entry.openAction(context) }
                        )
                    }
                }
            }
        }
    }
}

private fun buildPermissionEntries(): List<PermissionEntry> {
    return listOf(
        PermissionEntry(
            key = "accessibility",
            titleRes = R.string.accessibility_service,
            subtitleRes = R.string.accessibility_disabled,
            icon = Icons.Filled.Accessibility,
            isGranted = { context -> isAccessibilityEnabled(context) },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
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
            }
        ),
        PermissionEntry(
            key = "notification_access",
            titleRes = R.string.notification_access,
            subtitleRes = R.string.notification_access_sub,
            icon = Icons.Filled.NotificationsActive,
            isGranted = { context ->
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.isNotificationListenerAccessGranted(ComponentName(context, com.nexaflow.core.engine.NotificationListener::class.java))
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        ),
        PermissionEntry(
            key = "sms",
            titleRes = R.string.sms_permission,
            subtitleRes = R.string.sms_permission_sub,
            icon = Icons.Filled.Sms,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        ),
        PermissionEntry(
            key = "bluetooth",
            titleRes = R.string.bluetooth_permission,
            subtitleRes = R.string.bluetooth_permission_sub,
            icon = Icons.Filled.Bluetooth,
            isGranted = { context ->
                if (Build.VERSION.SDK_INT >= 31) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else true
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        ),
        PermissionEntry(
            key = "location",
            titleRes = R.string.location_permission,
            subtitleRes = R.string.location_permission_sub,
            icon = Icons.Filled.LocationOn,
            isGranted = { context ->
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        ),
        PermissionEntry(
            key = "write_settings",
            titleRes = R.string.write_settings_permission,
            subtitleRes = R.string.write_settings_permission_sub,
            icon = Icons.Filled.Tune,
            isGranted = { context -> Settings.System.canWrite(context) },
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
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
            openAction = { context ->
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
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
            openAction = { context ->
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Throwable) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        )
    )
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AppTriggerAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
}
