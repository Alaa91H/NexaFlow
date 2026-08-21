package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.Dimens.Space4

/**
 * Google-style filled card. Borderless, 16dp radius, resting on the tonal
 * surface-container tier — the same recipe Google apps (Tasks, Keep, Clock)
 * use for content blocks.
 */
@Composable
fun NexaFlowCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    alternatingIndex: Int? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContainerColor = containerColor
        ?: alternatingIndex?.let { alternatingSurfaceColor(it) }
        ?: MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Space4), content = content)
    }
}

@Preview(name = "NexaFlowCard", showBackground = true)
@Preview(name = "NexaFlowCard (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "NexaFlowCard (RTL)", showBackground = true, locale = "ar")
@Composable
private fun NexaFlowCardPreview() {
    MaterialTheme {
        NexaFlowCard {
            Text(
                text = "Morning Mode",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "When · Time · Then · 3 actions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
