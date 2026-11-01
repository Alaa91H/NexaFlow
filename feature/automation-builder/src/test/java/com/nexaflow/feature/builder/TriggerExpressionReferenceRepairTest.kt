package com.nexaflow.feature.builder

import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import com.nexaflow.domain.workflow.TriggerExpressionValidator
import com.nexaflow.domain.workflow.removeTriggerReference
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advanced trigger-expression editor is no longer part of the builder, but
 * rules stored by older versions keep running, so the reference repair the
 * builder performs when a trigger is deleted must still be checked: dropping a
 * referenced trigger has to leave a definition the validator rejects instead of
 * one that silently points at a different trigger.
 */
class TriggerExpressionReferenceRepairTest {

    private val triggers = listOf(
        Trigger(TriggerType.SMS, emptyMap()),
        Trigger(TriggerType.CALENDAR, emptyMap()),
    )

    private fun sequence() = TriggerExpressionDefinitionV2(
        root = TriggerExpressionNodeV2.Sequence(0, 1, 60_000L)
    )

    @Test
    fun anUntouchedStoredRuleStaysValid() {
        assertTrue(TriggerExpressionValidator.validate(sequence(), triggers).isEmpty())
    }

    @Test
    fun removingAReferencedTriggerLeavesTheStoredRuleInvalid() {
        val invalidated = sequence().removeTriggerReference(0)
        assertTrue(
            TriggerExpressionValidator.validate(invalidated, triggers.drop(1)).isNotEmpty()
        )
    }
}
