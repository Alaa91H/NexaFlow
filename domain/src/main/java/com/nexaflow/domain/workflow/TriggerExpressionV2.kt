package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Explicit, versioned opt-in. A null envelope keeps legacy ANY/ALL behavior. */
@Serializable
data class TriggerExpressionDefinitionV2(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val root: TriggerExpressionNodeV2
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        fun invalidSentinel(): TriggerExpressionDefinitionV2 = TriggerExpressionDefinitionV2(
            schemaVersion = Int.MIN_VALUE,
            root = TriggerExpressionNodeV2.State(-1)
        )
    }
}

@Serializable
sealed interface TriggerExpressionNodeV2 {
    @Serializable @SerialName("state") data class State(val index: Int) : TriggerExpressionNodeV2
    @Serializable @SerialName("event") data class Event(val index: Int) : TriggerExpressionNodeV2
    @Serializable @SerialName("all") data class AllOf(val children: List<TriggerExpressionNodeV2>) : TriggerExpressionNodeV2
    @Serializable @SerialName("any") data class AnyOf(val children: List<TriggerExpressionNodeV2>) : TriggerExpressionNodeV2
    @Serializable @SerialName("not_state") data class NotState(val index: Int) : TriggerExpressionNodeV2
    @Serializable @SerialName("sequence") data class Sequence(val firstIndex: Int, val secondIndex: Int, val withinMs: Long) : TriggerExpressionNodeV2
    @Serializable @SerialName("count") data class Count(val index: Int, val minimumCount: Int, val withinMs: Long) : TriggerExpressionNodeV2
}

enum class TriggerExpressionIssueCode {
    UNSUPPORTED_SCHEMA, TOO_DEEP, TOO_MANY_NODES, INVALID_REFERENCE, DUPLICATE_REFERENCE,
    WRONG_TRIGGER_KIND, UNSUPPORTED_TEMPORAL_SOURCE, EMPTY_GROUP, INVALID_WINDOW, INVALID_COUNT
}

data class TriggerExpressionIssue(val code: TriggerExpressionIssueCode, val path: String)

/** A deliberately narrow source allowlist: temporal history requires an auditable stable occurrence ID. */
object TemporalEventSourcePolicy {
    // Keep this aligned with monitor adapters that provide a concrete event ID.
    private val stableSources = setOf("SMS", "CALENDAR", "TIME", "CHARGER", "VOLUME_CHANGED", "BOOT_COMPLETED")

    fun supportsStableIdentity(trigger: Trigger): Boolean = trigger.type.name in stableSources
}

object TriggerExpressionValidator {
    const val MAX_NODES = 64
    const val MAX_DEPTH = 8
    const val MAX_WINDOW_MS = 604_800_000L
    const val MAX_COUNT = 1_000

    fun isStateReadable(trigger: Trigger): Boolean = trigger.isLiveStateTrigger()

