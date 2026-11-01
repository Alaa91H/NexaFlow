package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.core.ui.theme.forUserText

/**
 * A grouped list surface (§6): 28dp radius, clipped, one hairline around the
 * whole group. Rows draw their own 5% dividers, so the group reads as a single
 * sheet instead of a stack of cards.
 */
@Composable
fun PickerGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Dimens.GroupRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                Dimens.HairlineBorder,
                MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.HairlineBorder),
                shape
            ),
        content = content
    )
}

/**
 * Multi-select picker row: 44dp category tile, title, optional two-line
 * description, trailing 28dp [MultiCheck]. The whole row is the toggle target
 * and exposes checkbox semantics merged into one announcement.
 */
@Composable
fun PickerRow(
    icon: ImageVector,
    title: String,
    category: NexaFlowCategory,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (checked) colors.primary.copy(alpha = NexaFlowAlphas.SelectedRowTint)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {}
    ) {
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
                    style = NexaFlowTheme.type.pickerRowTitle.forUserText(title),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = NexaFlowTheme.type.meta.forUserText(description),
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) trailing() else MultiCheck(checked = checked)
        }
    }
}

/** 28dp selection circle: 2dp outline when off, primary + check when on. */
@Composable
fun MultiCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(Dimens.CheckSize)
            .clip(CircleShape)
            .background(if (checked) colors.primary else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = if (checked) colors.primary else colors.outline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
