package com.nexaflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.eyebrowText
import com.nexaflow.core.ui.theme.forText

/** Number of 4-hour buckets a "Today" sparkline always shows. */
const val SPARKLINE_BUCKETS: Int = 6

/**
 * Buckets execution hours (0–23) into the six 4-hour windows of a day, in
 * order: 00–04, 04–08, 08–12, 12–16, 16–20, 20–24.
 */
fun executionBuckets(hoursOfDay: Collection<Int>): List<Int> {
    val buckets = IntArray(SPARKLINE_BUCKETS)
    hoursOfDay.forEach { hour ->
        if (hour in 0..23) buckets[hour / 4]++
    }
    return buckets.toList()
}

/** Bar heights as a 0..1 fraction of the day's busiest bucket. */
fun sparklineFractions(counts: List<Int>): List<Float> {
    val max = counts.maxOrNull() ?: 0
    return if (max <= 0) counts.map { 0f } else counts.map { it / max.toFloat() }
}

/**
 * Bento statistic card (§6): eyebrow label, 28sp/800 value, 12.5sp caption,
 * and — for the "Today" variant — a six-bar sparkline (4dp gaps, 22dp max
 * height, 3dp bar radius, empty buckets at 12% primary).
 */
@Composable
fun BentoStatCard(
    eyebrow: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    sparkline: List<Int>? = null,
    sparklineDescription: String? = null
) {
    NexaFlowCard(modifier = modifier) {
        Text(
            text = eyebrowText(eyebrow),
            style = NexaFlowTheme.type.eyebrow.forText(eyebrow),
            color = NexaFlowTheme.colors.accentLabel
        )
        Text(
            text = value,
            style = NexaFlowTheme.type.bentoValue.forText(value),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Dimens.Space1)
        )
        // §9: at font scale 200% the caption wraps instead of losing its tail
        // ("No upcoming runs" was truncating to "No"). Two lines is the card's
        // whole budget, and the bento row is top-aligned so both cards grow.
        Text(
            text = caption,
            style = NexaFlowTheme.type.meta.forText(caption),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (sparkline != null) {
            Sparkline(
                counts = sparkline,
                description = sparklineDescription,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun Sparkline(
    counts: List<Int>,
    description: String?,
    modifier: Modifier = Modifier
) {
    val fractions = sparklineFractions(counts)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .semantics {
                if (description != null) contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
        verticalAlignment = Alignment.Bottom
    ) {
        fractions.forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((22.dp * fraction).coerceAtLeast(2.dp))
                    .background(
                        color = if (fraction == 0f) primary.copy(alpha = 0.12f) else primary,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Preview(name = "BentoStatCard", showBackground = true)
@Composable
private fun BentoStatCardPreview() {
    Column {
        BentoStatCard(
            eyebrow = "Today",
            value = "18",
            caption = "runs · 2h 14m saved",
            sparkline = listOf(1, 3, 0, 6, 4, 2),
            sparklineDescription = "18 runs today"
        )
    }
}
