package com.nexaflow.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * The floating navigation pill.
 *
 * Instead of a navigation bar welded to the bottom edge, the destinations
 * sit in a pill that floats above the content with its own margin, hairline
 * edge and shadow. The pill's background is a translucent surface so the
 * content scrolling under it stays faintly visible; the appearance settings
 * can turn that translucency off, and the compact density tightens it.
 *
 * The pill draws over the content rather than inside a Scaffold slot, so
 * callers place it in the screen's own overlay box and leave the bottom
 * inset to the content.
 */
@Composable
fun NexaFlowNavPill(
    currentRoute: String?,
    destinations: List<NavigationDestination>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val height = NexaFlowTraitsDefaults.navigationHeight()
    val translucent = NexaFlowTraitsDefaults.blur()
    val colors = MaterialTheme.colorScheme

    val containerColor = if (translucent) {
        colors.surfaceContainerLow.copy(alpha = 0.92f)
    } else {
        colors.surfaceContainerLow
    }

    val shape = RoundedCornerShape(height / 2)
    val hairline = NexaFlowTraitsDefaults.outline(
        color = NexaFlowTraitsDefaults.borderColor(colors.outlineVariant),
        width = 1.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(containerColor)
            .then(
                if (hairline != null) Modifier.border(hairline, shape) else Modifier
            )
            .padding(horizontal = Dimens.Space2),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            NavPillItem(
                destination = destination,
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) }
            )
        }
    }
}

@Composable
private fun NavPillItem(
    destination: NavigationDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val iconColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        label = "nexaflowNavPillIcon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.onSurface else colors.onSurfaceVariant,
        label = "nexaflowNavPillLabel"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(name = "NexaFlowNavPill", showBackground = true)
@Composable
private fun NexaFlowNavPillPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(Dimens.Space4)) {
            NexaFlowNavPill(
                currentRoute = "dashboard",
                destinations = listOf(
                    NavigationDestination(route = "dashboard", label = "Home", icon = Icons.Filled.Home),
                    NavigationDestination(route = "automations", label = "Routines", icon = Icons.Filled.List),
                    NavigationDestination(route = "settings", label = "Settings", icon = Icons.Filled.Settings)
                ),
                onNavigate = {}
            )
        }
    }
}
