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
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.Dimens.Space1
import com.nexaflow.core.ui.Dimens.Space2
import com.nexaflow.core.ui.Dimens.Space3

@Composable
fun StatusPill(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(50))
            .padding(horizontal = Space3, vertical = Space1)
    ) {
        Text(
            text = text,
            color = contentColor,
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
            background = Color(0xFFE4F4E9),
            contentColor = Color(0xFF006D3C)
        )
        StatusPill(
            text = "Off",
            background = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
