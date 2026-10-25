package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowGradients
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * A single number with its label.
 *
 * The short gradient bar above the value is the tile's signature: it gives
 * the row a consistent visual rhythm and lets a set of tiles read as one
 * band rather than as separate cards.
 */
@Composable
fun NexaFlowStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    gradient: List<androidx.compose.ui.graphics.Color> = NexaFlowGradients.items.first()
) {
    val accent = NexaFlowTraitsDefaults.accentColor(gradient.first())
    val end = NexaFlowTraitsDefaults.accentColor(gradient.last())
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(26.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.linearGradient(listOf(accent, end)))
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Preview(name = "NexaFlowStatTile", showBackground = true)
@Preview(name = "NexaFlowStatTile (dark)", showBackground = true)
@Composable
private fun NexaFlowStatTilePreview() {
    MaterialTheme {
        RowPreview()
    }
}

@Composable
private fun RowPreview() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(Dimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        NexaFlowStatTile(value = "12", label = "Succeeded", modifier = Modifier.fillMaxWidth(0.33f))
        NexaFlowStatTile(value = "0", label = "Failed", modifier = Modifier.fillMaxWidth(0.33f))
        NexaFlowStatTile(value = "98%", label = "Success rate", modifier = Modifier.fillMaxWidth(0.33f))
    }
}
