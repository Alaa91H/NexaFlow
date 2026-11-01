package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText

/**
 * Settings row (§7.4): same metrics as a picker row — 44dp tile, 13×16
 * padding, 15.5sp/700 title, 12.5sp meta — so Settings, History and the
 * pickers read as one system.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    category: NexaFlowCategory = NexaFlowCategory.ADVANCED,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.HairlineBorder)
                    .background(colors.onSurface.copy(alpha = NexaFlowAlphas.Divider))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = Dimens.Space4, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.TileTextGap)
        ) {
            IconTile(
                icon = icon,
                category = category,
                size = Dimens.PickerTile,
                radius = Dimens.PickerTileRadius,
                picker = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = NexaFlowTheme.type.pickerRowTitle.forText(title),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = NexaFlowTheme.type.meta.forText(subtitle),
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(name = "SettingRow", showBackground = true)
@Composable
private fun SettingRowPreview() {
    MaterialTheme {
        SettingRow(
            icon = Icons.Filled.Language,
            title = "Wi-Fi",
            subtitle = "Connected to HomeNetwork",
            onClick = {}
        )
    }
}
