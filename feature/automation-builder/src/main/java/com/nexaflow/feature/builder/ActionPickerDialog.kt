package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Searchable, multi-select action picker. Lets the user add several actions to
 * a task at once instead of scrolling the whole category list inline.
 */
/** Searchable, multi-select action picker. Lets the user add several actions to
 * a task at once instead of scrolling the whole category list inline.
 */
private data class SearchEntry(
    val option: ActionOption,
    val title: String,
    val subtitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerDialog(
    alreadySelected: List<ActionOption>,
    onConfirm: (List<ActionOption>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<ActionOption>() }

    // Google 2026: selection tasks open as a full-height modal bottom sheet,
    // not a centered dialog.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = stringResource(R.string.pick_actions),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search_actions)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true
                )
                val entries = actionOptions.map { option ->
                    SearchEntry(
                        option = option,
                        title = stringResource(option.titleRes),
                        subtitle = stringResource(option.subtitleRes)
                    )
                }
                val trimmed = query.trim().lowercase()
                val matched = entries.count { entry ->
                    entry.option !in alreadySelected &&
                        (trimmed.isEmpty() ||
                            entry.title.lowercase().contains(trimmed) ||
                            entry.subtitle.lowercase().contains(trimmed))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (matched == 0) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.no_matching_actions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        actionCategories.forEach { category ->
                            val options = entries.filter { entry ->
                                entry.option !in alreadySelected &&
                                    (trimmed.isEmpty() ||
                                        entry.title.lowercase().contains(trimmed) ||
                                        entry.subtitle.lowercase().contains(trimmed))
                            }
                            if (options.isNotEmpty()) {
                                item(key = "header_${category.name}") {
                                    ItemHeader(text = stringResource(category.headerRes))
                                }
                                options.forEach { entry ->
                                    item(key = entry.option.actionType.name) {
                                        ActionOptionRow(
                                            option = entry.option,
                                            checked = entry.option in picked,
                                            onToggle = {
                                                if (entry.option in picked) picked.remove(entry.option)
                                                else picked.add(entry.option)
                                            }
                                        )
                                    }
                                }
                            }
                        }
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
            TextButton(
                onClick = { onConfirm(picked.toList()) },
                enabled = picked.isNotEmpty()
            ) {
                Text(
                    text = if (picked.isEmpty()) {
                        stringResource(R.string.add_action)
                    } else {
                        stringResource(R.string.add_count, picked.size)
                    }
                )
            }
        }
    }
}
