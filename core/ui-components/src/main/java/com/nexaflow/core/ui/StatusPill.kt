package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.nexaflow.core.ui.Dimens.Space1
import com.nexaflow.core.ui.Dimens.Space2
import com.nexaflow.core.ui.Dimens.Space3

/**
 * A small rounded status label.
 *
 * Under the monochrome trait the status color is dropped and the label
 * falls back to the neutral surface, so a monochrome screen carries no
 * color signal it cannot explain.
 */
@Composable
fun StatusPill(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val resolvedBackground = NexaFlowTraitsDefaults.surfaceColor(background)
    val resolvedContent = NexaFlowTraitsDefaults.contentColor(contentColor, MaterialTheme.colorScheme.onSurface)

    Box(
        modifier = modifier
            .background(color = resolvedBackground, shape = RoundedCornerShape(50))
            .padding(horizontal = Space3, vertical = Space1)
    ) {
        Text(
            text = text,
            color = resolvedContent,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview(name = "StatusPill", showBackground = true)
@Preview(name = "StatusPill (dark)", showBackground = true)
@Composable
private fun StatusPillPreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space2)
    ) {
        StatusPill(
            text = "Active",
            background = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        StatusPill(
            text = "Off",
            background = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
