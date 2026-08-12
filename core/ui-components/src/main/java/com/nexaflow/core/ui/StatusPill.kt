package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(name = "StatusPill", showBackground = true)
@Preview(name = "StatusPill (dark)", showBackground = true)
@Composable
private fun StatusPillPreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        StatusPill(
            text = "Active",
            background = Color(0xFFE4F4E9),
            contentColor = Color(0xFF006D3C)
        )
        StatusPill(
            text = "Off",
            background = Color(0xFFEEEEEE),
            contentColor = Color(0xFF555555)
        )
    }
}
