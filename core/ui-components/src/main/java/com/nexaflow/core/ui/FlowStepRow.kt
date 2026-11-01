package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.eyebrowText
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.core.ui.theme.forUserText

/**
 * WHEN → THEN → EXIT, made visible. One component renders every flow step —
 * the dashboard's expanded card, the builder summary and task details — so
 * the structure of an automation reads the same wherever it appears.
 *
 * Label column 44dp, chips wrap with 6dp gaps, chips are one line each and
 * use a 7% `onSurface` fill with a 12dp radius.
 */
@Composable
fun FlowStepRow(
    label: String,
    chips: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        Text(
            text = eyebrowText(label),
            style = NexaFlowTheme.type.flowLabel.forText(label),
            color = NexaFlowTheme.colors.accentLabel,
            maxLines = 1,
            modifier = Modifier
                .width(Dimens.FlowLabelWidth)
                .padding(top = 6.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (chips.isEmpty()) {
                FlowChip(text = "—")
            } else {
                chips.forEach { chip -> FlowChip(text = chip) }
            }
        }
    }
}

/** A single flow chip: 6×10 padding, 12dp radius, 7% fill. */
@Composable
fun FlowChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.FlowChipFill),
                shape = RoundedCornerShape(Dimens.FlowChipRadius)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = NexaFlowTheme.type.flowChip.forUserText(text),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
