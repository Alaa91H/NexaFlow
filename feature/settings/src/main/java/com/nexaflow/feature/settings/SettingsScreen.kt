package com.nexaflow.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.nexaflow.core.datastore.AppLanguageManager
import com.nexaflow.core.datastore.LocationPreferences
import com.nexaflow.core.compat.ChannelTier
import com.nexaflow.core.ui.NexaFlowAnimatedVisibility
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.CheckableRow
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.settingsGroup
import com.nexaflow.data.backup.ImportResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    val stringBackupImportFailed = stringResource(R.string.backup_import_failed)
    val stringShareBackupTitle = stringResource(R.string.share_backup_title)
    val stringBackupImportedTemplate = stringResource(R.string.backup_imported)
    val stringBackupImportedDisabledTemplate = stringResource(R.string.backup_imported_disabled)
    // Section titles hoisted because settingsGroup is a LazyListScope
    // extension (non-composable) and cannot call stringResource itself.
    val sectionAutomationTitle = stringResource(R.string.section_automation)
    val sectionBackupTitle = stringResource(R.string.section_backup)
    val sectionAppearanceTitle = stringResource(R.string.section_appearance)
    val sectionAboutTitle = stringResource(R.string.section_about)

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

    // One professional flow: write the backup to cache, then open the system
    // share sheet with that file. The sheet includes this app as a target
    // («Save locally») at the top, so a single tap exports *and* shares.
    val stringBackupSaveFailed = stringResource(R.string.backup_save_failed)
    val onExportShare: () -> Unit = {
        scope.launch {
            viewModel.exportBackup { json ->
                if (json == null) {
                    scope.launch { snackbarHostState.showSnackbar(stringBackupSaveFailed) }
                    return@exportBackup
                }
                val file = runCatching {
                    val dir = File(context.cacheDir, "backup").apply { mkdirs() }
                    File(dir, "nexaflow_backup.json").apply { writeText(json) }
                }.getOrNull()
                if (file == null) {
                    scope.launch { snackbarHostState.showSnackbar(stringBackupSaveFailed) }
                    return@exportBackup
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                // Share the cached file with any app.
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, stringShareBackupTitle)
                    putExtra(Intent.EXTRA_TEXT, stringShareBackupTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // «Save locally» pinned as the first target of the share sheet.
                // Built by class name: feature:settings cannot depend on the
                // app module, and the explicit component needs no import.
                val saveLocallyIntent = Intent().apply {
                    setClassName(context, "com.nexaflow.app.SaveBackupActivity")
                    action = "com.nexaflow.app.action.SAVE_BACKUP"
                    putExtra("extra_file_path", file.absolutePath)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(sendIntent, stringShareBackupTitle).apply {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveLocallyIntent))
                        }
                    )
                }.onFailure {
                    scope.launch { snackbarHostState.showSnackbar(stringBackupSaveFailed) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            val message = when (result) {
                is ImportResult.Success ->
                    if (result.disabledCount > 0) {
                        stringBackupImportedDisabledTemplate.format(result.count)
                    } else {
                        stringBackupImportedTemplate.format(result.count)
                    }
                ImportResult.InvalidFile ->
                    stringBackupImportFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    // ON_RESUME (replayed immediately when the observer is added while already
    // resumed) drives the initial detection too — no separate LaunchedEffect.

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
            settingsGroup(title = sectionAutomationTitle) {
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
            settingsGroup(title = sectionBackupTitle) {
                SettingRow(
                    icon = Icons.Filled.Upload,
                    title = stringResource(R.string.backup_export),
                    subtitle = stringResource(R.string.backup_export_sub),
                    onClick = onExportShare
                )
                SettingRow(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.backup_import),
                    subtitle = stringResource(R.string.backup_import_sub),
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/*", "*/*")) }
                )
            }
            item {
                SectionHeader(text = stringResource(R.string.section_updates))
            }
            item {
                val updateViewModel: UpdateViewModel = hiltViewModel()
                val updateState by updateViewModel.state.collectAsState()
                var updateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                // Auto-open the update dialog the moment a newer APK is found;
                // close it on success (Idle → installer) or failure (Error).
                LaunchedEffect(updateState) {
                    when (updateState) {
                        is UpdateUiState.Available -> updateDialogInfo = (updateState as UpdateUiState.Available).info
                        UpdateUiState.Idle, is UpdateUiState.Error -> updateDialogInfo = null
                        else -> Unit
                    }
                }
                updateDialogInfo?.let { info ->
                    UpdateDialog(
                        info = info,
                        downloading = updateState is UpdateUiState.Downloading,
                        onDownloadAndInstall = { updateViewModel.downloadAndInstall() },
                        onDismiss = { updateDialogInfo = null }
                    )
                }
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
                                    color = MaterialTheme.colorScheme.primary,
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
                                        color = MaterialTheme.colorScheme.primary,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                    )
                                },
                                onClick = { updateDialogInfo = state.info }
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
                            val (labelRes, arg1, arg2) = locationIntervalLabel(checkInterval)
                            Text(
                                text = when {
                                    arg1 != null && arg2 != null -> stringResource(labelRes, arg1, arg2)
                                    arg1 != null -> stringResource(labelRes, arg1)
                                    else -> stringResource(labelRes)
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
            settingsGroup(title = sectionAppearanceTitle) {
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.themes),
                    subtitle = stringResource(R.string.themes_sub),
                    onClick = { navController.navigate("themes") }
                )
                val selectedLanguage = AppLanguageManager.selectedLanguageTag(context)
                SettingRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.app_language),
                    subtitle = appLanguageDisplayName(
                        tag = selectedLanguage,
                        systemDefaultLabel = stringResource(R.string.app_language_system_default)
                    ),
                    onClick = { showLanguagePicker = true }
                )
                SettingRow(
                    icon = Icons.Filled.Widgets,
                    title = stringResource(R.string.widgets),
                    subtitle = stringResource(R.string.widgets_sub),
                    onClick = { navController.navigate("widgets") }
                )
            }
            if (showLanguagePicker) {
                AppLanguagePickerDialog(
                    selectedTag = AppLanguageManager.selectedLanguageTag(context),
                    onSelect = { tag ->
                        showLanguagePicker = false
                        // AndroidX applies the shared per-app locale and
                        // recreates the host when its configuration changes.
                        AppLanguageManager.setLanguage(context, tag)
                    },
                    onDismiss = { showLanguagePicker = false }
                )
            }
            settingsGroup(title = sectionAboutTitle) {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_nexaflow),
                    subtitle = stringResource(R.string.version, appVersion(context)),
                    onClick = { showAbout = true }
                )
            }
            item {
                // ── Hidden expert section ───────────────────────────
                // Advanced tools that don't matter to the average user stay
                // folded away at the bottom of Settings: execution log,
                // plugins. Tapping the header row expands them.
                var expertExpanded by rememberSaveable { mutableStateOf(false) }
                NexaFlowCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingRow(
                            icon = Icons.Filled.Build,
                            title = stringResource(R.string.expert_section_title),
                            subtitle = stringResource(R.string.expert_section_sub),
                            trailing = {
                                Icon(
                                    imageVector = if (expertExpanded) {
                                        Icons.Filled.KeyboardArrowUp
                                    } else {
                                        Icons.Filled.KeyboardArrowDown
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { expertExpanded = !expertExpanded }
                        )
                        NexaFlowAnimatedVisibility(visible = expertExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
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
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            // The settings module cannot reference the app module's resources,
            // so the brand name stays a literal (it is not translated anyway).
            title = { Text("NexaFlow") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.version, appVersion(context)))
                    Text(
                        text = stringResource(R.string.about_description),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_license),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // Developer contact & support links.
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.about_developer),
                        subtitle = DEVELOPER_HANDLE
                    )
                    SettingRow(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.about_github),
                        subtitle = stringResource(R.string.about_github_sub),
                        onClick = { openUrl(context, DEV_GITHUB_URL) }
                    )
                    SettingRow(
                        icon = Icons.Filled.Email,
                        title = stringResource(R.string.about_email),
                        subtitle = stringResource(R.string.about_email_sub),
                        onClick = { openEmail(context, DEV_EMAIL) }
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = stringResource(R.string.about_telegram),
                        subtitle = stringResource(R.string.about_telegram_sub),
                        onClick = { openUrl(context, DEV_TELEGRAM_URL) }
                    )
                    SettingRow(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.about_support),
                        subtitle = stringResource(R.string.about_support_sub),
                        onClick = { openUrl(context, DEV_KOFI_URL) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

private data class AppLanguageOption(
    val tag: String?,
    val nativeName: String
)

private val APP_LANGUAGE_OPTIONS = listOf(
    AppLanguageOption(null, ""),
    AppLanguageOption("en", "English"),
    AppLanguageOption("ar", "العربية"),
    AppLanguageOption("de", "Deutsch"),
    AppLanguageOption("es", "Español"),
    AppLanguageOption("fr", "Français"),
    AppLanguageOption("hi", "हिन्दी"),
    AppLanguageOption("ja", "日本語"),
    AppLanguageOption("pt", "Português"),
    AppLanguageOption("ru", "Русский"),
    AppLanguageOption("tr", "Türkçe"),
    AppLanguageOption("zh-CN", "简体中文")
)

private fun appLanguageDisplayName(tag: String?, systemDefaultLabel: String): String =
    if (tag == null) systemDefaultLabel
    else APP_LANGUAGE_OPTIONS.firstOrNull { it.tag == tag }?.nativeName ?: systemDefaultLabel

@Composable
private fun AppLanguagePickerDialog(
    selectedTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val systemDefaultLabel = stringResource(R.string.app_language_system_default)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                APP_LANGUAGE_OPTIONS.forEachIndexed { index, option ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val label = if (option.tag == null) systemDefaultLabel else option.nativeName
                    CheckableRow(
                        selected = selectedTag == option.tag,
                        onClick = { onSelect(option.tag) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {}
    )
}

// ── Developer info (About dialog) ────────────────────────────────

private const val DEVELOPER_HANDLE = "Alaa91H"
private const val DEV_GITHUB_URL = "https://github.com/Alaa91H"
private const val DEV_EMAIL = "alahus2591@gmail.com"
private const val DEV_TELEGRAM_URL = "https://t.me/Alaa91h"
private const val DEV_KOFI_URL = "https://ko-fi.com/alaa91h"

/** Opens [url] in the system browser (best-effort, no crash on missing handler). */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Opens the default mail app addressed to [address] (best-effort). */
private fun openEmail(context: Context, address: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }
}

/**
 * String resource (+ optional format args) for a check-interval in minutes.
 * Custom intervals render as hours:minutes when >= 1 hour, else plain minutes.
 */
private fun locationIntervalLabel(minutes: Int): Triple<Int, Int?, Int?> = when (minutes) {
    15 -> Triple(R.string.location_check_15, null, null)
    30 -> Triple(R.string.location_check_30, null, null)
    60 -> Triple(R.string.location_check_60, null, null)
    180 -> Triple(R.string.location_check_180, null, null)
    360 -> Triple(R.string.location_check_360, null, null)
    0 -> Triple(R.string.location_check_manual, null, null)
    // Any other positive value is a user-set custom interval.
    else -> when {
        minutes % 60 == 0 -> Triple(R.string.location_check_custom_hours, minutes / 60, null)
        minutes >= 60 -> Triple(R.string.location_check_custom_h_m, minutes / 60, minutes % 60)
        else -> Triple(R.string.location_check_custom_minutes, minutes, null)
    }
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
    // Locally selected option (radio). Picking never closes the dialog — the
    // user reviews the choice and confirms with Apply (or cancels).
    // picked == 0 means manual (custom) mode, backed by the counter below.
    var picked by remember { mutableStateOf(if (isCustom) 0 else selected) }
    val initialMinutes = if (isCustom && selected > 0) selected else 0
    var hours by remember { mutableStateOf(initialMinutes / 60) }
    var minutes by remember { mutableStateOf(initialMinutes % 60) }
    val total = hours * 60 + minutes
    // Switching to manual from a preset never applies an empty (0) interval.
    val pickManual: () -> Unit = {
        if (picked != 0 && total == 0) minutes = 30
        picked = 0
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_check_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Preset durations — Google-style single-choice rows: the
                // selected option carries a trailing checkmark.
                val presetOptions = LocationPreferences.PRESETS.filter { it > 0 }
                presetOptions.forEachIndexed { index, presetMinutes ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    CheckableRow(
                        selected = picked == presetMinutes,
                        onClick = { picked = presetMinutes }
                    ) {
                        Text(
                            text = stringResource(locationIntervalLabel(presetMinutes).first),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Manual option — sits above the counter it drives.
                CheckableRow(
                    selected = picked == 0,
                    onClick = { pickManual() }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.location_check_manual),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.location_check_custom),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // The hours:minutes counter is always visible — up/down buttons
                // AND drag-to-scroll on the value itself, grouped in a tonal
                // container. Time is read left-to-right (hours : minutes) even
                // in RTL locales, so the row is pinned to LTR.
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StepperCounter(
                                    value = hours,
                                    range = 0..23,
                                    label = stringResource(R.string.location_check_hours),
                                    onValueChange = { hours = it }
                                )
                                Text(
                                    text = ":",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 30.dp)
                                )
                                StepperCounter(
                                    value = minutes,
                                    range = 0..59,
                                    label = stringResource(R.string.location_check_minutes),
                                    onValueChange = { minutes = it }
                                )
                            }
                        }
                    }
                }
            }
        },
        // Apply saves the chosen option — the dialog only closes on Apply/
        // Cancel, never on a plain selection.
        confirmButton = {
            TextButton(
                onClick = { onSelect(if (picked == 0) total else picked) },
                enabled = true
            ) {
                Text(stringResource(R.string.location_check_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * A large value stepper column: up/down buttons plus drag-to-scroll on the
 * value itself (drag up increases, drag down decreases, ~28dp per step).
 * The displayed value wraps within [range] (e.g. minutes 0..59 stays put at
 * the edges; hours never exceed 23).
 */
@Composable
private fun StepperCounter(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val state = remember { mutableStateOf(value.coerceIn(range)) }
    // Re-sync when the parent resets the dialog or switches presets.
    LaunchedEffect(value) { state.value = value.coerceIn(range) }
    val change by rememberUpdatedState(onValueChange)
    val applyDelta = { delta: Int ->
        val next = (state.value + delta).coerceIn(range)
        if (next != state.value) {
            state.value = next
            change(next)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        IconButton(
            onClick = { applyDelta(+1) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.increase)
            )
        }
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .pointerInput(state, range) {
                    var accumulated = 0f
                    detectVerticalDragGestures { dragChange, dragAmount ->
                        dragChange.consume()
                        accumulated += dragAmount
                        val step = 28.dp.toPx()
                        var delta = 0
                        while (accumulated <= -step) {
                            accumulated += step
                            delta++
                        }
                        while (accumulated >= step) {
                            accumulated -= step
                            delta--
                        }
                        if (delta != 0) {
                            val next = (state.value + delta).coerceIn(range)
                            if (next != state.value) {
                                state.value = next
                                change(next)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Smooth value transition: digits slide/fade in the drag direction,
            // and tabular numerals keep the width fixed so nothing jitters.
            AnimatedContent(
                targetState = state.value,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically { it / 3 * direction } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 3 * direction } + fadeOut())
                },
                label = "counterValue"
            ) { currentValue ->
                Text(
                    text = currentValue.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        IconButton(
            onClick = { applyDelta(-1) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.decrease)
            )
        }
    }
}

private fun appVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Throwable) {
        "1.0.0"
    }
}
