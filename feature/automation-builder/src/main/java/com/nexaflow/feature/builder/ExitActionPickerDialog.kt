package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Lets the user pick which actions run when the task's condition ends,
 * reusing the same categorized action list as the main "then" step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitActionPickerDialog(
    alreadySelected: List<ActionOption>,
    onPick: (ActionOption) -> Unit,
    onDismiss: () -> Unit
) {
    // Google 2026: selection tasks open as a full-height modal bottom sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = stringResource(R.string.pick_exit_action),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                actionCategories.forEach { category ->
                    val options = actionOptions.filter { it.category == category && it !in alreadySelected }
                    if (options.isNotEmpty()) {
                        item(key = "header_${category.name}") {
                            ItemHeader(text = stringResource(category.headerRes))
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
}
