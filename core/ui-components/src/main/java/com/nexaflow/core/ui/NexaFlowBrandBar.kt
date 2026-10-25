package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowGradients
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults

/**
 * The app's top bar.
 *
 * Two shapes, one bar: a screen that is a top-level destination shows the
 * brand mark and a row of round action buttons; a screen that was pushed
 * onto the stack shows a back button and a title. The round filled buttons
 * are the bar's signature — they read as a separate layer above the canvas
 * rather than as part of it.
 */
@Composable
fun NexaFlowBrandBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showBrand: Boolean = true,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space4, vertical = Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        when {
            onBack != null -> {
                NexaFlowRoundIconButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.back)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            showBrand -> {
                NexaFlowBrandMark()
            }
        }

        if (title != null) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (onBack == null) {
            Box(modifier = Modifier.weight(1f))
        }

        actions()
    }
}

/**
 * The brand mark: the flow glyph on the brand gradient, next to the
 * wordmark.
 */
@Composable
fun NexaFlowBrandMark(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true
) {
    val gradient = NexaFlowGradients.brandSolid
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nexaflow_flow),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showWordmark) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A round, filled action button — the app's one button shape.
 *
 * @param accent Whether the button wears the accent container instead of the neutral one.
 */
@Composable
fun NexaFlowRoundIconButton(
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val container = if (accent) colors.primaryContainer else colors.surfaceContainerHigh
    val shape = CircleShape
    val hairline = NexaFlowTraitsDefaults.outline(
        color = NexaFlowTraitsDefaults.borderColor(colors.outlineVariant),
        width = 1.dp
    )

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .background(container)
            .then(
                if (hairline != null) Modifier.border(hairline, shape) else Modifier
            )
    ) {
        IconButton(onClick = onClick) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Preview(name = "NexaFlowBrandBar", showBackground = true)
@Preview(name = "NexaFlowBrandBar (dark)", showBackground = true)
@Composable
private fun NexaFlowBrandBarPreview() {
    MaterialTheme {
        Column {
            NexaFlowBrandBar(
                actions = {
                    NexaFlowRoundIconButton(onClick = {}, contentDescription = null) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Home,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            NexaFlowBrandBar(
                title = "Task details",
                subtitle = "3 actions",
                showBrand = false,
                onBack = {}
            )
        }
    }
}
