package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.Dimens.Space3
import com.nexaflow.core.ui.theme.NexaFlowGradients
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * A circular icon badge.
 *
 * The badge can wear a flat container color or a gradient. A gradient badge
 * is the app's quick-glance coding at its smallest: an item's own color
 * right next to its name.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier,
    size: Int = Dimens.IconBadgeSmall,
    gradient: List<Color>? = null
) {
    val shape = CircleShape
    val hairline = NexaFlowTraitsDefaults.outline(
        color = NexaFlowTraitsDefaults.borderColor(MaterialTheme.colorScheme.outlineVariant),
        width = 1.dp
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                brush = gradient?.let { Brush.linearGradient(it) }
                    ?: Brush.linearGradient(listOf(containerColor, containerColor)),
                shape = shape
            )
            .then(
                if (hairline != null) Modifier.border(hairline, shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
    }
}

@Preview(name = "IconBadge", showBackground = true)
@Preview(name = "IconBadge (dark)", showBackground = true)
@Composable
private fun IconBadgePreview() {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space3)) {
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = MaterialTheme.colorScheme.primary,
            gradient = NexaFlowGradients.items[0]
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = MaterialTheme.colorScheme.secondary,
            gradient = NexaFlowGradients.items[1]
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = MaterialTheme.colorScheme.error,
            gradient = NexaFlowGradients.items[5]
        )
    }
}
