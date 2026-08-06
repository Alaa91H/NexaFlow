package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun IconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    size: Int = 44
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color = containerColor, shape = RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
    }
}

@Preview(name = "IconBadge", showBackground = true)
@Preview(name = "IconBadge (dark)", showBackground = true)
@Composable
private fun IconBadgePreview() {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFF1B62B7)
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFF2FA84F)
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFFE5533D)
        )
    }
}
