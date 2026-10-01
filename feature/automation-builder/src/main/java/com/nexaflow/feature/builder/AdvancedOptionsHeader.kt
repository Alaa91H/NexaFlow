package com.nexaflow.feature.builder

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource

@Composable
internal fun AdvancedOptionsHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateLabel = stringResource(
        if (expanded) R.string.option_tier_expanded else R.string.option_tier_collapsed
    )
    TextButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            stateDescription = stateLabel
        }
    ) {
        Text(title)
        Text(if (expanded) "  ↑" else "  ↓")
    }
}
