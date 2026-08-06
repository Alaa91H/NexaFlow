package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
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

@Composable
fun NexaFlowCard(
    modifier: Modifier = Modifier,
    border: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
