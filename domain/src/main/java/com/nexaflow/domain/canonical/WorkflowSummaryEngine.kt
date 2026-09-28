package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T13 — Central Summary Engine (plan §13).
 *
 * One deterministic text for a whole workflow draft, reused identically by
 * the builder, templates, history, import preview and execution diagnostics.
 * Per-node lines come from the T08 [NodeSummaryFormatter]; the trigger line
 * carries its explicit selection semantics; the actions line carries the
 * execution/failure policies. Locale-neutral tokens are emitted here; UI
 * strings stay in the resource layer (no hardcoded user-facing copy).
 */
@Serializable
data class WorkflowDraftNode(
    /** Stable node id; unique within the draft. */
    val nodeId: String,
    val schema: NodeSchema,
    val values: List<NodeFieldValue>,
)

/** Declared draft-level semantics rendered into the summary. */
@Serializable
data class WorkflowDraftSemantics(
    val eventLogic: EventLogic? = null,
    val conditionLogic: ConditionLogic? = null,
    val executionMode: ExecutionMode? = null,
    val failurePolicy: FailurePolicy? = null,
) {
    init {
        require(eventLogic != null || conditionLogic != null || executionMode != null || failurePolicy != null) {
            "WorkflowDraftSemantics requires at least one declared dimension"
        }
    }
}

/** Sections of the summary, in deterministic order (plan §13 examples). */
@Serializable
data class WorkflowSummary(
    val triggerLine: String? = null,
    val conditionLine: String? = null,
    val actionLines: List<String> = emptyList(),
    val semanticsLine: String? = null,
) {
    val text: String
        get() = listOfNotNull(
            triggerLine,
            conditionLine,
            *actionLines.toTypedArray(),
            semanticsLine,
        ).joinToString("\n")
}

/**
 * The summary engine. Pure and deterministic: the same draft always renders
 * the same summary, wherever it is shown.
 */
object WorkflowSummaryEngine {

    /** Renders one node line through the shared T08 formatter. */
    fun summarizeNode(node: WorkflowDraftNode): String =
        NodeSummaryFormatter.summarize(node.schema, node.values)

    /**
     * Renders the full draft summary. Node kinds split by schema kind;
     * within each kind the declaration order of [nodes] is preserved.
     */
    fun summarize(
        nodes: List<WorkflowDraftNode>,
        semantics: WorkflowDraftSemantics? = null,
    ): WorkflowSummary {
        require(nodes.map { it.nodeId }.distinct().size == nodes.size) {
            "WorkflowDraftNode ids must be unique"
        }

        val triggers = nodes.filter { it.schema.kind == NodeSchemaKind.TRIGGER }
        val actions = nodes.filter { it.schema.kind == NodeSchemaKind.ACTION }

        val triggerLine = when {
            triggers.isEmpty() -> null
            else -> triggers.joinToString(" + ") { summarizeNode(it) }
        }

        val conditionNodes = triggers.filter { it.schema.target.value in CONDITION_TARGETS }
        val conditionLine = if (conditionNodes.size >= 2) {
            val logic = semantics?.conditionLogic ?: ConditionLogic.ANY
            conditionNodes.joinToString(" + ") { summarizeNode(it) } + " • $logic"
        } else {
            null
        }

        val actionLines = actions.map(::summarizeNode)

        val semanticsLine = semantics?.let {
            listOfNotNull(
                it.eventLogic?.let { logic -> "events:$logic" },
                it.conditionLogic?.let { logic -> "conditions:$logic" },
                it.executionMode?.let { mode -> "mode:$mode" },
                it.failurePolicy?.let { policy -> "failure:$policy" },
            ).joinToString(" • ")
        }

        return WorkflowSummary(
            triggerLine = triggerLine,
            conditionLine = conditionLine,
            actionLines = actionLines,
            semanticsLine = semanticsLine,
        )
    }

    /** One-line compact preview for cards and history rows. */
    fun summarizeCompact(
        nodes: List<WorkflowDraftNode>,
        semantics: WorkflowDraftSemantics? = null,
    ): String {
        val summary = summarize(nodes, semantics)
        return buildList {
            summary.triggerLine?.let(::add)
            summary.actionLines.firstOrNull()?.let(::add)
            if (summary.actionLines.size > 1) {
                add("+${summary.actionLines.size - 1}")
            }
        }.joinToString(" → ")
    }

    private val CONDITION_TARGETS = setOf(
        "core.connectivity.vpn",
        "core.connectivity.wifi_network",
        "core.connectivity.default_network",
    )
}
