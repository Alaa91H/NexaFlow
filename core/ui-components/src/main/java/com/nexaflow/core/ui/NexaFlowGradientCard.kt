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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowGradients
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * A card that carries its own color.
 *
 * The gradient is the app's quick-glance coding: a routine, an app or a
 * status can be recognised by its color before a word is read. The palette
 * is fixed and curated, so the colors read as a system rather than as noise.
 *
 * The gradient stops are [NexaFlowTraitsDefaults.accentColor]-aware: under
 * the monochrome trait the card falls back to the theme's accent, and under
 * the "theme accent" trait it uses the primary color instead of its own.
 */
@Composable
fun NexaFlowGradientCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    gradient: List<Color> = NexaFlowGradients.items.first(),
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val accent = NexaFlowTraitsDefaults.accentColor(gradient.first())
    val end = NexaFlowTraitsDefaults.accentColor(gradient.last())
    val brush = Brush.linearGradient(listOf(accent, end))
    val shape = MaterialTheme.shapes.large

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * The rounded icon disc that sits on a gradient card.
 *
 * Separate from [NexaFlowGradientCard] so a card can place it at the start,
 * the end, or over a larger surface.
 */
@Composable
fun NexaFlowGradientIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    gradient: List<Color> = NexaFlowGradients.items.first(),
    size: Int = 40
) {
    val brush = NexaFlowTraitsDefaults.iconBackground(gradient)
    val tint = NexaFlowTraitsDefaults.iconTint(Color.White)

    Box(
        modifier = modifier
            .size(size.dp)
            .background(brush, CircleShape)
            .then(
                val hairline = NexaFlowTraitsDefaults.outline(
                    color = Color.White.copy(alpha = 0.18f),
                    width = 1.dp
                )
                if (hairline != null) Modifier.border(hairline, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp)
        )
    }
}

@Preview(name = "NexaFlowGradientCard", showBackground = true)
@Preview(name = "NexaFlowGradientCard (dark)", showBackground = true)
@Composable
private fun NexaFlowGradientCardPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
        ) {
            NexaFlowGradientCard(
                title = "Morning briefing",
                subtitle = "07:00 · every day",
                icon = androidx.compose.material.icons.Icons.Filled.Home,
                gradient = NexaFlowGradients.items[0]
            )
            NexaFlowGradientCard(
                title = "Wi-Fi at the office",
                subtitle = "On arrival",
                icon = androidx.compose.material.icons.Icons.Filled.List,
                gradient = NexaFlowGradients.items[1]
            )
        }
    }
}