    fun validate(definition: TriggerExpressionDefinitionV2, triggers: List<Trigger>): List<TriggerExpressionIssue> {
        val issues = mutableListOf<TriggerExpressionIssue>()
        if (definition.schemaVersion != TriggerExpressionDefinitionV2.CURRENT_SCHEMA_VERSION) {
            return listOf(TriggerExpressionIssue(TriggerExpressionIssueCode.UNSUPPORTED_SCHEMA, "schemaVersion"))
        }
        var nodes = 0
        val references = mutableSetOf<Int>()
        fun ref(index: Int, stateRead: Boolean, temporal: Boolean, path: String) {
            if (index !in triggers.indices) {
                issues += TriggerExpressionIssue(TriggerExpressionIssueCode.INVALID_REFERENCE, path)
                return
            }
            if (!references.add(index)) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.DUPLICATE_REFERENCE, path)
            val trigger = triggers[index]
            if (stateRead && !trigger.isLiveStateTrigger()) {
                issues += TriggerExpressionIssue(TriggerExpressionIssueCode.WRONG_TRIGGER_KIND, path)
            }
            if (temporal && !TemporalEventSourcePolicy.supportsStableIdentity(trigger)) {
                issues += TriggerExpressionIssue(TriggerExpressionIssueCode.UNSUPPORTED_TEMPORAL_SOURCE, path)
            }
        }
        fun visit(node: TriggerExpressionNodeV2, depth: Int, path: String) {
            nodes++
            if (nodes > MAX_NODES) {
                if (nodes == MAX_NODES + 1) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.TOO_MANY_NODES, path)
                return
            }
            if (depth > MAX_DEPTH) {
                issues += TriggerExpressionIssue(TriggerExpressionIssueCode.TOO_DEEP, path)
                return
            }
            when (node) {
                is TriggerExpressionNodeV2.State -> ref(node.index, true, false, path)
                is TriggerExpressionNodeV2.Event -> ref(node.index, false, false, path)
                is TriggerExpressionNodeV2.NotState -> ref(node.index, true, false, path)
                is TriggerExpressionNodeV2.AllOf -> group(node.children, depth, path, ::visit, issues)
                is TriggerExpressionNodeV2.AnyOf -> group(node.children, depth, path, ::visit, issues)
                is TriggerExpressionNodeV2.Sequence -> {
                    window(node.withinMs, path, issues)
                    ref(node.firstIndex, false, true, "$path.firstIndex")
                    ref(node.secondIndex, false, true, "$path.secondIndex")
                    if (node.firstIndex == node.secondIndex) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.DUPLICATE_REFERENCE, path)
                }
                is TriggerExpressionNodeV2.Count -> {
                    window(node.withinMs, path, issues)
                    if (node.minimumCount !in 1..MAX_COUNT) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.INVALID_COUNT, "$path.minimumCount")
                    ref(node.index, false, true, "$path.index")
                }
            }
        }
        visit(definition.root, 1, "root")
        return issues.distinct()
    }

    private fun group(children: List<TriggerExpressionNodeV2>, depth: Int, path: String,
                      visit: (TriggerExpressionNodeV2, Int, String) -> Unit,
                      issues: MutableList<TriggerExpressionIssue>) {
        if (children.isEmpty()) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.EMPTY_GROUP, path)
        children.take(MAX_NODES + 1).forEachIndexed { index, child -> visit(child, depth + 1, "$path.children[$index]") }
    }

    private fun window(value: Long, path: String, issues: MutableList<TriggerExpressionIssue>) {
        if (value !in 1L..MAX_WINDOW_MS) issues += TriggerExpressionIssue(TriggerExpressionIssueCode.INVALID_WINDOW, "$path.withinMs")
    }
}

/** Keep explicit index references attached to the same trigger when the builder reorders the list. */
fun TriggerExpressionDefinitionV2.moveTriggerReference(from: Int, to: Int): TriggerExpressionDefinitionV2 {
    fun map(index: Int): Int = when {
        index == from -> to
        from < to && index in (from + 1)..to -> index - 1
        to < from && index in to until from -> index + 1
        else -> index
    }
    fun move(node: TriggerExpressionNodeV2): TriggerExpressionNodeV2 = when (node) {
        is TriggerExpressionNodeV2.State -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.Event -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.NotState -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.AllOf -> node.copy(children = node.children.map(::move))
        is TriggerExpressionNodeV2.AnyOf -> node.copy(children = node.children.map(::move))
        is TriggerExpressionNodeV2.Sequence -> node.copy(firstIndex = map(node.firstIndex), secondIndex = map(node.secondIndex))
        is TriggerExpressionNodeV2.Count -> node.copy(index = map(node.index))
    }
    return copy(root = move(root))
}

/** Removing a referenced trigger invalidates the draft instead of silently binding it to another row. */
fun TriggerExpressionDefinitionV2.removeTriggerReference(index: Int): TriggerExpressionDefinitionV2 {
    var removedReference = false
    fun map(value: Int): Int = when {
        value == index -> { removedReference = true; value }
        value > index -> value - 1
        else -> value
    }
    fun shift(node: TriggerExpressionNodeV2): TriggerExpressionNodeV2 = when (node) {
        is TriggerExpressionNodeV2.State -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.Event -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.NotState -> node.copy(index = map(node.index))
        is TriggerExpressionNodeV2.AllOf -> node.copy(children = node.children.map(::shift))
        is TriggerExpressionNodeV2.AnyOf -> node.copy(children = node.children.map(::shift))
        is TriggerExpressionNodeV2.Sequence -> node.copy(firstIndex = map(node.firstIndex), secondIndex = map(node.secondIndex))
        is TriggerExpressionNodeV2.Count -> node.copy(index = map(node.index))
    }
    val shifted = shift(root)
    return if (removedReference) TriggerExpressionDefinitionV2.invalidSentinel() else copy(root = shifted)
}

/** A stable-identity event already admitted to the minimal bounded history. */
@Serializable
data class TriggerExpressionHistoryEvent(val triggerIndex: Int, val elapsedRealtimeMs: Long, val ordinal: Long)

data class TriggerExpressionInput(
    val definition: TriggerExpressionDefinitionV2,
    val triggers: List<Trigger>,
    val currentEventIndices: Set<Int>,
    val liveResults: Map<Int, ConditionResult>,
    val elapsedRealtimeMs: Long,
    val history: List<TriggerExpressionHistoryEvent>
)

