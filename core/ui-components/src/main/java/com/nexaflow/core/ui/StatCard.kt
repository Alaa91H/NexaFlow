package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.nexaflow.core.ui.theme.NexaFlowGradients

/**
 * A statistic with its label.
 *
 * The value is set in the headline weight so a row of statistics reads as a
 * row of numbers first and a row of words second.
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    NexaFlowCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
        ) {
            if (icon != null) {
                NexaFlowGradientIcon(
                    icon = icon,
                    gradient = NexaFlowGradients.items.first(),
                    size = 32
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "StatCard", showBackground = true)
@Preview(name = "StatCard (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatCardPreview() {
    MaterialTheme {
        StatCard(
            value = "12",
            label = "Active routines",
            icon = Icons.Filled.Bolt
        )
    }
}
