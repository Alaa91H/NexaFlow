package com.nexaflow.feature.builder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionEvaluatorV2
import com.nexaflow.domain.workflow.TriggerExpressionInput
import com.nexaflow.domain.workflow.TriggerExpressionHistoryEvent
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import com.nexaflow.domain.workflow.TriggerExpressionValidator

@Composable
internal fun TriggerExpressionPreview(
    definition: TriggerExpressionDefinitionV2,
    triggers: List<Trigger>
) {
    val issues = TriggerExpressionValidator.validate(definition, triggers)
    if (issues.isNotEmpty()) {
        Text(stringResource(R.string.trigger_expression_invalid), color = MaterialTheme.colorScheme.error)
        return
    }
    val result = triggerExpressionPreviewResult(definition, triggers)
    val label = if (result == ConditionResult.Satisfied) {
        stringResource(R.string.trigger_expression_preview_satisfied)
    } else {
        stringResource(R.string.trigger_expression_preview_not_satisfied)
    }
    Text(label, style = MaterialTheme.typography.bodySmall)
}

internal fun triggerExpressionPreviewResult(
    definition: TriggerExpressionDefinitionV2,
    triggers: List<Trigger>
): ConditionResult {
    if (TriggerExpressionValidator.validate(definition, triggers).isNotEmpty()) return ConditionResult.Unavailable
    val events = buildSet {
        fun visit(node: TriggerExpressionNodeV2) {
            when (node) {
                is TriggerExpressionNodeV2.Event -> add(node.index)
                is TriggerExpressionNodeV2.AllOf -> node.children.forEach(::visit)
                is TriggerExpressionNodeV2.AnyOf -> node.children.forEach(::visit)
                is TriggerExpressionNodeV2.Sequence -> add(node.secondIndex)
                is TriggerExpressionNodeV2.Count -> add(node.index)
                else -> Unit
            }
        }
        visit(definition.root)
    }
    val elapsedMs = 604_801_000L
    val history = buildList {
        fun visit(node: TriggerExpressionNodeV2) {
            when (node) {
                is TriggerExpressionNodeV2.Sequence -> add(
                    TriggerExpressionHistoryEvent(node.firstIndex, elapsedMs - minOf(node.withinMs, 1_000L), size + 1L)
                )
                is TriggerExpressionNodeV2.Count -> repeat((node.minimumCount - 1).coerceAtMost(63)) { offset ->
                    add(TriggerExpressionHistoryEvent(node.index, elapsedMs - (offset + 1L), size + 1L))
                }
                is TriggerExpressionNodeV2.AllOf -> node.children.forEach(::visit)
                is TriggerExpressionNodeV2.AnyOf -> node.children.forEach(::visit)
                else -> Unit
            }
        }
        visit(definition.root)
    }.take(64)
    val live = triggers.indices.filter { TriggerExpressionValidator.isStateReadable(triggers[it]) }
        .associateWith { ConditionResult.Satisfied }
    return TriggerExpressionEvaluatorV2.evaluate(
        TriggerExpressionInput(
            definition = definition,
            triggers = triggers,
            currentEventIndices = events,
            liveResults = live,
            elapsedRealtimeMs = elapsedMs,
            history = history
        )
    ).result
}
