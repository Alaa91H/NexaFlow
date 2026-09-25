package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.isOneShotEvent

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
 *  - ALL: every configured condition needs current evidence. State-readable
 *    triggers are checked from live state; event-only triggers may be satisfied
 *    only by the [TriggerOccurrence] that started the current evaluation.
 *    Historical events are never cached as truth. This makes combinations such
 *    as SMS + Wi-Fi deterministic without pretending an old SMS is still true.
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
     * True when [trigger] has no readable post-state and therefore needs
     * current-event evidence during automatic evaluation. Manual evaluation has
     * no occurrence, so these triggers remain unverifiable there.
     */
    fun isEventOnly(trigger: Trigger): Boolean =
        trigger.isOneShotEvent() || TriggerStateEvaluator.isEventOnly(trigger.type)

    /** Type-only overload for draft/UI checks that hold no full [Trigger]. */
    fun isEventOnly(type: com.nexaflow.domain.models.TriggerType): Boolean =
        TriggerStateEvaluator.isEventOnly(type)

    /**
     * Advisory for a task configured with ALL whose trigger set contains at
     * least one event-only trigger. Such a task can start only from a matching
     * current event while every state-readable sibling is true. The builder
     * surfaces that event-driven behavior instead of implying persistent truth.
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
