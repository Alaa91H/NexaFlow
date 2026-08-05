package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Lets the user pick which actions run when the task's condition ends,
 * reusing the same categorized action list as the main "then" step.
 */
@Composable
fun ExitActionPickerDialog(
    alreadySelected: List<ActionOption>,
    onPick: (ActionOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pick_exit_action)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                actionCategories.forEach { category ->
                    val options = actionOptions.filter { it.category == category && it !in alreadySelected }
                    if (options.isNotEmpty()) {
                        item(key = "header_${category.name}") {
                            itemHeader(text = stringResource(category.headerRes))
                        }
                        options.forEach { option ->
                            item(key = option.actionType.name) {
                                ActionOptionRow(
                                    option = option,
                                    checked = false,
                                    onToggle = { onPick(option) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
