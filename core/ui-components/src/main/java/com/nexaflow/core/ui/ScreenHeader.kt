package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText

/**
 * Screen header (§6): a 44dp circular icon button at one end and the title
 * block at the other — or, on sub-screens, after a 14dp gap following the
 * back button. Replaces the old top bar on Dashboard, builder, details and
 * pickers.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    titleStyle: TextStyle = NexaFlowTheme.type.screenTitle,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            HeaderIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = backContentDescription,
                onClick = onBack
            )
            Box(modifier = Modifier.size(Dimens.TileTextGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle.forText(title),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = NexaFlowTheme.type.meta.forText(caption),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
            content = actions
        )
    }
}

/**
 * 44dp circular icon button with a 48dp touch target: 5% `onSurface` fill and
 * a 12% hairline. The only chrome style in the app — no stock IconButton.
 */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.IconButton
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(Dimens.Space12)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(colors.onSurface.copy(alpha = NexaFlowAlphas.IconButtonFill))
                .border(
                    Dimens.HairlineBorder,
                    colors.onSurface.copy(alpha = NexaFlowAlphas.StrongBorder),
                    shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
