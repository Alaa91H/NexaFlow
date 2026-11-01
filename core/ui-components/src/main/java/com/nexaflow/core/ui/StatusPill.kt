package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText

/**
 * The states worth surfacing (§6). There is deliberately no "idle" variant:
 * an idle routine shows nothing, which is what keeps the list calm.
 */
enum class StatusPillVariant {
    Running,
    NeedsPermission,
    Error
}

/**
 * Dot + label status pill: 6dp dot, 3dp × 9dp padding, 11.5sp/700 label.
 * Running uses the success role at 17% fill; permission and error states use
 * the M3 error family so they read as problems, not as decoration.
 */
@Composable
fun StatusPill(
    text: String,
    variant: StatusPillVariant,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (variant) {
        StatusPillVariant.Running ->
            NexaFlowTheme.colors.success.copy(alpha = NexaFlowAlphas.SuccessPillFill) to
                NexaFlowTheme.colors.success
        StatusPillVariant.NeedsPermission,
        StatusPillVariant.Error ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(content, CircleShape)
        )
        Text(
            text = text,
            color = content,
            style = NexaFlowTheme.type.statusPill.forText(text)
        )
    }
}

/**
 * Raw-colour pill retained for call sites that already compute their own
 * semantic pair (builder previews, import summaries). New UI should prefer
 * [StatusPill] with a [StatusPillVariant].
 */
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
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space1)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = NexaFlowTheme.type.statusPill.forText(text)
        )
    }
}
