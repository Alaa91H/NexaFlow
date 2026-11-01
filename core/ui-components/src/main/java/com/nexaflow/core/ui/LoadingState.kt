package com.nexaflow.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas

/**
 * Loading skeleton (§6): the card shape with a 6% `onSurface` shimmer. It
 * keeps the dashboard's rhythm while data resolves instead of collapsing the
 * layout into a spinner.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    count: Int = 3,
    contentDescription: String? = null
) {
    val reduceMotion = isSystemReduceMotionEnabled()
    val shimmer = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val alpha by transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeletonAlpha"
        )
        alpha
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (contentDescription != null) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        repeat(count) {
            SkeletonCard(alphaScale = shimmer)
        }
    }
}

@Composable
private fun SkeletonCard(alphaScale: Float) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = NexaFlowAlphas.LoadingShimmer)
    NexaFlowCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.TileTextGap)
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.RoutineTile)
                    .background(fill.copy(alpha = fill.alpha * alphaScale), RoundedCornerShape(Dimens.RoutineTileRadius))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .background(fill.copy(alpha = fill.alpha * alphaScale), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .background(fill.copy(alpha = fill.alpha * alphaScale), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * Shared error state with an optional retry action. Announces itself to
 * screen readers via an assertive live region, so a failed load is reported
 * immediately (as opposed to the polite skeleton above).
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space6, vertical = Dimens.Space8)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(Dimens.Space8),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(text = retryLabel)
            }
        }
    }
}

@Preview(name = "LoadingState", showBackground = true)
@Composable
private fun LoadingStatePreview() {
    MaterialTheme { LoadingState() }
}
