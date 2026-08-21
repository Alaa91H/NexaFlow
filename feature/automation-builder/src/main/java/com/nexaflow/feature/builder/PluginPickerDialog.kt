package com.nexaflow.feature.builder

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.pluginsdk.PluginRiskPolicy
import com.nexaflow.core.ui.toImageBitmapOrNull
import com.nexaflow.domain.models.PluginInfo

/**
 * Lists installed Locale-compatible action plugins. High-risk command plugins
 * require a clear local acknowledgement before their configuration activity can
 * be opened; the acknowledgement is persisted with the configured action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPickerDialog(
    plugins: List<PluginInfo>,
    onRefresh: () -> Unit,
    onPick: (PluginInfo, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingHighRiskPlugin by remember { mutableStateOf<PluginInfo?>(null) }
    LaunchedEffect(Unit) { onRefresh() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plugin_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.plugin_refresh)
                )
            }
        }
        if (plugins.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.plugin_picker_empty),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.plugin_picker_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(plugins.size, key = { plugins[it].receiverClass }) { index ->
                    val plugin = plugins[index]
                    PluginPickerRow(
                        plugin = plugin,
                        onClick = {
                            if (PluginRiskPolicy.requiresHighRiskApproval(plugin.packageName)) {
                                pendingHighRiskPlugin = plugin
                            } else {
                                onPick(plugin, false)
                            }
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    }

    pendingHighRiskPlugin?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingHighRiskPlugin = null },
            title = { Text(stringResource(R.string.plugin_high_risk_title, plugin.label)) },
            text = { Text(stringResource(R.string.plugin_high_risk_message)) },
            dismissButton = {
                TextButton(onClick = { pendingHighRiskPlugin = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHighRiskPlugin = null
                        onPick(plugin, true)
                    }
                ) {
                    Text(stringResource(R.string.plugin_high_risk_continue))
                }
            }
        )
    }
}

@Composable
private fun PluginPickerRow(plugin: PluginInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(plugin.packageName, context) {
        runCatching { context.packageManager.getApplicationIcon(plugin.packageName) }
            .getOrNull()
            ?.toImageBitmapOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = plugin.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = plugin.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (PluginRiskPolicy.requiresHighRiskApproval(plugin.packageName)) {
                Text(
                    text = stringResource(R.string.plugin_high_risk_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
