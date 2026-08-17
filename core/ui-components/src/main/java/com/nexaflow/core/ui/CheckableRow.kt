package com.nexaflow.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.nexaflow.core.ui.Dimens.Space1
import com.nexaflow.core.ui.Dimens.Space3

/**
 * Google-style single-choice row: the content on the left, and a trailing
 * primary-coloured check mark when [selected]. Used by single-choice pickers
 * (Settings-style dialogs) instead of radio buttons — the Google 2026 look.
 *
 * Accessibility is preserved: the row carries radio semantics via
 * [Modifier.selectable], so screen readers announce it as a checked/unchecked
 * radio option exactly like the old RadioButton, while the visual is a
 * checkmark row.
 */
@Composable
fun CheckableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = Space1, vertical = Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space3)
    ) {
        content()
        Spacer(modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(name = "CheckableRow", showBackground = true)
@Composable
private fun CheckableRowPreview() {
    NexaFlowTheme {
        CheckableRow(selected = true, onClick = {}) {
            Text("Sample option")
        }
    }
}

@Preview(name = "CheckableRow – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CheckableRowDarkPreview() {
    NexaFlowTheme {
        CheckableRow(selected = false, onClick = {}) {
            Text("Sample option")
        }
    }
}
