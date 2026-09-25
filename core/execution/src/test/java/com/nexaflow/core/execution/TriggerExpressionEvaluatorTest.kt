package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerExpressionEvaluatorTest {

    private fun automation(triggers: List<Trigger>): Automation = Automation(
        id = "expression-test",
        name = "Expression test",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = triggers,
        actions = emptyList(),
        triggerMatch = TriggerMatchMode.ALL,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun currentEventPlusSatisfiedLiveStatePassesAll() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, mapOf("contains" to "night")),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence.single(
                triggerIndex = 0,
                occurredAtEpochMs = 100L,
                sourceId = "sms",
            ),
            evaluatedAtEpochMs = 101L,
            stateReader = { trigger ->
                if (trigger.type == TriggerType.DARK_MODE) {
                    ConditionResult.Satisfied
                } else {
                    ConditionResult.Unknown
                }
            },
        )

        assertEquals(
            listOf(ConditionResult.Satisfied, ConditionResult.Satisfied),
            snapshot.results,
        )
        assertEquals(
            listOf(TriggerEvidenceSource.CURRENT_EVENT, TriggerEvidenceSource.LIVE_STATE),
            snapshot.evidence.map { it.source },
        )
        assertTrue(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

    @Test
    fun pastEventIsNotCarriedForwardAsTruth() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, emptyMap()),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = null,
            evaluatedAtEpochMs = 200L,
            stateReader = { trigger ->
                if (trigger.type == TriggerType.SMS) {
                    ConditionResult.Unknown
                } else {
                    ConditionResult.Satisfied
                }
            },
        )

        assertEquals(
            listOf(ConditionResult.Unknown, ConditionResult.Satisfied),
            snapshot.results,
        )
        assertFalse(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

    @Test
    fun stateReadableTriggerStillUsesLiveStateWhenItsMonitorFires() = runBlocking {
        val task = automation(
            listOf(Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")))
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence.single(
                triggerIndex = 0,
                occurredAtEpochMs = 300L,
                sourceId = "settings",
            ),
            evaluatedAtEpochMs = 301L,
            stateReader = { ConditionResult.Unsatisfied },
        )

        assertEquals(ConditionResult.Unsatisfied, snapshot.results.single())
        assertEquals(TriggerEvidenceSource.LIVE_STATE, snapshot.evidence.single().source)
        assertFalse(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

    @Test
    fun onePhysicalEventMaySatisfyMultipleMatchingEventFilters() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, mapOf("from" to "123")),
                Trigger(TriggerType.SMS, mapOf("contains" to "night")),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence(
                matchedTriggerIndices = setOf(0, 1),
                occurredAtEpochMs = 400L,
                sourceId = "sms",
            ),
            evaluatedAtEpochMs = 401L,
            stateReader = { error("event evidence should avoid a live read") },
        )

        assertEquals(
            listOf(ConditionResult.Satisfied, ConditionResult.Satisfied),
            snapshot.results,
        )
        assertTrue(snapshot.evidence.all { it.source == TriggerEvidenceSource.CURRENT_EVENT })
        assertTrue(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

    @Test
    fun separateEventOnlySiblingRemainsUnverifiableWithoutCorrelationWindow() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, mapOf("from" to "123")),
                Trigger(TriggerType.SMS, mapOf("from" to "456")),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence.single(
                triggerIndex = 0,
                occurredAtEpochMs = 500L,
                sourceId = "sms",
            ),
            evaluatedAtEpochMs = 501L,
            stateReader = { ConditionResult.Unknown },
        )

        assertEquals(
            listOf(ConditionResult.Satisfied, ConditionResult.Unknown),
            snapshot.results,
        )
        assertFalse(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

    @Test
    fun evidenceDiagnosticsAreTypedAndExcludeTriggerConfigValues() = runBlocking {
        val secretValue = "private-sms-content"
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, mapOf("contains" to secretValue)),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence.single(
                triggerIndex = 0,
                occurredAtEpochMs = 600L,
                sourceId = "sms",
            ),
            evaluatedAtEpochMs = 601L,
            stateReader = { trigger ->
                if (trigger.type == TriggerType.DARK_MODE) {
                    ConditionResult.Unsatisfied
                } else {
                    ConditionResult.Unknown
                }
            },
        )

        assertEquals(TriggerBlockKind.UNSATISFIED, snapshot.blockKind())
        assertEquals(
            ConditionResult.Unsatisfied,
            snapshot.decision(TriggerMatchMode.ALL),
        )
        val detail = snapshot.diagnosticDetail()
        assertTrue(detail.contains("#0:SMS=SATISFIED@CURRENT_EVENT"))
        assertTrue(detail.contains("#1:DARK_MODE=UNSATISFIED@LIVE_STATE"))
        assertFalse(detail.contains(secretValue))
    }

    @Test
    fun errorDominatesUnknownWhenNoConfirmedFalseExists() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, emptyMap()),
                Trigger(TriggerType.NOTIFICATION, emptyMap()),
            )
        )

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = null,
            evaluatedAtEpochMs = 700L,
            stateReader = { trigger ->
                if (trigger.type == TriggerType.SMS) {
                    ConditionResult.Unknown
                } else {
                    ConditionResult.Error("sensitive-provider-detail")
                }
            },
        )

        assertEquals(TriggerBlockKind.ERROR, snapshot.blockKind())
        assertFalse(snapshot.diagnosticDetail().contains("sensitive-provider-detail"))
        assertTrue(snapshot.diagnosticDetail().contains("ERROR@LIVE_STATE"))
    }


    @Test
    fun legacyWorkflowDoesNotSilentlyEnableCurrentEventEvidence() = runBlocking {
        val task = automation(
            listOf(
                Trigger(TriggerType.SMS, mapOf("contains" to "night")),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON")),
            )
        ).copy(workflowVersion = Automation.LEGACY_TRIGGER_SEMANTICS_VERSION)

        val snapshot = TriggerExpressionEvaluator.evaluate(
            automation = task,
            occurrence = TriggerOccurrence.single(
                triggerIndex = 0,
                occurredAtEpochMs = 800L,
                sourceId = "sms",
            ),
            evaluatedAtEpochMs = 801L,
            stateReader = { trigger ->
                if (trigger.type == TriggerType.DARK_MODE) {
                    ConditionResult.Satisfied
                } else {
                    ConditionResult.Unknown
                }
            },
        )

        assertEquals(
            listOf(ConditionResult.Unknown, ConditionResult.Satisfied),
            snapshot.results,
        )
        assertTrue(snapshot.evidence.all { it.source == TriggerEvidenceSource.LIVE_STATE })
        assertFalse(snapshot.isSatisfied(TriggerMatchMode.ALL))
    }

}
