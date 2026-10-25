package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.Dimens.RowIconSize
import com.nexaflow.core.ui.Dimens.Space1
import com.nexaflow.core.ui.Dimens.Space2
import com.nexaflow.core.ui.Dimens.Space3
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * A row in a settings list.
 *
 * The row sits on the card's surface with a hairline edge of its own, so a
 * list of rows reads as a stack of cards rather than as one long block with
 * rules drawn through it. The leading icon wears the theme's accent, and
 * under the monochrome trait it falls back to the neutral container.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    alternatingIndex: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color? = null
) {
    val rowSurface = alternatingIndex?.let { alternatingSurfaceColor(it) }
        ?: MaterialTheme.colorScheme.surfaceContainerLow
    val iconSurface = alternatingIndex?.let { alternatingSurfaceColor(it + 1) }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = MaterialTheme.shapes.small
    val hairline = NexaFlowTraitsDefaults.outline(
        color = NexaFlowTraitsDefaults.borderColor(MaterialTheme.colorScheme.outlineVariant),
        width = 1.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowSurface, shape)
            .then(
                if (hairline != null) Modifier.border(hairline, shape) else Modifier
            )
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Space3, vertical = Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space3)
    ) {
        Box(
            modifier = Modifier
                .size(RowIconSize.dp)
                .background(
                    color = iconSurface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NexaFlowTraitsDefaults.accentColor(
                    iconTint ?: MaterialTheme.colorScheme.primary
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Preview(name = "SettingRow", showBackground = true)
@Preview(name = "SettingRow (dark)", showBackground = true)
@Composable
private fun SettingRowPreview() {
    MaterialTheme {
        SettingRow(
            icon = Icons.Filled.Wifi,
            title = "Wi-Fi",
            subtitle = "Connected to HomeNetwork",
            onClick = {}
        )
    }
}
