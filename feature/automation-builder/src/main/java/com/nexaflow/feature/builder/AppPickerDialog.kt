package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

@Composable
fun AppPickerDialog(
    onPickSingle: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    onPickMultiple: ((List<InstalledApp>) -> Unit)? = null,
    multiSelect: Boolean = false
) {
    val context = LocalContext.current
    val allApps = remember { loadAllApps(context) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<InstalledApp>() }

    val filtered = allApps.filter { app ->
        (showSystem || !app.isSystemApp) &&
            (query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    fun confirm() {
        if (multiSelect && selected.isNotEmpty()) {
            onPickMultiple?.invoke(selected.toList())
        } else if (selected.isNotEmpty()) {
            onPickSingle(selected.first())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (multiSelect) stringResource(R.string.choose_apps) else stringResource(R.string.choose_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search_apps)) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.show_system_apps),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it }
                    )
                }
                Text(
                    text = stringResource(R.string.app_count, filtered.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isSelected = app in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (multiSelect) {
                                        if (isSelected) selected.remove(app) else selected.add(app)
                                    } else {
                                        selected.clear()
                                        selected.add(app)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (multiSelect) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) selected.remove(app) else selected.add(app)
                                    }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (!multiSelect && isSelected) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (multiSelect) confirm() else {
                    if (selected.isNotEmpty()) onPickSingle(selected.first()) else onDismiss()
                }
            }) {
                Text(text = if (multiSelect) "${stringResource(R.string.ok)} (${selected.size})" else stringResource(R.string.select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

private fun loadAllApps(context: Context): List<InstalledApp> {
    return try {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val info = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = try {
                    packageManager.getApplicationLabel(info.applicationInfo).toString()
                } catch (_: Throwable) {
                    info.packageName
                }
                InstalledApp(
                    label = label,
                    packageName = info.packageName,
                    isSystemApp = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    } catch (_: Throwable) {
        emptyList()
    }
}
