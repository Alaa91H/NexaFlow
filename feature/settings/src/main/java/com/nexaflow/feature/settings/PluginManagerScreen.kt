package com.nexaflow.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.execution.plugin.PluginFireClient
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginDescriptor
import com.nexaflow.core.pluginsdk.PluginType
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.toImageBitmapOrNull
import com.nexaflow.domain.models.PluginInfo
import kotlinx.coroutines.launch

/**
 * A local catalog of recommended Locale/Tasker extensions and discovered apps.
 *
 * The available section is intentionally local and reviewable, while the
 * installed section is always derived from Android's package manager and the
 * protocol-aware discovery registry. Opening Play Store, application settings,
 * or Android's uninstall confirmation is always user initiated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(navController: NavController) {
    val viewModel: PluginManagerViewModel = hiltViewModel()
    val context = LocalContext.current
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var testingPackage by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<PluginInfo, com.nexaflow.core.execution.plugin.PluginFireResult>?>(null) }
    var uninstallEntry by remember { mutableStateOf<PluginCatalogEntry?>(null) }

    val client = remember { PluginFireClient(timeoutMs = 4_000) }
    val stringTestSuccess = stringResource(R.string.plugin_test_success)
    val stringTestFailed = stringResource(R.string.plugin_test_failed)
    val stringTestTimeout = stringResource(R.string.plugin_test_timeout)
    val stringTestPending = stringResource(R.string.plugin_test_pending)

    fun testFire(plugin: PluginInfo) {
        testingPackage = plugin.packageName
        scope.launch {
            val result = client.fire(context, plugin.packageName, plugin.receiverClass, bundle = null)
            testingPackage = null
            testResult = plugin to result
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.plugin_manager_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.plugin_refresh)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.plugin_manager_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                Text(
                    text = stringResource(R.string.plugin_catalog_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (refreshing && catalog.installed.isEmpty() &&
                catalog.recommendedAvailable.isEmpty() && catalog.advancedAvailable.isEmpty()
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
            item {
                SectionHeader(
                    text = stringResource(R.string.plugin_installed_title, catalog.installed.size)
                )
            }
            if (catalog.installed.isEmpty() && !refreshing) {
                item {
                    PluginSectionEmpty(
                        title = stringResource(R.string.plugin_no_installed_plugins),
                        detail = stringResource(R.string.plugin_no_installed_plugins_hint)
                    )
                }
            }
            items(catalog.installed.size, key = { "installed:${catalog.installed[it].packageName}" }) { index ->
                PluginCatalogCard(
                    entry = catalog.installed[index],
                    testing = testingPackage == catalog.installed[index].packageName,
                    onTest = { plugin -> testFire(plugin) },
                    onOpenStore = { openStore(context, it) },
                    onOpenApp = { openApplication(context, it) },
                    onOpenAppInfo = { openAppInfo(context, it) },
                    onUninstall = { uninstallEntry = catalog.installed[index] }
                )
            }
            item {
                SectionHeader(
                    text = stringResource(
                        R.string.plugin_available_title,
                        catalog.recommendedAvailable.size
                    )
                )
            }
            if (catalog.recommendedAvailable.isEmpty()) {
                item {
                    PluginSectionEmpty(
                        title = stringResource(R.string.plugin_no_available_plugins),
                        detail = stringResource(R.string.plugin_no_available_plugins_hint)
                    )
                }
            }
            items(
                catalog.recommendedAvailable.size,
                key = { "recommended:${catalog.recommendedAvailable[it].packageName}" }
            ) { index ->
                PluginCatalogCard(
                    entry = catalog.recommendedAvailable[index],
                    testing = false,
                    onTest = {},
                    onOpenStore = { openStore(context, it) },
                    onOpenApp = {},
                    onOpenAppInfo = {},
                    onUninstall = {}
                )
            }
            item {
                SectionHeader(
                    text = stringResource(
                        R.string.plugin_advanced_title,
                        catalog.advancedAvailable.size
                    )
                )
            }
            item {
                Text(
                    text = stringResource(R.string.plugin_advanced_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(
                catalog.advancedAvailable.size,
                key = { "advanced:${catalog.advancedAvailable[it].packageName}" }
            ) { index ->
                PluginCatalogCard(
                    entry = catalog.advancedAvailable[index],
                    testing = false,
                    onTest = {},
                    onOpenStore = { openStore(context, it) },
                    onOpenApp = {},
                    onOpenAppInfo = {},
                    onUninstall = {}
                )
            }
        }
    }

    uninstallEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { uninstallEntry = null },
            title = { Text(stringResource(R.string.plugin_uninstall_title, entry.displayName)) },
            text = { Text(stringResource(R.string.plugin_uninstall_message)) },
            dismissButton = {
                TextButton(onClick = { uninstallEntry = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uninstallEntry = null
                        requestUninstall(context, entry.packageName)
                    }
                ) {
                    Text(stringResource(R.string.plugin_uninstall))
                }
            }
        )
    }

    testResult?.let { (plugin, result) ->
        val message = when {
            result.timedOut -> stringTestTimeout
            result.resultCode == LocaleContract.RESULT_CODE_OK -> stringTestSuccess
            result.resultCode == LocaleContract.RESULT_CODE_PENDING -> stringTestPending
            result.resultCode == LocaleContract.RESULT_CODE_FAILED ->
                stringTestFailed + (result.message?.let { ": $it" } ?: "")
            else -> result.message ?: plugin.label
        }
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(text = plugin.label) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { testResult = null }) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun PluginSectionEmpty(title: String, detail: String) {
    NexaFlowCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun PluginCatalogCard(
    entry: PluginCatalogEntry,
    testing: Boolean,
    onTest: (PluginInfo) -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(entry.packageName) { mutableStateOf(false) }
    val status = pluginStatus(entry)
    val canLaunch = entry.installed && entry.appEnabled &&
        context.packageManager.getLaunchIntentForPackage(entry.packageName) != null

    NexaFlowCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PluginIcon(entry = entry)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(status.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = status.tone.color()
                    )
                    if (entry.isHighRisk) {
                        Text(
                            text = stringResource(R.string.plugin_high_risk_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    entry.definition?.let { definition ->
                        Text(
                            text = stringResource(R.string.plugin_capabilities),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            definition.capabilities.forEach { capability ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(capability.labelRes)) }
                                )
                            }
                        }
                    }
                    Text(
                        text = entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    PluginProtocolSummary(entry.descriptors)
                    when {
                        !entry.installed -> Text(
                            text = stringResource(R.string.plugin_install_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        entry.testablePlugin == null -> Text(
                            text = stringResource(R.string.plugin_test_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!entry.installed) {
                            CatalogActionButton(
                                icon = Icons.Filled.Storefront,
                                label = stringResource(R.string.plugin_open_store),
                                onClick = { onOpenStore(entry.packageName) }
                            )
                        } else if (!entry.appEnabled) {
                            CatalogActionButton(
                                icon = Icons.Filled.Info,
                                label = stringResource(R.string.plugin_enable),
                                onClick = { onOpenAppInfo(entry.packageName) }
                            )
                            CatalogActionButton(
                                icon = Icons.Filled.Delete,
                                label = stringResource(R.string.plugin_uninstall),
                                onClick = onUninstall
                            )
                        } else {
                            entry.testablePlugin?.let { plugin ->
                                CatalogActionButton(
                                    icon = Icons.Filled.PlayArrow,
                                    label = stringResource(R.string.plugin_test),
                                    enabled = !testing,
                                    onClick = { onTest(plugin) }
                                )
                            }
                            CatalogActionButton(
                                icon = if (canLaunch) Icons.AutoMirrored.Filled.OpenInNew else Icons.Filled.Info,
                                label = stringResource(if (canLaunch) R.string.plugin_open_app else R.string.plugin_open_info),
                                onClick = {
                                    if (canLaunch) onOpenApp(entry.packageName)
                                    else onOpenAppInfo(entry.packageName)
                                }
                            )
                            CatalogActionButton(
                                icon = Icons.Filled.Info,
                                label = stringResource(R.string.plugin_open_info),
                                onClick = { onOpenAppInfo(entry.packageName) }
                            )
                            CatalogActionButton(
                                icon = Icons.Filled.Delete,
                                label = stringResource(R.string.plugin_uninstall),
                                onClick = onUninstall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginIcon(entry: PluginCatalogEntry) {
    val context = LocalContext.current
    // Installed packages provide the only authoritative local source for their
    // launcher icon. Cache the rasterized value across ordinary recompositions.
    val bitmap = remember(entry.packageName, entry.installed, context) {
        if (entry.installed) {
            runCatching { context.packageManager.getApplicationIcon(entry.packageName) }
                .getOrNull()
                ?.toImageBitmapOrNull()
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null)
        } else {
            Icon(
                imageVector = Icons.Filled.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PluginProtocolSummary(descriptors: List<PluginDescriptor>) {
    if (descriptors.isEmpty()) return
    val types = descriptors.map { descriptor ->
        stringResource(
            when (descriptor.type) {
                PluginType.SETTING -> R.string.plugin_type_action
                PluginType.CONDITION -> R.string.plugin_type_condition
                PluginType.EVENT -> R.string.plugin_type_event
            }
        )
    }.distinct().joinToString(separator = " · ")
    Text(
        text = stringResource(R.string.plugin_protocol_types, types),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun CatalogActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(enabled = enabled, onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = label, modifier = Modifier.padding(start = 4.dp))
    }
}

private data class PluginStatusPresentation(val labelRes: Int, val tone: PluginStatusTone)

private enum class PluginStatusTone { READY, CAUTION, NEUTRAL }

@Composable
private fun PluginStatusTone.color() = when (this) {
    PluginStatusTone.READY -> MaterialTheme.colorScheme.primary
    PluginStatusTone.CAUTION -> MaterialTheme.colorScheme.tertiary
    PluginStatusTone.NEUTRAL -> MaterialTheme.colorScheme.secondary
}

private fun pluginStatus(entry: PluginCatalogEntry): PluginStatusPresentation = when {
    !entry.installed -> PluginStatusPresentation(R.string.plugin_status_not_installed, PluginStatusTone.NEUTRAL)
    !entry.appEnabled -> PluginStatusPresentation(R.string.plugin_status_disabled, PluginStatusTone.CAUTION)
    entry.compatibility == PluginCompatibilityStatus.COMPATIBLE ->
        PluginStatusPresentation(R.string.plugin_status_ready, PluginStatusTone.READY)
    entry.compatibility == PluginCompatibilityStatus.PARTIALLY_COMPATIBLE ->
        PluginStatusPresentation(R.string.plugin_status_partial, PluginStatusTone.CAUTION)
    entry.compatibility != null -> PluginStatusPresentation(R.string.plugin_status_unavailable, PluginStatusTone.CAUTION)
    else -> PluginStatusPresentation(R.string.plugin_status_not_detected, PluginStatusTone.CAUTION)
}

private fun openStore(context: android.content.Context, packageName: String) {
    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
    runCatching { context.startActivity(playIntent) }
        .onFailure { runCatching { context.startActivity(webIntent) } }
}

private fun openApplication(context: android.content.Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        runCatching { context.startActivity(intent) }
    } ?: openAppInfo(context, packageName)
}

private fun openAppInfo(context: android.content.Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}

/** Android always owns the final confirmation; NexaFlow never removes an app directly. */
private fun requestUninstall(context: android.content.Context, packageName: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }
}
