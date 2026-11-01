package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme

/**
 * Category-tinted squircles (§6). The tile is what carries meaning now: 48dp
 * on routines, 44dp on picker and settings rows, filled with the category hue
 * at 16% (14% in pickers) and the icon at full strength.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    category: NexaFlowCategory,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.RoutineTile,
    radius: Dp = Dimens.RoutineTileRadius,
    picker: Boolean = false,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null
) {
    val hue = NexaFlowTheme.categories.of(category)
    val fill = if (picker) NexaFlowAlphas.CategoryTileFillPicker else NexaFlowAlphas.CategoryTileFill
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(hue.copy(alpha = fill)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = hue,
            modifier = Modifier.size(iconSize)
        )
    }
}
