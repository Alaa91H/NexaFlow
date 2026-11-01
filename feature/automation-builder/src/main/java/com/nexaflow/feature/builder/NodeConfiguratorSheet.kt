package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.Dimens
import com.nexaflow.core.ui.DockPrimaryButton
import com.nexaflow.core.ui.ScreenHeader
import com.nexaflow.core.ui.SearchPill
import com.nexaflow.core.ui.theme.NexaFlowTheme

/**
 * The trigger / action picker shell (§7.2). Solid `surfaceContainerHigh` with
 * a 28dp top radius and a 60% scrim — never blurred. Header, search, category
 * chips and rows come from the caller; the shell owns the search field, the
 * selected count and the confirm Dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NodeConfiguratorSheet(
    title: String,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    selectedCount: Int? = null,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = Dimens.GroupRadius, topEnd = Dimens.GroupRadius),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenHorizontalPadding)
                .padding(bottom = Dimens.Space6),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
        ) {
            ScreenHeader(
                title = title,
                caption = selectedCount?.let { stringResource(R.string.selected_count, it) },
                onBack = onDismiss,
                titleStyle = NexaFlowTheme.type.subScreenTitle
            )
            if (searchQuery != null && onSearchQueryChange != null) {
                SearchPill(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.search)
                )
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)
            ) {
                if (selectedCount != null) {
                    Text(
                        text = stringResource(R.string.selected_count, selectedCount),
                        style = NexaFlowTheme.type.sectionLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                DockPrimaryButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = if (selectedCount == null) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

/** Inline catalog shell for the builder's trigger and execution sections. */
@Composable
internal fun NodeConfiguratorPanel(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCount: Int,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space1),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
    ) {
        ScreenHeader(
            title = title,
            caption = stringResource(R.string.selected_count, selectedCount),
            titleStyle = NexaFlowTheme.type.subScreenTitle
        )
        SearchPill(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = stringResource(R.string.search)
        )
        content()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)
        ) {
            Text(
                text = stringResource(R.string.selected_count, selectedCount),
                style = NexaFlowTheme.type.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            DockPrimaryButton(
                label = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled
            )
        }
    }
}
