package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode

/**
 * Single source of truth for combining multiple trigger conditions under the
 * task's [TriggerMatchMode]. The engine gate (automatic runs), the manual-run
 * gate, and diagnostics all evaluate through this policy so the truth table
 * exists in exactly one place.
 *
 * Semantics:
 *  - ANY (default, historical): the firing monitor's own event is the proof;
 *    sibling conditions are not re-checked. A task with zero triggers cannot
 *    start through a monitor anyway, so ANY of an empty list stays false.
 *  - ALL: the firing monitor only *starts* the evaluation; every configured
 *    condition must be verifiably satisfied right now. [TriggerStateEvaluator]
 *    reads each condition's live state — a past event is never treated as
 *    current truth. Unverifiable conditions (event-only sources such as SMS,
 *    notification, package install, boot, webhook...) can never be confirmed,
 *    so ALL never becomes true when one of them participates; the engine
 *    surfaces this to the builder as an advisory warning instead of silently
 *    dead-locking the task.
 */
object TriggerMatchPolicy {

    /**
     * Combines per-condition [ConditionResult]s under the task's mode.
     *
     * Truth table (per spec):
     *  - ANY: at least one condition Satisfied → true.
     *  - ALL: every condition Satisfied → true; any Unsatisfied → false;
     *    otherwise (Unknown/Unavailable/Error) → false (never fabricate a
     *    success from an unverifiable condition).
     *
     * An empty condition list evaluates to false under ALL (`all(empty) ==
     * true` is never allowed to start a task); ANY of an empty list is also
     * false because there is nothing that fired.
     */
    fun combine(mode: TriggerMatchMode, results: List<ConditionResult>): Boolean = when {
        results.isEmpty() -> false
        mode == TriggerMatchMode.ANY -> results.any { it == ConditionResult.Satisfied }
        else -> results.all { it == ConditionResult.Satisfied }
    }

    /**
     * True when [trigger] can never be verified from current device state —
     * it is a pure momentary event with no readable post-state. A task that
     * mixes such a trigger into ALL mode can only ever skip; the builder shows
     * an advisory so the user can move it to a state-based condition.
     */
    fun isEventOnly(trigger: Trigger): Boolean =
        TriggerStateEvaluator.isEventOnly(trigger.type)

    /** Type-only overload for draft/UI checks that hold no full [Trigger]. */
    fun isEventOnly(type: com.nexaflow.domain.models.TriggerType): Boolean =
        TriggerStateEvaluator.isEventOnly(type)

    /**
     * Advisory for a task configured with ALL whose trigger set contains at
     * least one event-only trigger. Null when the task is fully verifiable.
     * The builder displays this next to the selector so the configuration is
     * explainable rather than mysteriously inert.
     */
    fun allModeAdvisory(triggers: List<Trigger>): String? =
        triggers.filter { isEventOnly(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { eventOnly ->
                eventOnly.joinToString(", ") { TriggerStateEvaluator.triggerLabel(it) }
            }

    /**
     * Skip message for a run the ALL gate rejected. Lists the confirmed-false
     * labels first; when none is confirmed false but the combination still
     * failed, the condition state was unverifiable — say so instead of
     * implying a check that never produced a definitive answer.
     */
    fun skipMessage(triggers: List<Trigger>, results: List<ConditionResult>): String {
        val failed = triggers.zip(results)
            .filter { (_, result) -> result == ConditionResult.Unsatisfied }
            .map { (trigger, _) -> TriggerStateEvaluator.triggerLabel(trigger) }
        return if (failed.isEmpty()) {
            "Skipped: not all trigger conditions are true (condition state unverifiable)"
        } else {
            "Skipped: not all trigger conditions are true (${failed.joinToString(", ")})"
        }
    }
}
