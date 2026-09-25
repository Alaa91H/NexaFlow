package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.isOneShotEvent

/**
 * Single source of truth for combining multiple trigger conditions under the
 * task's [TriggerMatchMode]. Runtime routers, the automatic engine gate, the
 * manual gate and readiness surfaces must delegate their tri-state truth table
 * here instead of maintaining local ANY/ALL implementations.
 *
 * Semantics:
 *  - ANY (historical): one satisfied condition is enough. If every known
 *    condition is unsatisfied the result is Unsatisfied; otherwise unresolved
 *    evidence remains Unknown.
 *  - ALL: one confirmed Unsatisfied condition blocks the expression. Every
 *    condition must be Satisfied to pass; otherwise unresolved evidence stays
 *    Unknown.
 *
 * Automatic ALL evaluation supplies CURRENT_EVENT evidence for momentary
 * triggers and LIVE_STATE evidence for readable triggers. Historical events
 * are never cached as truth.
 */
object TriggerMatchPolicy {

    /**
     * Typed aggregation for a non-empty trigger expression.
     *
     * Empty is intentionally [ConditionResult.Unsatisfied] here because an
     * automatic trigger expression with no conditions must never self-start.
     * Manual "no triggers configured" handling remains an explicit caller rule
     * in [TriggerStateEvaluator.evaluateAsync].
     */
    fun aggregate(
        mode: TriggerMatchMode,
        results: List<ConditionResult>,
    ): ConditionResult {
        if (results.isEmpty()) return ConditionResult.Unsatisfied

        return when (mode) {
            TriggerMatchMode.ANY -> when {
                results.any { it == ConditionResult.Satisfied } ->
                    ConditionResult.Satisfied
                results.all { it == ConditionResult.Unsatisfied } ->
                    ConditionResult.Unsatisfied
                else ->
                    ConditionResult.Unknown
            }

            TriggerMatchMode.ALL -> when {
                results.any { it == ConditionResult.Unsatisfied } ->
                    ConditionResult.Unsatisfied
                results.all { it == ConditionResult.Satisfied } ->
                    ConditionResult.Satisfied
                else ->
                    ConditionResult.Unknown
            }
        }
    }

    /** Boolean admission view used by the automatic engine gate. */
    fun combine(mode: TriggerMatchMode, results: List<ConditionResult>): Boolean =
        aggregate(mode, results) == ConditionResult.Satisfied

    /**
     * True when [trigger] has no readable post-state and therefore needs
     * current-event evidence during automatic evaluation. Manual evaluation has
     * no occurrence, so these triggers remain unverifiable there.
     */
    fun isEventOnly(trigger: Trigger): Boolean =
        trigger.isOneShotEvent() || TriggerStateEvaluator.isEventOnly(trigger.type)

    /**
     * Type-only compatibility overload. It cannot classify configuration-
     * sensitive triggers such as TIME (AT vs RANGE); new runtime/UI code should
     * always call [isEventOnly] with the full [Trigger].
     */
    fun isEventOnly(type: com.nexaflow.domain.models.TriggerType): Boolean =
        TriggerStateEvaluator.isEventOnly(type)

    /**
     * Event semantics to explain for ALL mode.
     *
     * With one momentary condition, that current event may be combined with
     * any number of live-state siblings. With two or more momentary conditions,
     * NexaFlow does not remember earlier events: one physical/logical occurrence
     * must match every momentary condition participating in that ALL decision.
     */
    enum class AllModeEventSemantics {
        NONE,
        CURRENT_EVENT_WITH_LIVE_STATE,
        SAME_OCCURRENCE_REQUIRED,
    }

    fun allModeEventSemantics(triggers: List<Trigger>): AllModeEventSemantics {
        val eventOnlyCount = triggers.count(::isEventOnly)
        return when {
            eventOnlyCount == 0 -> AllModeEventSemantics.NONE
            eventOnlyCount == 1 -> AllModeEventSemantics.CURRENT_EVENT_WITH_LIVE_STATE
            else -> AllModeEventSemantics.SAME_OCCURRENCE_REQUIRED
        }
    }

    /**
     * Diagnostic labels for event-only conditions. Kept for existing call sites
     * while UI wording is selected via [allModeEventSemantics].
     */
    fun allModeAdvisory(triggers: List<Trigger>): String? =
        triggers.filter(::isEventOnly)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { TriggerStateEvaluator.triggerLabel(it) }

    /**
     * Skip message for a run the ALL gate rejected. Lists confirmed-false
     * labels first; when none is confirmed false but the combination still
     * failed, the condition state was unresolved — say so rather than implying
     * a definitive false read.
     */
    fun skipMessage(triggers: List<Trigger>, results: List<ConditionResult>): String {
        val paired = triggers.zip(results)
        val failed = paired
            .filter { (_, result) -> result == ConditionResult.Unsatisfied }
            .map { (trigger, _) -> TriggerStateEvaluator.triggerLabel(trigger) }

        if (failed.isNotEmpty()) {
            return "Skipped: not all trigger conditions are true (${failed.joinToString(", ")})"
        }

        val unresolvedMomentary = paired.count { (trigger, result) ->
            isEventOnly(trigger) && result != ConditionResult.Satisfied
        }
        if (
            allModeEventSemantics(triggers) == AllModeEventSemantics.SAME_OCCURRENCE_REQUIRED &&
            unresolvedMomentary > 0
        ) {
            return "Skipped: ALL momentary conditions must match the same current occurrence"
        }

        return "Skipped: not all trigger conditions are true (condition state unverifiable)"
    }
}
