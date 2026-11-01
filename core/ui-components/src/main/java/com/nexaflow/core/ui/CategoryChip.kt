package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.core.ui.theme.forUserText

/**
 * Category selector chip (§6): 40dp tall inside a 48dp touch target, 16dp
 * horizontal padding, 8dp icon gap, 14sp/600. Selected flips to a primary
 * fill with the check replacing the category icon — the M3 filter-chip
 * pattern, without the stock chip's mixed surfaces.
 */
@Composable
fun CategoryChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    showCheck: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary else colors.surfaceContainerLow
    val content = if (selected) colors.onPrimary else colors.onSurface
    val border = if (selected) {
        colors.primary
    } else {
        colors.onSurface.copy(alpha = NexaFlowAlphas.ChipBorder)
    }
    Box(
        modifier = modifier
            .height(Dimens.Space12)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(Dimens.ChipHeight)
                .clip(shape)
                .background(container)
                .border(Dimens.HairlineBorder, border, shape)
                .padding(horizontal = Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
        ) {
            when {
                selected && showCheck -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )

                leadingIcon != null -> Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = NexaFlowTheme.type.chip.forUserText(label),
                color = content
            )
        }
    }
}

/**
 * Horizontally scrollable chip row with 8dp gaps and a trailing edge fade, so
 * the last chip visibly runs off-screen instead of ending abruptly.
 */
@Composable
fun CategoryChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val fadeColor = MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .drawWithContent {
                drawContent()
                val fadeWidth = 28.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                        startX = size.width - fadeWidth,
                        endX = size.width
                    ),
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height)
                )
            },
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        content = content
    )
}
