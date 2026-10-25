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
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * The app's content card.
 *
 * A filled card on the tonal surface-container tier, with a hairline edge
 * that the appearance settings can turn off. The edge is what separates a
 * card from the canvas on a near-black surface, where a tonal step alone
 * barely reads.
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

    val hairline = NexaFlowTraitsDefaults.outline(
        color = NexaFlowTraitsDefaults.borderColor(MaterialTheme.colorScheme.outlineVariant),
        width = 1.dp
    )

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = resolvedContainerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = NexaFlowTraitsDefaults.cardElevation(0.dp)
        ),
        border = hairline
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
