package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
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
                            val iconBitmap = app.icon?.let { drawable ->
                                val bitmap = android.graphics.Bitmap.createBitmap(
                                    drawable.intrinsicWidth.coerceAtLeast(1),
                                    drawable.intrinsicHeight.coerceAtLeast(1),
                                    android.graphics.Bitmap.Config.ARGB_8888
                                )
                                val canvas = android.graphics.Canvas(bitmap)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                bitmap.asImageBitmap()
                            }
                            if (iconBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
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
                val icon = runCatching { packageManager.getApplicationIcon(info.applicationInfo) }.getOrNull()
                InstalledApp(
                    label = label,
                    packageName = info.packageName,
                    isSystemApp = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    } catch (_: Throwable) {
        emptyList()
    }
}
