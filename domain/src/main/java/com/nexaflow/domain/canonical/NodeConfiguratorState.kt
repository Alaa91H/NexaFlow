package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T12 — Unified Configurator Infrastructure (plan §12, ADR-005 implementation
 * core).
 *
 * A pure, deterministic state machine one UI shell ([render] via any Compose/
 * platform host) drives: every question "which tabs exist, which fields are
 * visible, what is required right now, is the current draft valid, what does
 * the row read like?" is answered by the schema — never by a per-family
 * screen. The feature layer (T12 UI wiring) renders this state; it must not
 * re-implement its logic.
 */
@Serializable
data class NodeConfiguratorTab(
    val id: String,
    val title: String,
)

/** Selection view-model for large lists (apps, devices, networks…). */
@Serializable
data class SelectionState(
    val options: List<SelectionOption>,
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
) {
    init {
        require(options.map { it.id }.distinct().size == options.size) {
            "SelectionState options must have unique ids"
        }
    }

    /** Deterministic filtered+ordered view for lazy rendering. */
    val visibleOptions: List<SelectionOption>
        get() = options
            .filter { it.label.contains(searchQuery, ignoreCase = true) }
            .sortedWith(compareBy({ it.label }, { it.id }))

    val selectedCount: Int get() = selectedIds.size

    fun toggle(id: String): SelectionState = copy(
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id,
    )

    fun selectAll(): SelectionState = copy(selectedIds = options.mapTo(mutableSetOf()) { it.id })

    fun clearAll(): SelectionState = copy(selectedIds = emptySet())
}

@Serializable
data class SelectionOption(
    val id: String,
    val label: String,
    val subtitle: String? = null,
)

/** Which progressive-disclosure levels the sheet currently shows (§12.2). */
@Serializable
data class DisclosureState(
    val maximumLevel: NodeSchemaLevel = NodeSchemaLevel.BASIC,
) {
    val showsAdvanced: Boolean get() = maximumLevel != NodeSchemaLevel.BASIC
    val showsExpert: Boolean get() = maximumLevel == NodeSchemaLevel.EXPERT

    fun expand(): DisclosureState = copy(
        maximumLevel = when (maximumLevel) {
            NodeSchemaLevel.BASIC -> NodeSchemaLevel.ADVANCED
            NodeSchemaLevel.ADVANCED -> NodeSchemaLevel.EXPERT
            NodeSchemaLevel.EXPERT -> NodeSchemaLevel.EXPERT
        },
    )
}

/**
 * Immutable state of one configurator session. All mutations return new
 * state; UI hosts stay dumb renderers.
 */
data class NodeConfiguratorState(
    val schema: NodeSchema,
    val values: List<NodeFieldValue> = emptyList(),
    val disclosure: DisclosureState = DisclosureState(),
    /** Field id -> selection state, for MULTI-typed option fields. */
    val selections: Map<String, SelectionState> = emptyMap(),
    /** Cardinality configured per operation (T05 linkage), for MULTI fields. */
    val cardinality: OperationCardinality? = null,
) {

    /** §12.1 — dynamic tabs; an unused dimension produces no tab. */
    fun tabs(): List<NodeConfiguratorTab> = buildList {
        add(NodeConfiguratorTab("type", "Type"))
        if (schema.operation != null || schema.predicate != null) {
            add(
                NodeConfiguratorTab(
                    id = "target",
                    title = if (schema.kind == NodeSchemaKind.TRIGGER) "Event" else "Operation",
                ),
            )
        }
        add(NodeConfiguratorTab("options", "Options"))
        if (schema.capabilities.isNotEmpty()) {
            add(NodeConfiguratorTab("conditions", "Conditions"))
        }
        if (schema.conflicts.isNotEmpty() || schema.securityClass != NodeSecurityClass.STANDARD) {
            add(NodeConfiguratorTab("advanced", "Advanced"))
        }
    }

    /** Fields visible at the current disclosure level, in declaration order. */
    fun visibleFields(): List<NodeSchemaField> {
        val byId = values.associateBy { it.field }
        return schema.fields.filter { field ->
            val levelShown = when (field.level) {
                NodeSchemaLevel.BASIC -> true
                NodeSchemaLevel.ADVANCED -> disclosure.showsAdvanced
                NodeSchemaLevel.EXPERT -> disclosure.showsExpert
            }
            levelShown && (field.visibleWhen.isEmpty() || conditionsHold(field.visibleWhen, byId))
        }
    }

    /** Fields the draft must still supply under the current values. */
    fun missingRequired(): List<CanonicalFieldId> {
        val byId = values.associateBy { it.field }
        return schema.fields.filter { field ->
            val required = field.alwaysRequired ||
                (field.requiredWhen.isNotEmpty() && conditionsHold(field.requiredWhen, byId))
            required && field.id !in byId
        }.map { it.id }
    }

    /** Live violations from the T08 schema validator over the current draft. */
    fun validationIssues(): List<NodeSchemaViolation> = validateNodeValues(schema, values)

    fun expand(): NodeConfiguratorState = copy(disclosure = disclosure.expand())

    /** Sets one typed value (schema-typed; untyped strings never enter). */
    fun setValue(field: CanonicalFieldId, value: CanonicalValue): NodeConfiguratorState {
        val declared = schema.field(field)
        require(declared != null) { "field ${field.value} is not declared by this schema" }
        val others = values.filterNot { it.field == field }
        return copy(values = others + NodeFieldValue(field, value))
    }

    /** Selection helpers for bounded large lists (§12.3). */
    fun selectionFor(field: CanonicalFieldId): SelectionState =
        selections[field.value] ?: SelectionState(options = emptyList())

    fun withSelection(field: CanonicalFieldId, state: SelectionState): NodeConfiguratorState =
        copy(selections = selections + (field.value to state))

    /** Deterministic row summary via the T08 formatter (§13 groundwork). */
    fun summary(): String = NodeSummaryFormatter.summarize(schema, values)

    /** A draft is submittable only when schema-valid with no missing fields. */
    val submittable: Boolean
        get() = validationIssues().isEmpty() && missingRequired().isEmpty()
}

/** Builds the initial state for configuring [schema] with declared defaults. */
fun configuratorStateFor(
    schema: NodeSchema,
    cardinality: OperationCardinality? = null,
): NodeConfiguratorState {
    val defaults = defaultsOf(schema)
    return NodeConfiguratorState(
        schema = schema,
        values = defaults,
        cardinality = cardinality,
    )
}
