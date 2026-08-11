package com.nexaflow.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.compat.ChannelStatus
import com.nexaflow.core.compat.ChannelStatusMapper
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.compat.ChannelTier
import com.nexaflow.core.compat.ExecutionChannelSelector
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.rom.PermissionStatus
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

    // Save the backup directly to a local file (SAF create-document) — the
    // user picks the folder/name, then the JSON is written there.
    val stringBackupSaved = stringResource(R.string.backup_saved)
    val stringBackupSaveFailed = stringResource(R.string.backup_save_failed)
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.exportBackup { json ->
                    scope.launch {
                        val written = json != null && runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(json.toByteArray())
                            }
                        }.isSuccess
                        snackbarHostState.showSnackbar(
                            if (written) stringBackupSaved else stringBackupSaveFailed
                        )
                    }
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
                        icon = Icons.Filled.Save,
                        title = stringResource(R.string.backup_save),
                        subtitle = stringResource(R.string.backup_save_sub),
                        onClick = { saveBackupLauncher.launch("nexaflow_backup.json") }
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
                SectionHeader(text = stringResource(R.string.section_updates))
            }
            item {
                val updateViewModel: UpdateViewModel = hiltViewModel()
                val updateState by updateViewModel.state.collectAsState()
                NexaFlowCard {
                    when (val state = updateState) {
                        UpdateUiState.Idle -> SettingRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.update_check),
                            subtitle = stringResource(R.string.update_check_sub),
                            onClick = { updateViewModel.check() }
                        )
                        UpdateUiState.Checking -> SettingRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.update_checking),
                            subtitle = stringResource(R.string.update_check_sub)
                        )
                        is UpdateUiState.Latest -> SettingRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.update_latest),
                            subtitle = stringResource(R.string.update_latest_sub, state.info.version),
                            trailing = {
                                Text(
                                    text = stringResource(R.string.state_ok),
                                    color = Color(0xFF2FA84F),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                )
                            },
                            onClick = { updateViewModel.check() }
                        )
                        is UpdateUiState.Available -> {
                            SettingRow(
                                icon = Icons.Filled.SystemUpdate,
                                title = stringResource(R.string.update_available),
                                subtitle = stringResource(R.string.update_available_sub, state.info.version),
                                trailing = {
                                    Text(
                                        text = stringResource(R.string.state_update),
                                        color = Color(0xFF1E88E5),
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                    )
                                },
                                onClick = { updateViewModel.downloadAndInstall() }
                            )
                        }
                        UpdateUiState.Downloading -> SettingRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.update_downloading),
                            subtitle = stringResource(R.string.update_check_sub)
                        )
                        is UpdateUiState.Error -> SettingRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.update_error),
                            subtitle = stringResource(
                                when (state.message) {
                                    "update_no_apk" -> R.string.update_no_apk
                                    "update_download_failed" -> R.string.update_download_failed
                                    "update_install_failed" -> R.string.update_install_failed
                                    else -> R.string.update_check_failed
                                }
                            ),
                            onClick = { updateViewModel.check() }
                        )
                    }
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_privacy))
            }
            item {
                val privacyViewModel: PrivacyViewModel = hiltViewModel()
                val crashReporting by privacyViewModel.crashReportingEnabled.collectAsState()
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.crash_reporting),
                        subtitle = stringResource(R.string.crash_reporting_sub),
                        trailing = {
                            Switch(
                                checked = crashReporting,
                                onCheckedChange = { privacyViewModel.setCrashReportingEnabled(it) }
                            )
                        }
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.section_location))
            }
            item {
                val locationViewModel: LocationSettingsViewModel = hiltViewModel()
                val checkInterval by locationViewModel.checkIntervalMinutes.collectAsState()
                var showIntervalDialog by remember { mutableStateOf(false) }
                NexaFlowCard {
                    SettingRow(
                        icon = Icons.Filled.MyLocation,
                        title = stringResource(R.string.location_check_interval),
                        subtitle = stringResource(
                            if (checkInterval == 0) R.string.location_check_interval_manual_sub
                            else R.string.location_check_interval_auto_sub
                        ),
                        trailing = {
                            val (labelRes, arg) = locationIntervalLabel(checkInterval)
                            Text(
                                text = if (arg != null) {
                                    stringResource(labelRes, arg)
                                } else {
                                    stringResource(labelRes)
                                },
                                color = MaterialTheme.colorScheme.primary,
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                            )
                        },
                        onClick = { showIntervalDialog = true }
                    )
                }
                if (showIntervalDialog) {
                    LocationIntervalDialog(
                        selected = checkInterval,
                        onSelect = {
                            locationViewModel.setCheckIntervalMinutes(it)
                            showIntervalDialog = false
                        },
                        onDismiss = { showIntervalDialog = false }
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
                    SettingRow(
                        icon = Icons.Filled.Extension,
                        title = stringResource(R.string.plugins),
                        subtitle = stringResource(R.string.plugins_sub),
                        onClick = { navController.navigate("plugins") }
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
    fun isEnabled(context: Context): Boolean =
        PermissionStatus.isAccessibilityServiceEnabled(context)

    fun openSettings(context: Context) =
        PermissionStatus.openAccessibilitySettings(context)
}

/** String resource (+ optional format arg) for a check-interval in minutes. */
private fun locationIntervalLabel(minutes: Int): Pair<Int, Int?> = when (minutes) {
    15 -> R.string.location_check_15 to null
    30 -> R.string.location_check_30 to null
    60 -> R.string.location_check_60 to null
    180 -> R.string.location_check_180 to null
    360 -> R.string.location_check_360 to null
    0 -> R.string.location_check_manual to null
    // Any other positive value is a user-typed custom interval.
    else -> R.string.location_check_custom_minutes to minutes
}

/**
 * Interval picker: manual / 15m / 30m / 1h / 3h / 6h / custom — the custom
 * row lets the user type any number of minutes (e.g. 45), which the periodic
 * worker honors exactly as-is.
 */
@Composable
private fun LocationIntervalDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isCustom = selected !in LocationPreferences.PRESETS
    var editingCustom by remember { mutableStateOf(isCustom) }
    var customText by remember {
        mutableStateOf(if (isCustom && selected > 0) selected.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_check_title)) },
        text = {
            Column {
                LocationPreferences.PRESETS.forEachIndexed { index, minutes ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selected == minutes,
                            onClick = { onSelect(minutes) }
                        )
                        Text(
                            text = stringResource(
                                locationIntervalLabel(minutes).first
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingCustom = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = isCustom,
                        onClick = { editingCustom = true }
                    )
                    Text(
                        text = stringResource(R.string.location_check_custom),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (editingCustom) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.location_check_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Button(
                            onClick = {
                                customText.toIntOrNull()
                                    ?.takeIf { it >= 1 }
                                    ?.let { minutes ->
                                        onSelect(minutes)
                                        editingCustom = false
                                    }
                            }
                        ) {
                            Text(stringResource(R.string.location_check_apply))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    )
}

private fun appVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Throwable) {
        "1.0.0"
    }
}
