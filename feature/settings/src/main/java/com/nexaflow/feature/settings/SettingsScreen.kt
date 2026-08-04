package com.nexaflow.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.nexaflow.core.engine.AppTriggerAccessibilityService
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var monitoringRunning by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = AccessibilityStatus.isEnabled(context)
                monitoringRunning = MonitoringService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.settings_title), onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                SectionHeader(text = stringResource(R.string.section_automation))
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Accessibility,
                        title = stringResource(R.string.accessibility_service),
                        subtitle = if (accessibilityEnabled) stringResource(R.string.accessibility_enabled) else stringResource(R.string.accessibility_disabled),
                        trailing = {
                            Text(
                                text = if (accessibilityEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                                color = if (accessibilityEnabled) Color(0xFF2FA84F) else Color(0xFFE5533D),
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                            )
                        },
                        onClick = { AccessibilityStatus.openSettings(context) }
                    )
                    SettingRow(
                        icon = Icons.Filled.MonitorHeart,
                        title = stringResource(R.string.monitoring_service),
                        subtitle = if (monitoringRunning) stringResource(R.string.monitoring_running) else stringResource(R.string.monitoring_stopped),
                        trailing = {
                            Switch(
                                checked = monitoringRunning,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        MonitoringService.start(context)
                                    } else {
                                        MonitoringService.stop(context)
                                    }
                                    monitoringRunning = checked
                                }
                            )
                        }
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_appearance))
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.themes),
                        subtitle = stringResource(R.string.themes_sub),
                        onClick = { navController.navigate("themes") }
                    )
                    SettingRow(
                        icon = Icons.Filled.Widgets,
                        title = stringResource(R.string.widgets),
                        subtitle = stringResource(R.string.widgets_sub),
                        onClick = { navController.navigate("widgets") }
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_integration))
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.capability_center),
                        subtitle = stringResource(R.string.capability_center_sub),
                        onClick = { navController.navigate("capability_center") }
                    )
                    SettingRow(
                        icon = Icons.Filled.PlayArrow,
                        title = stringResource(R.string.execution_history),
                        subtitle = stringResource(R.string.execution_history_sub),
                        onClick = { navController.navigate("history") }
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_about))
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.about_nexaflow),
                        subtitle = stringResource(R.string.version, appVersion(context)),
                        onClick = { showAbout = true }
                    )
                    SettingRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.rom_integration),
                        subtitle = stringResource(R.string.rom_integration_sub),
                        onClick = { navController.navigate("capability_center") }
                    )
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("NexaFlow") },
            text = {
                Column {
                    Text(stringResource(R.string.version, appVersion(context)))
                    Text(
                        text = stringResource(R.string.about_description),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_license),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

private object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppTriggerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun appVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Throwable) {
        "1.0.0"
    }
}