data class TriggerExpressionEvaluation(val result: ConditionResult, val invalid: Boolean = false)

/** Pure evaluator shared by runtime and the builder simulator. */
object TriggerExpressionEvaluatorV2 {
    fun evaluate(input: TriggerExpressionInput): TriggerExpressionEvaluation {
        if (TriggerExpressionValidator.validate(input.definition, input.triggers).isNotEmpty() ||
            input.elapsedRealtimeMs < 0L || input.history.any { it.elapsedRealtimeMs > input.elapsedRealtimeMs || it.elapsedRealtimeMs < 0L }) {
            return TriggerExpressionEvaluation(ConditionResult.Unavailable, invalid = true)
        }
        fun eval(node: TriggerExpressionNodeV2): ConditionResult = when (node) {
            is TriggerExpressionNodeV2.State -> input.liveResults[node.index] ?: ConditionResult.Unknown
            is TriggerExpressionNodeV2.Event -> if (node.index in input.currentEventIndices) ConditionResult.Satisfied else ConditionResult.Unsatisfied
            is TriggerExpressionNodeV2.NotState -> when (input.liveResults[node.index] ?: ConditionResult.Unknown) {
                ConditionResult.Satisfied -> ConditionResult.Unsatisfied
                ConditionResult.Unsatisfied -> ConditionResult.Satisfied
                else -> input.liveResults[node.index] ?: ConditionResult.Unknown
            }
            is TriggerExpressionNodeV2.AllOf -> combine(node.children.map(::eval), all = true)
            is TriggerExpressionNodeV2.AnyOf -> combine(node.children.map(::eval), all = false)
            is TriggerExpressionNodeV2.Sequence -> {
                val events = input.history.sortedBy { it.ordinal }
                val b = events.lastOrNull { it.triggerIndex == node.secondIndex }
                val a = events.lastOrNull { it.triggerIndex == node.firstIndex && (b == null || it.ordinal < b.ordinal) }
                if (b != null && a != null && b.ordinal > a.ordinal && b.elapsedRealtimeMs - a.elapsedRealtimeMs in 0..node.withinMs && b.elapsedRealtimeMs == input.elapsedRealtimeMs) ConditionResult.Satisfied
                else if (node.secondIndex in input.currentEventIndices && a != null && input.elapsedRealtimeMs - a.elapsedRealtimeMs <= node.withinMs) ConditionResult.Satisfied
                else ConditionResult.Unsatisfied
            }
            is TriggerExpressionNodeV2.Count -> {
                val floor = (input.elapsedRealtimeMs - node.withinMs).coerceAtLeast(0L)
                val historyContainsCurrent = input.history.any {
                    it.triggerIndex == node.index && it.elapsedRealtimeMs == input.elapsedRealtimeMs
                }
                val current = if (node.index in input.currentEventIndices && !historyContainsCurrent) {
                    sequenceOf(TriggerExpressionHistoryEvent(node.index, input.elapsedRealtimeMs, Long.MAX_VALUE))
                } else emptySequence()
                val count = (input.history.asSequence() + current)
                    .filter { it.triggerIndex == node.index && it.elapsedRealtimeMs in floor..input.elapsedRealtimeMs }
                    .map { it.ordinal }.distinct().take(node.minimumCount).count()
                if (count >= node.minimumCount) ConditionResult.Satisfied else ConditionResult.Unsatisfied
            }
        }
        return TriggerExpressionEvaluation(eval(input.definition.root))
    }

    private fun combine(values: List<ConditionResult>, all: Boolean): ConditionResult {
        if (all && values.any { it == ConditionResult.Unsatisfied }) return ConditionResult.Unsatisfied
        if (!all && values.any { it == ConditionResult.Satisfied }) return ConditionResult.Satisfied
        if (values.any { it is ConditionResult.Error }) return values.first { it is ConditionResult.Error }
        if (values.any { it == ConditionResult.Unavailable }) return ConditionResult.Unavailable
        if (values.any { it == ConditionResult.Unknown }) return ConditionResult.Unknown
        return if (all) ConditionResult.Satisfied else ConditionResult.Unsatisfied
    }
}

private fun Trigger.isLiveStateTrigger(): Boolean = type.name !in setOf(
    "SMS", "WEBHOOK", "CALENDAR", "SENSOR", "NOTIFICATION", "PLUGIN_EVENT",
    "CLIPBOARD_CHANGED", "NFC_TAG_SCANNED", "BOOT_COMPLETED", "APP_INSTALLED",
    "TIMEZONE_CHANGED", "ALARM_SET_CHANGED", "LOCATION", "SCREEN_TIMEOUT_CHANGED", "INCOMING_CALL"
)
