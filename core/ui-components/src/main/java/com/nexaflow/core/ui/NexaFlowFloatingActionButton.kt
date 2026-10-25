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
import com.nexaflow.core.ui.theme.NexaFlowGradients
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * The app's primary action button.
 *
 * An extended floating action button in the brand gradient. It is the one
 * control that is allowed to carry the full gradient, because it is the one
 * control a user is meant to reach for.
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

/**
 * The app's primary action button, on the brand gradient.
 */
@Composable
fun NexaFlowGradientFab(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val gradient = NexaFlowGradients.brand
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.nexaFlowEntrance(),
        shape = RoundedCornerShape(percent = 50),
        containerColor = NexaFlowTraitsDefaults.accentColor(gradient.first()),
        contentColor = Color.White,
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
    MaterialTheme {
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
    MaterialTheme {
        NexaFlowGradientFab(
            onClick = {},
            icon = Icons.Filled.Add,
            label = "New"
        )
    }
}
