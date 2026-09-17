package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.rom.EvolverCatalog
import com.nexaflow.core.rom.RomIntegrationManager

/**
 * Professional Evolver setting picker — the heart of Evolution X automation.
 *
 * Shows every Evolver key (live from device via EvolutionXSettingsBridge.listCustomKeys
 * plus the curated EvolverCatalog.knownSettings) grouped by category (QS, Status Bar,
 * Lockscreen, etc.) with search, current value, and description. Selecting a key
 * auto-fills namespace/key/value for the EVO_SET_SETTING action.
 *
 * This replaces the raw SYSTEM_SET_SETTING key/value text fields with a typed,
 * searchable, categorized picker — the professional UX for controlling every
 * Evolution X property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvolverSettingPickerDialog(
    onPick: (EvolutionXSettingsBridge.SettingEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var liveKeys by remember { mutableStateOf<List<EvolutionXSettingsBridge.SettingEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Load live keys on background thread (may need root/Shizuku for full list)
        liveKeys = try {
            EvolutionXSettingsBridge.listCustomKeys(context)
        } catch (_: Exception) {
            emptyList()
        }
        loading = false
    }

    val isEvolutionX = try {
        RomIntegrationManager.buildInfo(context).family.displayName.contains("Evolution", ignoreCase = true)
    } catch (_: Exception) { false }

    // Merge live keys + catalog keys (catalog provides typed metadata for every category)
    val catalogKeys = EvolverCatalog.knownSettings.map { meta ->
        EvolutionXSettingsBridge.SettingEntry(
            namespace = meta.namespace,
            key = meta.keyPattern.replace("*", "").replace("_*", ""),
            value = meta.defaultValue
        )
    }

    val allKeys = (liveKeys + catalogKeys).distinctBy { it.key }.sortedBy { it.displayKey }

    val filtered = if (query.isBlank()) allKeys else allKeys.filter {
        it.key.contains(query, ignoreCase = true) ||
            it.value.contains(query, ignoreCase = true) ||
            EvolverCatalog.categorize(it.key).displayName.contains(query, ignoreCase = true)
    }

    val grouped = filtered.groupBy { EvolverCatalog.categorize(it.key) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.evolver_picker_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isEvolutionX) stringResource(R.string.evolver_picker_subtitle_evo, allKeys.size) else stringResource(R.string.evolver_picker_subtitle_any, allKeys.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.evolver_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                singleLine = true
            )

            if (loading) {
                Text(stringResource(R.string.evolver_picker_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 16.dp))
            } else if (filtered.isEmpty()) {
                Text(stringResource(R.string.evolver_picker_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    grouped.forEach { (category, keys) ->
                        item(key = "header_${category.name}") {
                            Text(
                                text = "${categoryLabel(category)} • ${keys.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(keys, key = { it.displayKey }) { entry ->
                            val meta = EvolverCatalog.metaFor(entry.key)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(entry) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.Build, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${entry.namespace.shellName} • ${meta?.description ?: entry.value.ifBlank { "—" }} • ${meta?.valueType ?: "STRING"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** Localized display name for an [com.nexaflow.core.rom.EvolverCatalog.Category]. */
@Composable
internal fun categoryLabel(category: com.nexaflow.core.rom.EvolverCatalog.Category): String =
    when (category) {
        com.nexaflow.core.rom.EvolverCatalog.Category.QUICK_SETTINGS -> stringResource(R.string.evolver_cat_qs)
        com.nexaflow.core.rom.EvolverCatalog.Category.STATUS_BAR -> stringResource(R.string.evolver_cat_status)
        com.nexaflow.core.rom.EvolverCatalog.Category.LOCKSCREEN -> stringResource(R.string.evolver_cat_lock)
        com.nexaflow.core.rom.EvolverCatalog.Category.NOTIFICATIONS -> stringResource(R.string.evolver_cat_notif)
        com.nexaflow.core.rom.EvolverCatalog.Category.NAVIGATION -> stringResource(R.string.evolver_cat_nav)
        com.nexaflow.core.rom.EvolverCatalog.Category.THEMING -> stringResource(R.string.evolver_cat_theming)
        com.nexaflow.core.rom.EvolverCatalog.Category.AMBIENT_AOD -> stringResource(R.string.evolver_cat_aod)
        com.nexaflow.core.rom.EvolverCatalog.Category.BUTTONS -> stringResource(R.string.evolver_cat_buttons)
        com.nexaflow.core.rom.EvolverCatalog.Category.NETWORK_BATTERY -> stringResource(R.string.evolver_cat_netbat)
        com.nexaflow.core.rom.EvolverCatalog.Category.SYSTEM_UI -> stringResource(R.string.evolver_cat_sysui)
        com.nexaflow.core.rom.EvolverCatalog.Category.DEX -> stringResource(R.string.evolver_cat_dex)
        com.nexaflow.core.rom.EvolverCatalog.Category.OTHER -> stringResource(R.string.evolver_cat_other)
    }

/** Localized one-line description for an [com.nexaflow.core.rom.EvolverCatalog.Category]. */
@Composable
internal fun categoryDescription(category: com.nexaflow.core.rom.EvolverCatalog.Category): String =
    when (category) {
        com.nexaflow.core.rom.EvolverCatalog.Category.QUICK_SETTINGS -> stringResource(R.string.evolver_cat_qs_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.STATUS_BAR -> stringResource(R.string.evolver_cat_status_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.LOCKSCREEN -> stringResource(R.string.evolver_cat_lock_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.NOTIFICATIONS -> stringResource(R.string.evolver_cat_notif_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.NAVIGATION -> stringResource(R.string.evolver_cat_nav_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.THEMING -> stringResource(R.string.evolver_cat_theming_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.AMBIENT_AOD -> stringResource(R.string.evolver_cat_aod_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.BUTTONS -> stringResource(R.string.evolver_cat_buttons_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.NETWORK_BATTERY -> stringResource(R.string.evolver_cat_netbat_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.SYSTEM_UI -> stringResource(R.string.evolver_cat_sysui_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.DEX -> stringResource(R.string.evolver_cat_dex_desc)
        com.nexaflow.core.rom.EvolverCatalog.Category.OTHER -> stringResource(R.string.evolver_cat_other_desc)
    }
