package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

/**
 * Google 2026 pill FAB — an extended floating action button with a fully
 * rounded (pill) shape, tonal container and label, exactly like the primary
 * action button in Google Tasks / Keep / Calendar.
 */
@Composable
fun NexaFlowFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.nexaFlowEntrance(),
        shape = RoundedCornerShape(percent = 50),
        containerColor = containerColor,
        contentColor = contentColor,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    )
}

@Preview(name = "FAB", showBackground = true)
@Composable
private fun FabPreview() {
    NexaFlowTheme {
        NexaFlowFloatingActionButton(
            onClick = {},
            icon = Icons.Filled.Add,
            label = "New"
        )
    }
}

@Preview(name = "FAB – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FabDarkPreview() {
    NexaFlowTheme {
        NexaFlowFloatingActionButton(
            onClick = {},
            icon = Icons.Filled.Add,
            label = "New"
        )
    }
}
