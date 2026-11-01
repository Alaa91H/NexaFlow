package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText

/**
 * The floating Dock (§7.1): the app's only glass surface. 68dp tall, 34dp
 * radius, 8dp padding, 16dp side margins and 28dp above the navigation inset.
 * Layout is identical in blur and solid modes — only the material changes.
 */
@Composable
fun Dock(
    leading: @Composable RowScope.() -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.DockMaxWidth)
                .heightIn(min = Dimens.DockHeight),
            shape = RoundedCornerShape(Dimens.DockRadius),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.DockPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                    content = leading
                )
                action()
            }
        }
    }
}

/**
 * The Ask affordance inside the Dock: 52dp tall, fully rounded, 18dp side
 * padding, bolt icon and 15sp/600 label on a 6% fill.
 */
@Composable
fun DockAskPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    // Ink's own periwinkle, or primary under dynamic colour (see the role's doc).
    val labelColor = NexaFlowTheme.colors.askPillLabel
    Row(
        modifier = modifier
            .height(Dimens.DockButton)
            .clip(RoundedCornerShape(50))
            .background(colors.onSurface.copy(alpha = NexaFlowAlphas.AskPillFill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            style = NexaFlowTheme.type.askPill.forText(label),
            color = labelColor,
            maxLines = 1
        )
    }
}

/** The primary Dock action: 52dp tall, 24dp side padding, fully rounded. */
@Composable
fun DockPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(Dimens.DockButton)
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) colors.primary else colors.primary.copy(alpha = 0.38f)
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.Space6),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = NexaFlowTheme.type.button.forText(label),
            color = if (enabled) colors.onPrimary else colors.onPrimary.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

/** 52dp circular primary action (Dashboard "+"). */
@Composable
fun DockIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dimens.DockButton)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Text-only Dock action ("Run now"), for the builder. */
@Composable
fun DockTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .height(Dimens.DockButton)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.Space4),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = NexaFlowTheme.type.button.forText(label),
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
            maxLines = 1
        )
    }
}

/** Tonal pill button used for "Add trigger / Add action" in the builder. */
@Composable
fun AddNodeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = Dimens.Space12)
            .clip(RoundedCornerShape(50))
            .background(colors.primary.copy(alpha = NexaFlowAlphas.SelectedRowTint))
            .border(
                Dimens.HairlineBorder,
                colors.primary.copy(alpha = NexaFlowAlphas.ChipBorder),
                RoundedCornerShape(50)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            style = NexaFlowTheme.type.button.forText(label),
            color = colors.primary,
            maxLines = 1
        )
    }
}
