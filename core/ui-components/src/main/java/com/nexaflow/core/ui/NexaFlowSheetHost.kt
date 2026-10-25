package com.nexaflow.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one way the app opens a bottom sheet.
 *
 * Every sheet in the app goes through this host so the shape, the container
 * color and the drag handle stay identical everywhere — the builder's
 * configurator, the app picker, the ROM setting picker and the action
 * editors all open the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaFlowSheetHost(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { NexaFlowSheetHandle() }
    ) {
        content()
    }
}

/** The small rounded grab bar at the top of a sheet. */
@Composable
fun NexaFlowSheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = Dimens.Space3)
            .size(width = 36.dp, height = 4.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 36.dp, height = 4.dp)) {
            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.5f),
                cornerRadius = CornerRadius(size.height / 2f)
            )
        }
    }
}
