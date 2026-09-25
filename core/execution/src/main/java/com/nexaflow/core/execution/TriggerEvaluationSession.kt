package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import kotlinx.coroutines.CancellationException

/**
 * One concrete trigger event that caused an automation evaluation.
 *
 * Trigger indices are tied to the immutable [Automation] snapshot supplied to
 * [ExecutionEngine.runAutomation]. More than one index may be matched by the
 * same physical event (for example one SMS may satisfy two SMS filters).
 *
 * [eventId], when present, must identify the concrete physical/logical
 * occurrence rather than only the event type. The engine uses it for a short
 * same-process replay guard; sources without a trustworthy occurrence identity
 * should leave it null.
 *
 * This object is intentionally ephemeral: it is evaluation evidence, not
 * persisted workflow state.
 */
data class TriggerOccurrence(
    val matchedTriggerIndices: Set<Int>,
    val occurredAtEpochMs: Long,
    val sourceId: String? = null,
    val eventId: String? = null,
) {
    init {
        require(matchedTriggerIndices.isNotEmpty()) {
            "TriggerOccurrence must reference at least one trigger"
        }
        require(matchedTriggerIndices.all { it >= 0 }) {
            "TriggerOccurrence indices must be non-negative"
        }
        require(occurredAtEpochMs >= 0L) {
            "TriggerOccurrence timestamp must be non-negative"
        }
        require(sourceId == null || sourceId.isNotBlank()) {
            "TriggerOccurrence sourceId must be null or non-blank"
        }
        require(eventId == null || eventId.isNotBlank()) {
            "TriggerOccurrence eventId must be null or non-blank"
        }
        require(sourceId == null || sourceId.length <= 128) {
            "TriggerOccurrence sourceId is too long"
        }
        require(eventId == null || eventId.length <= 512) {
            "TriggerOccurrence eventId is too long"
        }
    }

    companion object {
        fun single(
            triggerIndex: Int,
            occurredAtEpochMs: Long,
            sourceId: String? = null,
            eventId: String? = null,
        ): TriggerOccurrence = TriggerOccurrence(
            matchedTriggerIndices = setOf(triggerIndex),
            occurredAtEpochMs = occurredAtEpochMs,
            sourceId = sourceId,
            eventId = eventId,
        )
    }
}

/** Where the proof for one trigger result came from. */
enum class TriggerEvidenceSource {
    /** A momentary event matched this trigger in the current evaluation. */
    CURRENT_EVENT,

    /** The trigger was re-read from current device/application state. */
    LIVE_STATE,
}

/** Dominant fail-closed reason for an ALL expression that did not pass. */
enum class TriggerBlockKind {
    UNSATISFIED,
    UNKNOWN,
    UNAVAILABLE,
    ERROR,
}

/** Typed evidence for one trigger in saved-list order. */
data class TriggerEvidence(
    val triggerIndex: Int,
    val trigger: Trigger,
    val result: ConditionResult,
    val source: TriggerEvidenceSource,
)

/**
 * Logical snapshot used for one ANY/ALL decision.
 *
 * All evidence belongs to the same evaluation session and is kept in the
 * automation's saved trigger order so diagnostics remain deterministic.
 */
