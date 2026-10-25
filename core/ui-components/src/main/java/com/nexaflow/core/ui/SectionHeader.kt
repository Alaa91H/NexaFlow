package com.nexaflow.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A section header.
 *
 * The title carries the weight, an optional leading icon gives the section
 * a visual anchor, and an optional trailing action sits at the end. Under
 * the monochrome trait the icon takes the theme's accent instead of its own
 * color, so the header still reads as part of the group.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NexaFlowTraitsDefaults.accentColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Preview(name = "SectionHeader", showBackground = true)
@Preview(name = "SectionHeader (dark)", showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    MaterialTheme {
        SectionHeader(
            text = "ROUTINES",
            trailing = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                }
            }
        )
    }
}
