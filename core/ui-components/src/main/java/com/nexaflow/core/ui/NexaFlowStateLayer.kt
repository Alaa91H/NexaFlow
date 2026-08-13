package com.nexaflow.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Centralized M3 state layer for custom rows and cards.
 *
 * Material 3 draws a translucent state layer (hover / focus / press / drag)
 * on top of the container. Rather than each component re-deriving opacity
 * values and ripples, this modifier implements the shared recipe:
 *
 * - [ripple] for press feedback (color = onSurface, bounded),
 * - hover / focus / press / drag overlays with the spec alphas
 *   (hover 0.08, focus 0.12, press 0.12, dragged 0.16) composited over the
 *   container color,
 * - a focus outline when keyboard-focusable.
 *
 * Components that previously hand-rolled `.clickable { }` migrate to this so
 * interaction semantics live in one place. Callers that need rounded clipping
 * should pass a `clip` modifier before this one (drawBehind is inset by it).
 */
fun Modifier.nexaFlowStateLayer(
    enabled: Boolean = true,
    onClick: () -> Unit,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    focusOutlineColor: Color? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focusColor = focusOutlineColor ?: MaterialTheme.colorScheme.primary
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val dragged by source.collectIsDraggedAsState()
    val layerAlpha = when {
        dragged -> 0.16f
        pressed -> 0.12f
        hovered -> 0.08f
        focused -> 0.12f
        else -> 0f
    }
    val layerColor by animateColorAsState(
        targetValue = if (layerAlpha == 0f) Color.Transparent
        else MaterialTheme.colorScheme.onSurface.copy(alpha = layerAlpha)
    )

    this
        .clickable(
            enabled = enabled,
            role = role,
            interactionSource = source,
            indication = ripple(color = MaterialTheme.colorScheme.onSurface),
            onClick = onClick
        )
        .then(
            if (focused) {
                Modifier.border(
                    width = 2.dp,
                    color = focusColor
                )
            } else {
                Modifier
            }
        )
        .drawBehind {
            if (layerColor != Color.Transparent) {
                clipRect {
                    drawRect(color = layerColor)
                }
            }
        }
}
