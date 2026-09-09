package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * An installed launcher app. Immutable and keeps no icon reference: the icon
 * is loaded lazily per item (keyed by package name) so stability is preserved
 * without eagerly rendering every app's bitmap upfront.
 */
@Immutable
data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    onPickSingle: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    onPickMultiple: ((List<InstalledApp>) -> Unit)? = null,
    multiSelect: Boolean = false,
    preSelectedPackages: List<String> = emptyList()
) {
    val context = LocalContext.current
    val allApps = remember { loadAllApps(context) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    // Pre-check packages that are already selected so returning to the picker
    // keeps the checkbox marked and lets the user add even more apps.
    val selected = remember(allApps) {
        mutableStateListOf<InstalledApp>().apply {
            addAll(allApps.filter { it.packageName in preSelectedPackages })
        }
    }

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

    // Cancel always discards the current selection and closes the sheet
    // without applying anything; OK applies exactly what is checked.

    // Google 2026: selection tasks open as a full-height modal bottom sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Text(
            text = if (multiSelect) stringResource(R.string.choose_apps) else stringResource(R.string.choose_app),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        }
        // The list is the only weighted child of the sheet's ColumnScope: it
        // shrinks to the space left after the header, the selection preview,
        // and the OK/Cancel bar are measured. Weighting it anywhere nested
        // (or leaving it unweighted) lets a long app list consume the whole
        // sheet and push the confirm bar off-screen — the bug where OK/Cancel
        // were never visible on real devices.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(horizontal = 24.dp)
        ) {
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
                        // Lazy icon render: only items actually composed pay
                        // the bitmap cost, keyed by package so it is stable
                        // across recompositions of the same row.
                        val iconBitmap = remember(app.packageName) {
                            loadAppIcon(context, app.packageName)
                        }
                        if (iconBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Android,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest
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
                        }
                        if (!multiSelect && isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        // Live preview of exactly what OK will apply: each checked app shows
        // its icon with a remove (×) affordance; tapping it deselects in place.
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (multiSelect) {
                        "${stringResource(R.string.selected_count, selected.size)} — ${stringResource(R.string.ok)}"
                    } else {
                        stringResource(R.string.selected_count, selected.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(selected, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val previewBitmap = remember(app.packageName) {
                            loadAppIcon(context, app.packageName)
                        }
                        if (previewBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = previewBitmap,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Android,
                                contentDescription = app.label,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp)
                        )
                        IconButton(
                            onClick = { selected.remove(app) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.remove),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cancel: discard the selection and go back — nothing is applied.
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.cancel))
            }
            // OK: confirm the checked apps. Disabled while nothing is selected
            // so the pair always reads as apply/discard, never as navigation.
            Button(
                onClick = {
                    if (multiSelect) {
                        confirm()
                    } else {
                        if (selected.isNotEmpty()) onPickSingle(selected.first()) else onDismiss()
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (multiSelect) {
                        "${stringResource(R.string.ok)} (${selected.size})"
                    } else {
                        stringResource(R.string.ok)
                    }
                )
            }
        }
    }
}

/**
 * Renders one app's launcher icon to a stable [ImageBitmap], or null.
 * Renders at a fixed density-scaled size (~72dp) so vector and adaptive icons
 * draw crisp inside the 40.dp slot instead of upscaling from intrinsic size.
 */
private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val size = (72 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    bitmap.asImageBitmap()
}.getOrNull()

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
