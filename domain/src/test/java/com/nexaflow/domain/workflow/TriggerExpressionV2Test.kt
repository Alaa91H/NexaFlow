package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TriggerExpressionV2Test {

    private fun automation() = Automation(
        id = "expression-v2",
        name = "Expression v2",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = false,
        triggers = emptyList(),
        actions = emptyList(),
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun legacyAutomationRoundTripsAnEmptyExpressionSlotAndImmutableRevision() {
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(Automation.serializer(), automation())

        assertTrue(encoded.contains("\"workflowRevision\":1"))
        assertTrue(encoded.contains("\"triggerExpressionV2\":null"))

        val decoded = json.decodeFromString(Automation.serializer(), encoded)
        assertEquals(automation().id, decoded.id)
        assertEquals(1L, decoded.workflowRevision)
        assertNull(decoded.triggerExpressionV2)
    }

    private val stateTriggers = listOf(
        Trigger(TriggerType.BATTERY, emptyMap()),
        Trigger(TriggerType.WIFI_CONNECTED, emptyMap()),
        Trigger(TriggerType.DARK_MODE, emptyMap())
    )

    private fun issues(root: TriggerExpressionNodeV2, triggers: List<Trigger> = stateTriggers,
                       version: Int = 2) = TriggerExpressionValidator.validate(
        TriggerExpressionDefinitionV2(version, root), triggers
    )

    @Test
    fun acceptsNestedAllAndAnyExpression() {
        val root = TriggerExpressionNodeV2.AnyOf(listOf(
            TriggerExpressionNodeV2.AllOf(listOf(TriggerExpressionNodeV2.State(0), TriggerExpressionNodeV2.State(1))),
            TriggerExpressionNodeV2.State(2)
        ))
        assertTrue(issues(root).isEmpty())
    }

    @Test
    fun rejectsWrongKindDuplicateAndOutOfRangeReferences() {
        val mixedTriggers = listOf(Trigger(TriggerType.SMS, emptyMap()), stateTriggers[1])
        val root = TriggerExpressionNodeV2.AllOf(listOf(
            TriggerExpressionNodeV2.State(0), TriggerExpressionNodeV2.State(1), TriggerExpressionNodeV2.State(0),
            TriggerExpressionNodeV2.State(99)
        ))
        val codes = issues(root, mixedTriggers).map { it.code }
        assertTrue(codes.contains(TriggerExpressionIssueCode.WRONG_TRIGGER_KIND))
        assertTrue(codes.contains(TriggerExpressionIssueCode.DUPLICATE_REFERENCE))
        assertTrue(codes.contains(TriggerExpressionIssueCode.INVALID_REFERENCE))
    }

    @Test
    fun rejectsEmptyGroupsUnsupportedVersionsDepthAndNodeOverflow() {
        assertTrue(issues(TriggerExpressionNodeV2.AllOf(emptyList())).any { it.code == TriggerExpressionIssueCode.EMPTY_GROUP })
        assertTrue(issues(TriggerExpressionNodeV2.State(0), version = 3).any { it.code == TriggerExpressionIssueCode.UNSUPPORTED_SCHEMA })
        var deep: TriggerExpressionNodeV2 = TriggerExpressionNodeV2.State(0)
        repeat(9) { deep = TriggerExpressionNodeV2.AllOf(listOf(deep)) }
        assertTrue(issues(deep).any { it.code == TriggerExpressionIssueCode.TOO_DEEP })
        val large = TriggerExpressionNodeV2.AllOf((0..64).map { TriggerExpressionNodeV2.State(it % 3) })
        assertTrue(issues(large).any { it.code == TriggerExpressionIssueCode.TOO_MANY_NODES })
    }

    @Test
    fun rejectsInvalidTemporalWindowsCountsAndEventOnlyNot() {
        val events = listOf(Trigger(TriggerType.SMS, emptyMap()))
        assertTrue(issues(TriggerExpressionNodeV2.Sequence(0, 0, 1), events).any { it.code == TriggerExpressionIssueCode.DUPLICATE_REFERENCE })
        assertTrue(issues(TriggerExpressionNodeV2.Count(0, 0, 1), events).any { it.code == TriggerExpressionIssueCode.INVALID_COUNT })
        assertTrue(issues(TriggerExpressionNodeV2.Count(0, 1001, 1), events).any { it.code == TriggerExpressionIssueCode.INVALID_COUNT })
        assertTrue(issues(TriggerExpressionNodeV2.Count(0, 2, 604_800_001), events).any { it.code == TriggerExpressionIssueCode.INVALID_WINDOW })
        assertTrue(issues(TriggerExpressionNodeV2.NotState(0), events).any { it.code == TriggerExpressionIssueCode.WRONG_TRIGGER_KIND })
        assertFalse(issues(TriggerExpressionNodeV2.Count(0, 1000, 604_800_000), events).isNotEmpty())
    }

    @Test
    fun temporalSourcesRequireMonitorProvidedStableOccurrenceIds() {
        assertTrue(issues(TriggerExpressionNodeV2.Count(0, 2, 10), listOf(
            Trigger(TriggerType.BATTERY, emptyMap())
        )).any { it.code == TriggerExpressionIssueCode.UNSUPPORTED_TEMPORAL_SOURCE })
        assertFalse(issues(TriggerExpressionNodeV2.Count(0, 2, 10), listOf(
            Trigger(TriggerType.CHARGER, emptyMap())
        )).any { it.code == TriggerExpressionIssueCode.UNSUPPORTED_TEMPORAL_SOURCE })
    }

    @Test
    fun eventLeafOnlyAcceptsCurrentOccurrenceAndUnknownStateStaysUnknown() {
        val triggers = listOf(Trigger(TriggerType.SMS, emptyMap()), stateTriggers[0])
        val root = TriggerExpressionNodeV2.AllOf(listOf(TriggerExpressionNodeV2.Event(0), TriggerExpressionNodeV2.State(1)))
        val def = TriggerExpressionDefinitionV2(root = root)
        val noEvent = TriggerExpressionEvaluatorV2.evaluate(TriggerExpressionInput(def, triggers, emptySet(), mapOf(1 to ConditionResult.Satisfied), 10, emptyList()))
        assertEquals(ConditionResult.Unsatisfied, noEvent.result)
        val unknown = TriggerExpressionEvaluatorV2.evaluate(TriggerExpressionInput(def, triggers, setOf(0), emptyMap(), 10, emptyList()))
        assertEquals(ConditionResult.Unknown, unknown.result)
    }

    @Test
    fun sequenceIsOrderedAndBoundedAndCountExpires() {
        val events = listOf(Trigger(TriggerType.SMS, emptyMap()), Trigger(TriggerType.CALENDAR, emptyMap()))
        val sequence = TriggerExpressionDefinitionV2(root = TriggerExpressionNodeV2.Sequence(0, 1, 50))
        val ordered = listOf(TriggerExpressionHistoryEvent(0, 100, 1), TriggerExpressionHistoryEvent(1, 150, 2))
        assertEquals(ConditionResult.Satisfied, TriggerExpressionEvaluatorV2.evaluate(TriggerExpressionInput(sequence, events, emptySet(), emptyMap(), 150, ordered)).result)
        val reverseOrder = listOf(TriggerExpressionHistoryEvent(1, 100, 1), TriggerExpressionHistoryEvent(0, 150, 2))
        assertEquals(ConditionResult.Unsatisfied, TriggerExpressionEvaluatorV2.evaluate(TriggerExpressionInput(sequence, events, emptySet(), emptyMap(), 150, reverseOrder)).result)
        val count = TriggerExpressionDefinitionV2(root = TriggerExpressionNodeV2.Count(0, 2, 10))
        val expired = listOf(TriggerExpressionHistoryEvent(0, 80, 1))
        assertEquals(ConditionResult.Unsatisfied, TriggerExpressionEvaluatorV2.evaluate(TriggerExpressionInput(count, events, setOf(0), emptyMap(), 100, expired)).result)
        val oneCurrentEvent = listOf(TriggerExpressionHistoryEvent(0, 100, 2))
        assertEquals(ConditionResult.Unsatisfied, TriggerExpressionEvaluatorV2.evaluate(
            TriggerExpressionInput(count, events, setOf(0), emptyMap(), 100, oneCurrentEvent)
        ).result)
    }

    @Test
    fun movingAndRemovingTriggersKeepsReferencesSafe() {
        val expression = TriggerExpressionDefinitionV2(root = TriggerExpressionNodeV2.AllOf(listOf(
            TriggerExpressionNodeV2.State(0), TriggerExpressionNodeV2.State(2)
        )))
        val moved = expression.moveTriggerReference(0, 2)
        assertEquals(listOf(1, 2), (moved.root as TriggerExpressionNodeV2.AllOf).children.map {
            (it as TriggerExpressionNodeV2.State).index
        }.sorted())
        assertEquals(Int.MIN_VALUE, expression.removeTriggerReference(0).schemaVersion)
        val shifted = expression.removeTriggerReference(1)
        assertEquals(listOf(0, 1), (shifted.root as TriggerExpressionNodeV2.AllOf).children.map {
            (it as TriggerExpressionNodeV2.State).index
        }.sorted())
    }
}
