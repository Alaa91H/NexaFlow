package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * T12 product shell: the builder opens one modal configurator for discovery
 * instead of permanently laying every trigger/action option into the wizard.
 *
 * The caller supplies the schema/catalog-driven category and row content.
 * This shell owns the common search, selected-count and confirmation surface;
 * node-specific editors remain field renderers beneath the same shell while
 * the remaining T18-T25 families are cut over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NodeConfiguratorSheet(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCount: Int,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search)) }
            )
            Text(
                text = stringResource(R.string.selected_count, selectedCount),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
            )
            content()
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = confirmEnabled
            ) {
                Text(confirmLabel)
            }
        }
    }
}
