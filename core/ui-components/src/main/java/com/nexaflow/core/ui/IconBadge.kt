package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.nexaflow.core.ui.Dimens.Space3

/**
 * Google-style circular icon badge — the tonal circle Google apps use to
 * hold an action icon (40dp on settings rows, 56dp on key actions).
 */
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
            .background(color = containerColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
    }
}

@Preview(name = "IconBadge", showBackground = true)
@Preview(name = "IconBadge (dark)", showBackground = true)
@Composable
private fun IconBadgePreview() {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space3)) {
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFF0B57D0)
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFF006D3C)
        )
        IconBadge(
            icon = Icons.Filled.Bolt,
            containerColor = Color(0xFFBA1A1A)
        )
    }
}
