package com.nexaflow.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.compat.ChannelStatus
import com.nexaflow.core.compat.ChannelStatusMapper
import com.nexaflow.core.compat.ChannelTier
import com.nexaflow.core.compat.ExecutionChannelSelector
import com.nexaflow.core.engine.AppTriggerAccessibilityService
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.data.backup.ImportResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var monitoringRunning by remember { mutableStateOf(false) }
    var channelStatus by remember { mutableStateOf<ChannelStatus?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val channelSelector = remember { ExecutionChannelSelector() }

    fun refreshChannelStatus() {
        // Single source of truth: the provider the selector picks for the live
        // profile is passed straight into the mapper.
        channelStatus = runCatching {
            val profile = channelSelector.detect(context)
            ChannelStatusMapper.map(
                provider = channelSelector.selectFor(profile),
                capabilityCount = profile.capabilities.size
            )
        }.getOrNull() ?: ChannelStatus.none()
    }
    val stringBackupImportFailed = stringResource(R.string.backup_import_failed)
    val stringBackupExportFailed = stringResource(R.string.backup_export_failed)
    val stringShareBackupTitle = stringResource(R.string.share_backup_title)
    val stringBackupImportedTemplate = stringResource(R.string.backup_imported)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (json.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(stringBackupImportFailed)
                } else {
                    viewModel.importBackup(json)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            val message = when (result) {
                is ImportResult.Success ->
                    stringBackupImportedTemplate.format(result.count)
                ImportResult.InvalidFile ->
                    stringBackupImportFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    fun shareBackup() {
        viewModel.exportBackup { json ->
            if (json == null) {
                scope.launch { snackbarHostState.showSnackbar(stringBackupExportFailed) }
                return@exportBackup
            }
            val result = runCatching {
                val dir = File(context.cacheDir, "backup").apply { mkdirs() }
                val file = File(dir, "nexaflow_backup.json")
                file.writeText(json)
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, stringShareBackupTitle)
                    putExtra(Intent.EXTRA_TEXT, stringShareBackupTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, stringShareBackupTitle))
            }
            if (result.isFailure) {
                scope.launch { snackbarHostState.showSnackbar(stringBackupExportFailed) }
            }
        }
    }

    // ON_RESUME (replayed immediately when the observer is added while already
    // resumed) drives the initial detection too — no separate LaunchedEffect.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = AccessibilityStatus.isEnabled(context)
                monitoringRunning = MonitoringService.isRunning
                refreshChannelStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { NexaFlowTopBar(title = stringResource(R.string.settings_title), onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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
                                text = if (accessibilityEnabled) stringResource(R.string.state_enabled) else stringResource(R.string.state_disabled),
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
                    SettingRow(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.permission_manager),
                        subtitle = stringResource(R.string.permission_manager_sub),
                        onClick = { navController.navigate("permission_manager") }
                    )
                    SettingRow(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.notification_manager),
                        subtitle = stringResource(R.string.notification_manager_sub),
                        onClick = { navController.navigate("notification_manager") }
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_backup))
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Upload,
                        title = stringResource(R.string.backup_export),
                        subtitle = stringResource(R.string.backup_export_sub),
                        onClick = { shareBackup() }
                    )
                    SettingRow(
                        icon = Icons.Filled.Download,
                        title = stringResource(R.string.backup_import),
                        subtitle = stringResource(R.string.backup_import_sub),
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/*", "*/*")) }
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
                    ChannelStatusRow(
                        status = channelStatus,
                        onRefresh = { refreshChannelStatus() }
                    )
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
