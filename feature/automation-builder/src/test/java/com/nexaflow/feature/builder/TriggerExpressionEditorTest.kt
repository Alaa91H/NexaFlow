package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionEvaluatorV2
import com.nexaflow.domain.workflow.TriggerExpressionHistoryEvent
import com.nexaflow.domain.workflow.TriggerExpressionInput
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import com.nexaflow.domain.workflow.removeTriggerReference
import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerExpressionEditorTest {
    @Test
    fun sequencePreviewMatchesTheSharedRuntimeEvaluator() {
        val triggers = listOf(
            Trigger(TriggerType.SMS, emptyMap()),
            Trigger(TriggerType.CALENDAR, emptyMap()),
        )
        val expression = TriggerExpressionDefinitionV2(
            root = TriggerExpressionNodeV2.Sequence(0, 1, 60_000L)
        )
        val preview = triggerExpressionPreviewResult(expression, triggers)
        val elapsedMs = 604_801_000L
        val runtime = TriggerExpressionEvaluatorV2.evaluate(
            TriggerExpressionInput(
                definition = expression,
                triggers = triggers,
                currentEventIndices = setOf(1),
                liveResults = emptyMap(),
                elapsedRealtimeMs = elapsedMs,
                history = listOf(TriggerExpressionHistoryEvent(0, elapsedMs - 1_000L, 1L)),
            )
        ).result
        assertEquals(ConditionResult.Satisfied, preview)
        assertEquals(runtime, preview)
    }

    @Test
    fun removingAnReferencedTriggerMakesTheDraftFailClosed() {
        val triggers = listOf(
            Trigger(TriggerType.SMS, emptyMap()),
            Trigger(TriggerType.CALENDAR, emptyMap()),
        )
        val expression = TriggerExpressionDefinitionV2(
            root = TriggerExpressionNodeV2.Sequence(0, 1, 60_000L)
        )
        val invalidated = expression.removeTriggerReference(0)
        assertEquals(1, triggers.size - 1)
        assertEquals(ConditionResult.Unavailable, triggerExpressionPreviewResult(invalidated, triggers.drop(1)))
    }
}
