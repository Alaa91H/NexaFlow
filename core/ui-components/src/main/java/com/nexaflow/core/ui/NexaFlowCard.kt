package com.nexaflow.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas

/**
 * The single content surface of Ink Glass (§6): 24dp radius,
 * `surfaceContainerLow`, a 1dp hairline, 14dp vertical / 16dp horizontal
 * padding and no elevation. Category meaning lives in the icon tile, so cards
 * themselves are uniform — the old alternating grey surfaces are gone.
 */
@Composable
fun NexaFlowCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    radius: Dp = Dimens.CardRadius,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimens.Space4,
        vertical = Dimens.TileTextGap
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            Dimens.HairlineBorder,
            MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.HairlineBorder)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
