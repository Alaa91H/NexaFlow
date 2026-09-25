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

    fun isSatisfied(mode: TriggerMatchMode): Boolean =
        TriggerMatchPolicy.combine(mode, results)
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
