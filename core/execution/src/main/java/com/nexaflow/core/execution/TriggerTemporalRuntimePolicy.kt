package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.schedule.TriggerFilterConfigParse
import com.nexaflow.domain.schedule.TriggerFilterDecision
import com.nexaflow.domain.schedule.TriggerFilterReason
import com.nexaflow.domain.schedule.TriggerStateDecision
import com.nexaflow.domain.schedule.TriggerTemporalFilterConfigParser
import com.nexaflow.domain.schedule.TriggerTemporalFilterState

/** Execution admission adapter. State lives for this engine process and remains bounded. */
class TriggerTemporalRuntimePolicy(
    private val state: TriggerTemporalFilterState = TriggerTemporalFilterState(),
) {
    fun applyEventFilters(
        automation: Automation,
        occurrence: TriggerOccurrence?,
        elapsedRealtimeMs: Long,
    ): TriggerFilterDecision {
        val matched = occurrence?.matchedTriggerIndices ?: return TriggerFilterDecision.Allowed
        for (index in matched.sorted()) {
            val trigger = automation.triggers.getOrNull(index) ?: continue
            val parsed = TriggerTemporalFilterConfigParser.parse(trigger.config)
            when (parsed) {
                TriggerFilterConfigParse.Unconfigured -> Unit
                is TriggerFilterConfigParse.Invalid -> {
                    return TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE)
                }
                is TriggerFilterConfigParse.Valid -> {
                    val config = parsed.config
                    if (config.debounceMs != null) {
                        if (index !in occurrence.debounceAdmittedTriggerIndices ||
                            !state.consumeDebouncedAdmission(key(automation, index))
                        ) return TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING)
                        continue
                    }
                    val decision = state.evaluateEvent(
                        key = key(automation, index),
                        config = config,
                        elapsedRealtimeMs = elapsedRealtimeMs,
                    )
                    if (decision !is TriggerFilterDecision.Allowed) return decision
                }
            }
        }
        return TriggerFilterDecision.Allowed
    }

    fun observeDebouncedOccurrence(automation: Automation, triggerIndex: Int, elapsedRealtimeMs: Long): TriggerFilterDecision {
        val trigger = automation.triggers.getOrNull(triggerIndex)
            ?: return TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE)
        return when (val parsed = TriggerTemporalFilterConfigParser.parse(trigger.config)) {
            is TriggerFilterConfigParse.Invalid -> TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE)
            TriggerFilterConfigParse.Unconfigured -> TriggerFilterDecision.Allowed
            is TriggerFilterConfigParse.Valid -> if (parsed.config.debounceMs == null) {
                TriggerFilterDecision.Allowed
            } else {
                state.observeDebounced(key(automation, triggerIndex), parsed.config, elapsedRealtimeMs)
            }
        }
    }

    fun admitDebouncedOccurrence(
        automation: Automation,
        triggerIndex: Int,
        elapsedRealtimeMs: Long,
    ): TriggerFilterDecision {
        val trigger = automation.triggers.getOrNull(triggerIndex) ?: return TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE)
        return when (val parsed = TriggerTemporalFilterConfigParser.parse(trigger.config)) {
            TriggerFilterConfigParse.Unconfigured -> TriggerFilterDecision.Allowed
            is TriggerFilterConfigParse.Invalid -> TriggerFilterDecision.Unknown(TriggerFilterReason.INVALID_STATE)
            is TriggerFilterConfigParse.Valid -> state.admitDebounced(
                key(automation, triggerIndex), parsed.config, elapsedRealtimeMs,
            )
        }
    }

    fun applyThreshold(
        automation: Automation,
        index: Int,
        value: Double,
        threshold: Double,
        above: Boolean,
        elapsedRealtimeMs: Long,
    ): ConditionResult {
        val trigger = automation.triggers.getOrNull(index) ?: return ConditionResult.Unknown
        return when (val parsed = TriggerTemporalFilterConfigParser.parse(trigger.config)) {
            TriggerFilterConfigParse.Unconfigured -> if (value.isFinite() && threshold.isFinite()) {
                if (if (above) value >= threshold else value <= threshold) {
                    ConditionResult.Satisfied
                } else ConditionResult.Unsatisfied
            } else ConditionResult.Unknown
            is TriggerFilterConfigParse.Invalid -> ConditionResult.Unknown
            is TriggerFilterConfigParse.Valid -> state.evaluateThreshold(
                key = key(automation, index),
                value = value,
                threshold = threshold,
                above = above,
                config = parsed.config,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }
    }
    fun applyStableFor(
        automation: Automation,
        index: Int,
        observation: ConditionResult,
        elapsedRealtimeMs: Long,
    ): TriggerStateDecision {
        val trigger = automation.triggers.getOrNull(index) ?: return TriggerStateDecision(ConditionResult.Unknown)
        return when (val parsed = TriggerTemporalFilterConfigParser.parse(trigger.config)) {
            TriggerFilterConfigParse.Unconfigured -> TriggerStateDecision(observation)
            is TriggerFilterConfigParse.Invalid -> TriggerStateDecision(
                ConditionResult.Unknown,
                TriggerFilterReason.INVALID_STATE,
            )
            is TriggerFilterConfigParse.Valid -> state.evaluateStability(
                key = key(automation, index),
                observation = observation,
                config = parsed.config,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }
    }

    fun clear(automationId: String) {
        state.clearPrefix("$automationId:")
    }

    private fun key(automation: Automation, index: Int): String {
        val trigger = automation.triggers[index]
        val fingerprint = trigger.config.toSortedMap().hashCode().toUInt().toString(16)
        return "${automation.id}:${trigger.type.name}:$index:$fingerprint"
    }
}



