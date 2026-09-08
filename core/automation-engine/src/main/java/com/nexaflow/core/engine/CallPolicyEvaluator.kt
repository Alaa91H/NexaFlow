package com.nexaflow.core.engine

import com.nexaflow.domain.constraints.ConstraintEvaluator
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure, synchronous call-screening decision logic for the INCOMING_CALL
 * trigger. Adapted from BlackList's conservative precedence model to NexaFlow's
 * per-automation trigger config — there is no separate rule database: every
 * enabled task with an INCOMING_CALL trigger is one screening rule.
 *
 * Precedence (evaluated across all matching rules):
 *  1. Emergency numbers are NEVER screened (the caller decides nothing here;
 *     the service refuses to return a non-default response for them).
 *  2. BLOCK beats SILENCE when several rules match the same call.
 *  3. A rule whose task carries a SCHEDULE constraint only applies inside its
 *     window (day-of-week + time range, overnight-capable) — the same gate
 *     used by [ConstraintEvaluator].
 *
 * Matching reuses the SMS semantics so the two communication triggers stay
 * consistent: `from` + `matchMode` (CONTAINS/EXACT/ANY) against the caller
 * number, plus `category` (ANY/UNKNOWN/PRIVATE) for callers without a
 * readable number. Matching is pure and unit-testable; the Android screening
 * service only applies the returned verdict.
 */
object CallPolicyEvaluator {

    /** Screening verdicts for one incoming call. */
    enum class Verdict { /** Let the phone ring normally. */ NONE, /** Reject the call pre-ring. */ BLOCK, /** Let the call continue without ringing. */ SILENCE }

    /** Caller categories a rule can target. */
    const val CATEGORY_ANY = "ANY"
    const val CATEGORY_UNKNOWN = "UNKNOWN"
    const val CATEGORY_PRIVATE = "PRIVATE"
    const val CATEGORY_CONTACT = "CONTACT"

    /**
     * The verdict of one task's INCOMING_CALL trigger for a call, or null when
     * the task does not screen this call (no match, or outside its schedule).
     *
     * @param isEmergency true when the number is a regional emergency number
     *   (computed by the caller via PhoneNumberUtils); emergency calls always
     *   get [Verdict.NONE] regardless of rules.
     */
    fun verdictOf(
        automation: Automation,
        number: String,
        category: String,
        isEmergency: Boolean,
        nowTime: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): Verdict? {
        if (isEmergency) return null
        if (!automation.enabled) return null
        val trigger = automation.triggers.firstOrNull { it.type == TriggerType.INCOMING_CALL }
            ?: return null
        if (!matchesTrigger(trigger.config, number, category)) return null
        // A schedule gate narrows when the rule applies; a corrupt window
        // fails closed (rule inactive) the same way ConstraintEvaluator does.
        automation.constraints
            .filter { it.type == com.nexaflow.domain.models.ConstraintType.SCHEDULE }
            .forEach { constraint ->
                if (!ConstraintEvaluator.scheduleSatisfied(constraint.config, nowTime, today)) return null
            }
        return intentOf(automation) ?: return null
    }

    /**
     * The strongest verdict across every enabled INCOMING_CALL task:
     * BLOCK wins over SILENCE; no matching rule means [Verdict.NONE].
     */
    fun evaluate(
        automations: List<Automation>,
        number: String,
        category: String,
        isEmergency: Boolean,
        nowTime: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): Verdict = automations
        .mapNotNull { verdictOf(it, number, category, isEmergency, nowTime, today) }
        .fold(Verdict.NONE) { strongest, verdict ->
            when {
                strongest == Verdict.BLOCK || verdict == Verdict.BLOCK -> Verdict.BLOCK
                verdict == Verdict.SILENCE -> Verdict.SILENCE
                else -> strongest
            }
        }

    /**
     * Trigger-number matching: same `from` + `matchMode` contract as the SMS
     * trigger (CONTAINS substring, EXACT full-number equality, ANY ignores the
     * number), plus the `category` gate for UNKNOWN/PRIVATE callers.
     */
    fun matchesTrigger(config: Map<String, String>, number: String, category: String): Boolean {
        val wanted = config["category"].orEmpty().trim().uppercase().ifEmpty { CATEGORY_ANY }
        if (wanted != CATEGORY_ANY && wanted != category) return false
        val from = config["from"].orEmpty().trim()
        // The call editor's implicit (displayed) default is ANY, so an absent
        // matchMode must mean ANY here too — otherwise a task whose mode was
        // never touched would screen by substring at runtime while the UI
        // promised "any number".
        val mode = config["matchMode"].orEmpty().trim().uppercase().ifEmpty { SmsTriggerMatcher.MATCH_ANY }
        return when (mode) {
            SmsTriggerMatcher.MATCH_EXACT -> from.isNotEmpty() && number.trim() == from
            SmsTriggerMatcher.MATCH_ANY -> true
            // CONTAINS mirrors the SMS body filter: an empty filter matches.
            else -> from.isEmpty() || number.contains(from, ignoreCase = true)
        }
    }

    /**
     * What the task wants to do with a screened call: a CALL_BLOCK action
     * means reject, a CALL_SILENCE action means silence. Tasks without either
     * control action are pure observers (e.g. log-only) and return null.
     */
    fun intentOf(automation: Automation): Verdict? = when {
        automation.actions.any { it.isCallBlock() } -> Verdict.BLOCK
        automation.actions.any { it.isCallSilence() } -> Verdict.SILENCE
        else -> null
    }

    private fun Action.isCallBlock(): Boolean = type == ActionType.CALL_BLOCK
    private fun Action.isCallSilence(): Boolean = type == ActionType.CALL_SILENCE
}
