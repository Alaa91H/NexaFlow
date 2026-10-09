package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.core.datastore.TriggerExpressionHistoryStore
import com.nexaflow.core.datastore.TriggerHistoryStatus
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionEvaluation
import com.nexaflow.domain.workflow.TriggerExpressionEvaluatorV2
import com.nexaflow.domain.workflow.TriggerExpressionHistoryEvent
import com.nexaflow.domain.workflow.TriggerExpressionInput
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import com.nexaflow.domain.workflow.TriggerExpressionValidator

/** Adapter from one ephemeral monitor occurrence and live Android reads into the pure shared evaluator. */
class TriggerExpressionRuntimeEvaluator(
    private val historyStore: TriggerExpressionHistoryStore?
) {
    suspend fun evaluate(
        context: Context,
        automation: Automation,
        occurrence: TriggerOccurrence?,
        elapsedRealtimeMs: Long
    ): TriggerExpressionEvaluation {
        val definition = automation.triggerExpressionV2
            ?: return TriggerExpressionEvaluation(ConditionResult.Unavailable, invalid = true)
        if (TriggerExpressionValidator.validate(definition, automation.triggers).isNotEmpty()) {
            return TriggerExpressionEvaluation(ConditionResult.Unavailable, invalid = true)
        }
        val temporalIndices = temporalIndices(definition)
        val stateIndices = stateIndices(definition)
        val liveReadIndices = stateIndices + temporalIndices.filter {
            TriggerExpressionValidator.isStateReadable(automation.triggers[it])
        }
        val live = buildMap {
            liveReadIndices.forEach { index ->
                put(index, TriggerStateEvaluator.evaluateTriggerState(context, automation.triggers[index]))
            }
        }
        val permissionUncertainTemporalIndices = temporalIndices.filterTo(mutableSetOf()) { index ->
            TriggerExpressionValidator.isStateReadable(automation.triggers[index]) &&
                live[index] !in setOf(ConditionResult.Satisfied, ConditionResult.Unsatisfied)
        }
        if (permissionUncertainTemporalIndices.isNotEmpty()) {
            historyStore?.clearSourcePermissionScope(automation.id, permissionUncertainTemporalIndices)
            return TriggerExpressionEvaluation(ConditionResult.Unavailable)
        }
        var history = emptyList<TriggerExpressionHistoryEvent>()
        if (temporalIndices.isNotEmpty()) {
            val matchedTemporalIndices = occurrence?.matchedTriggerIndices.orEmpty().intersect(temporalIndices)
            val sourceId = occurrence?.sourceId
            val eventId = occurrence?.eventId
            if (historyStore == null) return TriggerExpressionEvaluation(ConditionResult.Unavailable)
            if (matchedTemporalIndices.isNotEmpty()) {
                if (sourceId == null || eventId == null) return TriggerExpressionEvaluation(ConditionResult.Unavailable)
                if (matchedTemporalIndices.any { index ->
                        sourceId !in stableSourceIds(automation.triggers[index].type)
                    }
                ) return TriggerExpressionEvaluation(ConditionResult.Unavailable)
                val stored = historyStore.recordAndRead(
                    automationId = automation.id,
                    workflowRevision = automation.workflowRevision,
                    eventIndices = matchedTemporalIndices.sorted(),
                    elapsedMs = elapsedRealtimeMs,
                    sourceId = sourceId,
                    stableEventId = eventId
                )
                if (stored.status in setOf(
                        TriggerHistoryStatus.UNAVAILABLE,
                        TriggerHistoryStatus.CAPACITY_REACHED,
                        TriggerHistoryStatus.CLOCK_RESET,
                        TriggerHistoryStatus.CORRUPT_RESET,
                        TriggerHistoryStatus.DUPLICATE
                    )) return TriggerExpressionEvaluation(ConditionResult.Unavailable)
                history = stored.events
            } else {
                val stored = historyStore.read(
                    automationId = automation.id,
                    workflowRevision = automation.workflowRevision,
                    elapsedMs = elapsedRealtimeMs,
                )
                if (stored.status in setOf(TriggerHistoryStatus.CLOCK_RESET, TriggerHistoryStatus.CORRUPT_RESET,
                        TriggerHistoryStatus.UNAVAILABLE)) return TriggerExpressionEvaluation(ConditionResult.Unavailable)
                history = stored.events
            }
        }
        return TriggerExpressionEvaluatorV2.evaluate(
            TriggerExpressionInput(
                definition = definition,
                triggers = automation.triggers,
                currentEventIndices = occurrence?.matchedTriggerIndices.orEmpty(),
                liveResults = live,
                elapsedRealtimeMs = elapsedRealtimeMs,
                history = history
            )
        )
    }

    private fun temporalIndices(definition: TriggerExpressionDefinitionV2): Set<Int> = buildSet {
        fun visit(node: TriggerExpressionNodeV2) {
            when (node) {
                is TriggerExpressionNodeV2.Sequence -> { add(node.firstIndex); add(node.secondIndex) }
                is TriggerExpressionNodeV2.Count -> add(node.index)
                is TriggerExpressionNodeV2.AllOf -> node.children.forEach(::visit)
                is TriggerExpressionNodeV2.AnyOf -> node.children.forEach(::visit)
                else -> Unit
            }
        }
        visit(definition.root)
    }

    private fun stateIndices(definition: TriggerExpressionDefinitionV2): Set<Int> = buildSet {
        fun visit(node: TriggerExpressionNodeV2) {
            when (node) {
                is TriggerExpressionNodeV2.State -> add(node.index)
                is TriggerExpressionNodeV2.NotState -> add(node.index)
                is TriggerExpressionNodeV2.AllOf -> node.children.forEach(::visit)
                is TriggerExpressionNodeV2.AnyOf -> node.children.forEach(::visit)
                else -> Unit
            }
        }
        visit(definition.root)
    }

    private fun stableSourceIds(type: com.nexaflow.domain.models.TriggerType): Set<String> = when (type) {
        com.nexaflow.domain.models.TriggerType.SMS -> setOf("sms")
        com.nexaflow.domain.models.TriggerType.CALENDAR -> setOf("calendar")
        com.nexaflow.domain.models.TriggerType.TIME -> setOf("time")
        com.nexaflow.domain.models.TriggerType.CHARGER -> setOf("battery")
        com.nexaflow.domain.models.TriggerType.VOLUME_CHANGED -> setOf("volume")
        com.nexaflow.domain.models.TriggerType.BOOT_COMPLETED -> setOf("device-event:boot_completed")
        else -> emptySet()
    }
}
