package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
@Composable
fun ActionPickerDialog(
    alreadySelected: List<ActionOption>,
    onConfirm: (List<ActionOption>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<ActionOption>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pick_actions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val trimmed = query.trim().lowercase()
                    val matched = actionOptions.count { option ->
                        option !in alreadySelected &&
                            (trimmed.isEmpty() ||
                                context.getString(option.titleRes).lowercase().contains(trimmed) ||
                                context.getString(option.subtitleRes).lowercase().contains(trimmed))
                    }
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
                            val options = actionOptions.filter { option ->
                                option !in alreadySelected &&
                                    (trimmed.isEmpty() ||
                                        context.getString(option.titleRes).lowercase().contains(trimmed) ||
                                        context.getString(option.subtitleRes).lowercase().contains(trimmed))
                            }
                            if (options.isNotEmpty()) {
                                item(key = "header_${category.name}") {
                                    itemHeader(text = stringResource(category.headerRes))
                                }
                                options.forEach { option ->
                                    item(key = option.actionType.name) {
                                        ActionOptionRow(
                                            option = option,
                                            checked = option in picked,
                                            onToggle = {
                                                if (option in picked) picked.remove(option)
                                                else picked.add(option)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
