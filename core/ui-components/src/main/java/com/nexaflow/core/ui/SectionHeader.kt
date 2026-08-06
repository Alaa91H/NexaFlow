package com.nexaflow.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

/**
 * Samsung-style section header. Shows the section title with an optional
 * trailing action (e.g. a "+" add button) aligned to the end.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
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