data class TriggerEvaluationSnapshot(
    val evaluatedAtEpochMs: Long,
    val occurrence: TriggerOccurrence?,
    val evidence: List<TriggerEvidence>,
) {
    val results: List<ConditionResult>
        get() = evidence.map { it.result }

    fun decision(mode: TriggerMatchMode): ConditionResult =
        TriggerMatchPolicy.aggregate(mode, results)

    fun isSatisfied(mode: TriggerMatchMode): Boolean =
        decision(mode) == ConditionResult.Satisfied

    /**
     * Stable dominant block kind. Confirmed false wins over unresolved states,
     * matching ALL aggregation semantics; then errors/unavailability/unknown
     * describe why no definitive truth was available.
     */
    fun blockKind(): TriggerBlockKind? {
        if (results.all { it == ConditionResult.Satisfied }) return null
        return when {
            results.any { it == ConditionResult.Unsatisfied } -> TriggerBlockKind.UNSATISFIED
            results.any { it is ConditionResult.Error } -> TriggerBlockKind.ERROR
            results.any { it == ConditionResult.Unavailable } -> TriggerBlockKind.UNAVAILABLE
            else -> TriggerBlockKind.UNKNOWN
        }
    }

    /**
     * Bounded, config-free evidence summary for diagnostics. It intentionally
     * excludes trigger values, payloads, phone numbers, tokens and error text.
     */
    fun diagnosticDetail(maxLength: Int = 1_024): String {
        require(maxLength > 0) { "maxLength must be positive" }
        return evidence.joinToString(";") { item ->
            "#${item.triggerIndex}:${item.trigger.type.name}=${item.result.diagnosticCode()}@${item.source.name}"
        }.take(maxLength)
    }
}

private fun ConditionResult.diagnosticCode(): String = when (this) {
    ConditionResult.Satisfied -> "SATISFIED"
    ConditionResult.Unsatisfied -> "UNSATISFIED"
    ConditionResult.Unknown -> "UNKNOWN"
    ConditionResult.Unavailable -> "UNAVAILABLE"
    is ConditionResult.Error -> "ERROR"
}

/**
 * Central evaluator for trigger expressions.
 *
 * Event-only triggers can only become true from the *current* occurrence.
 * State-readable triggers are always re-read live, even when their monitor
 * fired the evaluation, so a stale callback cannot manufacture a true state.
 *
 * This distinction is the key to stable ALL semantics:
 *   SMS event + Wi-Fi live state -> can evaluate true
 *   old SMS + Wi-Fi live state   -> cannot evaluate true
 *   charger callback + charger already disconnected -> live false/unknown
 */
object TriggerExpressionEvaluator {

    suspend fun evaluate(
        context: Context,
        automation: Automation,
        occurrence: TriggerOccurrence?,
        evaluatedAtEpochMs: Long = System.currentTimeMillis(),
    ): TriggerEvaluationSnapshot = evaluate(
        automation = automation,
        occurrence = occurrence,
        evaluatedAtEpochMs = evaluatedAtEpochMs,
        stateReader = { trigger ->
            TriggerStateEvaluator.evaluateTriggerState(context, trigger)
        },
    )

    /**
     * Pure test seam: callers provide the current-state reader while event
     * evidence still follows the exact production policy.
     */
    internal suspend fun evaluate(
        automation: Automation,
        occurrence: TriggerOccurrence?,
        evaluatedAtEpochMs: Long,
        stateReader: suspend (Trigger) -> ConditionResult,
    ): TriggerEvaluationSnapshot {
        val evidence = automation.triggers.mapIndexed { index, trigger ->
            val currentEventProvesTrigger =
                automation.workflowVersion >=
                    Automation.OCCURRENCE_AWARE_TRIGGER_SEMANTICS_VERSION &&
                    occurrence?.matchedTriggerIndices?.contains(index) == true &&
                    TriggerMatchPolicy.isEventOnly(trigger)

            if (currentEventProvesTrigger) {
                TriggerEvidence(
                    triggerIndex = index,
                    trigger = trigger,
                    result = ConditionResult.Satisfied,
                    source = TriggerEvidenceSource.CURRENT_EVENT,
                )
            } else {
                val result = try {
                    stateReader(trigger)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    val reason = failure.message
                        ?.takeIf { it.isNotBlank() }
                        ?.take(1_024)
                        ?: "unreadable trigger state"
                    ConditionResult.Error(reason)
                }
                TriggerEvidence(
                    triggerIndex = index,
                    trigger = trigger,
                    result = result,
                    source = TriggerEvidenceSource.LIVE_STATE,
                )
            }
        }

        return TriggerEvaluationSnapshot(
            evaluatedAtEpochMs = evaluatedAtEpochMs,
            occurrence = occurrence,
            evidence = evidence,
        )
    }
}
