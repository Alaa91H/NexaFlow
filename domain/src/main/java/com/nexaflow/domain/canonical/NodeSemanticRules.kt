package com.nexaflow.domain.canonical

/**
 * T06 — Semantic Rules Engine (plan §10) over the canonical AST.
 *
 * Pure, deterministic and UI-independent: the same input always produces the
 * same violations. Design contract:
 *
 * 1. Fail closed — anything that cannot be proven consistent is a violation,
 *    never a silent pass (unknown predicate, unknown operation, expression
 *    payload).
 * 2. No false accusations — conflict rules apply only inside one atomic batch
 *    scope. Writes on opposite sides of a branch or separated by a wait are
 *    different execution paths/orders and never conflict.
 * 3. Registry-backed — predicates and operations are judged by their stable
 *    rule declarations, not by name string heuristics.
 */
class NodeSemanticRules private constructor(
    private val eventPredicateRules: Map<PredicateId, EventPredicateRule>,
    private val writeOperationRules: Map<OperationId, WriteOperationRule>,
    private val maxWritesPerTargetPerBatch: Int,
) {

    /**
     * Structural write tolerance before a violation fires even when all writes
     * are identical. Two identical writes are authoring redundancy; three or
     * more writes to one target in a single batch are an authoring error that
     * the safe-consolidation optimizer must resolve first (plan §29).
     */
    fun writeTolerancePerTarget(): Int = maxWritesPerTargetPerBatch

    fun eventPredicateRule(predicate: PredicateId): EventPredicateRule? =
        eventPredicateRules[predicate]

    fun writeOperationRule(operation: OperationId): WriteOperationRule? =
        writeOperationRules[operation]

    /**
     * Copy of the rules with overrides applied. Overrides may refine an
     * existing declaration (e.g. declare proven exclusivity between two
     * predicates once a device rule proves it) or register a new one. A
     * mismatched id fails loudly instead of silently no-op'ing.
     */
    fun withOverrides(
        eventPredicateRules: Map<PredicateId, EventPredicateRule> = emptyMap(),
        writeOperationRules: Map<OperationId, WriteOperationRule> = emptyMap(),
        maxWritesPerTargetPerBatch: Int? = null,
    ): NodeSemanticRules {
        eventPredicateRules.forEach { (id, rule) ->
            require(id == rule.predicate) {
                "Override key $id does not match rule predicate ${rule.predicate}"
            }
        }
        writeOperationRules.forEach { (id, rule) ->
            require(id == rule.operation) {
                "Override key $id does not match rule operation ${rule.operation}"
            }
        }
        maxWritesPerTargetPerBatch?.let {
            require(it >= 2) { "maxWritesPerTargetPerBatch must be >= 2" }
        }
        return NodeSemanticRules(
            eventPredicateRules = this.eventPredicateRules + eventPredicateRules,
            writeOperationRules = this.writeOperationRules + writeOperationRules,
            maxWritesPerTargetPerBatch = maxWritesPerTargetPerBatch ?: this.maxWritesPerTargetPerBatch,
        )
    }

    companion object {
        private const val DEFAULT_MAX_WRITES_PER_TARGET_PER_BATCH = 2

        /**
         * Builds a custom rule set (providers, tests, pilot families). Same
         * uniqueness guarantees as the identity registry: a duplicated stable
         * id fails immediately.
         */
        fun of(
            eventPredicateRules: List<EventPredicateRule> = emptyList(),
            writeOperationRules: List<WriteOperationRule> = emptyList(),
            maxWritesPerTargetPerBatch: Int = DEFAULT_MAX_WRITES_PER_TARGET_PER_BATCH,
        ): NodeSemanticRules {
            val byPredicate = eventPredicateRules.associateBy { it.predicate }
            require(byPredicate.size == eventPredicateRules.size) {
                "Duplicate event predicate rule declaration"
            }
            val byOperation = writeOperationRules.associateBy { it.operation }
            require(byOperation.size == writeOperationRules.size) {
                "Duplicate write operation rule declaration"
            }
            return NodeSemanticRules(
                eventPredicateRules = byPredicate,
                writeOperationRules = byOperation,
                maxWritesPerTargetPerBatch = maxWritesPerTargetPerBatch,
            )
        }

        /**
         * Default rule set. Exclusivity between predicates is intentionally
         * undeclared until proven (fail closed): every registered predicate
         * starts with an empty exclusivity set, so ALL over distinct event
         * predicates is rejected until a rule proves coincidence possible.
         */
        fun default(): NodeSemanticRules {
            val eventRules = listOf(
                "core.predicate.match_change_event",
                "core.predicate.match_event",
                "core.predicate.match_event_filter",
                "core.predicate.match_event_or_state",
                "core.predicate.match_reading",
                "core.predicate.match_schedule",
                "core.predicate.match_state",
                "core.predicate.match_state_or_event",
                "core.predicate.match_state_or_transition",
                "core.predicate.match_threshold",
                "core.predicate.match_threshold_with_filters",
                "core.predicate.match_transition",
            ).associate { id ->
                val predicate = PredicateId(id)
                predicate to EventPredicateRule(predicate, mutuallyExclusiveWith = emptySet())
            }

            val writeRules = listOf(
                OperationId("core.operation.set_state"),
                OperationId("core.operation.set_value"),
            ).associate { operation ->
                operation to WriteOperationRule(operation, writesValue = true)
            }

            return NodeSemanticRules(
                eventPredicateRules = eventRules,
                writeOperationRules = writeRules,
                maxWritesPerTargetPerBatch = DEFAULT_MAX_WRITES_PER_TARGET_PER_BATCH,
            )
        }
    }
}

/** Rule declaration for an event predicate. */
data class EventPredicateRule(
    val predicate: PredicateId,
    /**
     * Predicates proven never to coincide with this one. Empty by default:
     * coincidence is assumed possible until a rule proves otherwise.
     */
    val mutuallyExclusiveWith: Set<PredicateId> = emptySet(),
)

/** Rule declaration for a write operation. */
data class WriteOperationRule(
    val operation: OperationId,
    /** The operation requires a typed value payload on its node. */
    val writesValue: Boolean,
)

/** Deterministic violations detected by the T06 rules. */
sealed interface SemanticRuleViolation {
    val message: String
    /** Stable rule name for diagnostics UI and golden test pinning. */
    val rule: String
}

/** plan §10.1 / §46.12 — two state assertions that cannot both hold. */
data class ContradictoryStateConditions(
    val target: TargetId,
    val field: CanonicalFieldId,
    override val rule: String = "contradictory_state_conditions",
    override val message: String =
        "ALL of ${field.value}=true and ${field.value}=false on target $target can never be satisfied",
) : SemanticRuleViolation

/** plan §8.2 / §46.12 — proven mutually exclusive events combined with ALL. */
data class EventAllOnMutuallyExclusivePredicates(
    val first: PredicateId,
    val second: PredicateId,
    override val rule: String = "event_all_on_mutually_exclusive",
    override val message: String =
        "ALL over events of $first and $second can never coincide",
) : SemanticRuleViolation

/** Fail-closed sibling of [EventAllOnMutuallyExclusivePredicates]. */
data class EventAllRequiresProof(
    val first: PredicateId,
    val second: PredicateId,
    override val rule: String = "event_all_requires_proof",
    override val message: String =
        "ALL over events of $first and $second cannot be statically proven satisfiable",
) : SemanticRuleViolation

/** plan §10.9 / Gate D — conflicting writes to the same target in one batch. */
data class DuplicateConflictingWrites(
    val target: TargetId,
    val occurrences: Int,
    override val rule: String = "duplicate_conflicting_writes",
    override val message: String =
        "$occurrences conflicting writes to target $target inside one atomic batch",
) : SemanticRuleViolation

/** Fail-closed: conflict-freedom of a write cannot be proven statically. */
data class UnprovableWriteConflict(
    val target: TargetId,
    override val rule: String = "unprovable_write_conflict",
    override val message: String =
        "conflict-freedom for target $target cannot be proven inside one atomic batch",
) : SemanticRuleViolation

/** Fail-closed: an event predicate without a rule declaration. */
data class UnregisteredEventPredicate(
    val predicate: PredicateId,
    override val rule: String = "unregistered_event_predicate",
    override val message: String = "event predicate $predicate has no rule declaration",
) : SemanticRuleViolation

/** Fail-closed: a write operation without a rule declaration. */
data class UnregisteredWriteOperation(
    val operation: OperationId,
    override val rule: String = "unregistered_write_operation",
    override val message: String = "write operation $operation has no rule declaration",
) : SemanticRuleViolation

/** plan §10.4 — a write operation whose required typed payload is missing. */
data class WriteValueRequired(
    val target: TargetId,
    val operation: OperationId,
    override val rule: String = "write_value_required",
    override val message: String =
        "operation $operation on $target must carry a typed value argument",
) : SemanticRuleViolation

/** The stable write operations owned by the value-writing primitives. */
object CanonicalWriteOperations {
    val SET_STATE = OperationId("core.operation.set_state")
    val SET_VALUE = OperationId("core.operation.set_value")

    /** Returns the write operation owned by a value-writing action node. */
    fun of(node: CanonicalActionNode): OperationId? = when (node) {
        is SetStateNode -> SET_STATE
        is SetValueNode -> SET_VALUE
        else -> null
    }
}

/**
 * One boolean state assertion extracted from an observation, e.g.
 * `match_state(core.connectivity.wifi, connected=true)`.
 */
data class StateConditionAssertion(
    val target: TargetId,
    val field: CanonicalFieldId,
    val value: Boolean,
)

/**
 * Extract the state assertions carried by an observation. An observation may
 * carry zero, one or several boolean arguments; every boolean argument is an
 * assertion on the observed target.
 */
fun stateAssertionsOf(observation: ObserveNode): List<StateConditionAssertion> =
    observation.arguments.entries.mapNotNull { argument ->
        (argument.value as? BooleanValue)?.let {
            StateConditionAssertion(observation.target, argument.id, it.value)
        }
    }

/**
 * plan §10.1 — detect contradictory boolean state assertions (per target and
 * field), e.g. `Wi-Fi connected` ALL `Wi-Fi disconnected`. Works on any
 * collection of observations; the trigger compiler and schema layers (T08+)
 * feed assembled condition groups into it.
 */
fun evaluateStateConditions(
    observations: List<ObserveNode>,
): List<SemanticRuleViolation> {
    data class Key(val target: TargetId, val field: CanonicalFieldId)

    val violations = mutableListOf<SemanticRuleViolation>()
    val byKey = linkedMapOf<Key, LinkedHashSet<Boolean>>()
    for (observation in observations) {
        for (assertion in stateAssertionsOf(observation)) {
            val key = Key(assertion.target, assertion.field)
            byKey.getOrPut(key) { LinkedHashSet() }.add(assertion.value)
        }
    }
    for ((key, values) in byKey) {
        if (values.size > 1) {
            violations += ContradictoryStateConditions(key.target, key.field)
        }
    }
    return violations
}

/**
 * plan §8.2 / §46.12 / Gate D — validate a declared event group. [logic] is
 * the group's declared combination logic: ANY_OF groups are always valid
 * (events are an "or" over occurrences by design), ALL groups must prove that
 * every pair of member predicates can coincide.
 */
fun evaluateEventGroup(
    events: List<ObserveNode>,
    logic: ConditionLogic,
    rules: NodeSemanticRules = NodeSemanticRules.default(),
): List<SemanticRuleViolation> {
    if (logic != ConditionLogic.ALL || events.size < 2) return emptyList()

    val violations = mutableListOf<SemanticRuleViolation>()
    val declared = mutableListOf<Pair<PredicateId, EventPredicateRule>>()

    for (event in events) {
        val rule = rules.eventPredicateRule(event.predicate)
        if (rule == null) {
            violations += UnregisteredEventPredicate(event.predicate)
            continue
        }
        declared += event.predicate to rule
    }

    for (i in declared.indices) {
        for (j in i + 1 until declared.size) {
            val (firstId, firstRule) = declared[i]
            val (secondId, secondRule) = declared[j]
            // Coincidence with itself is trivially provable (e.g. ALL over
            // "package installed" events for several packages). Argument-level
            // contradictions are the state-conditions rule's job, not this one.
            if (firstId == secondId) continue
            val provenExclusive = secondId in firstRule.mutuallyExclusiveWith ||
                firstId in secondRule.mutuallyExclusiveWith
            if (provenExclusive) {
                violations += EventAllOnMutuallyExclusivePredicates(firstId, secondId)
            } else {
                violations += EventAllRequiresProof(firstId, secondId)
            }
        }
    }
    return violations
}

/**
 * plan §10.2/§10.9 / Gate D — evaluate write conflicts over the writes of ONE
 * atomic batch scope. Only value-writing primitives participate
 * ([SetStateNode]/[SetValueNode] via [CanonicalWriteOperations]); invoking,
 * opening or sending on the same target is not a state write. Fail-closed
 * behavior:
 *
 * - a value-writing primitive without a rule declaration is a violation
 *   (registry drift must never silently disable conflict analysis);
 * - an expression payload cannot be compared → [UnprovableWriteConflict];
 * - distinct literal payloads → [DuplicateConflictingWrites] regardless of
 *   which write primitive produced them;
 * - more writes per target than [NodeSemanticRules.writeTolerancePerTarget]
 *   even when identical → [DuplicateConflictingWrites].
 */
fun evaluateWriteConflicts(
    writes: List<CanonicalActionNode>,
    rules: NodeSemanticRules = NodeSemanticRules.default(),
): List<SemanticRuleViolation> {
    val violations = mutableListOf<SemanticRuleViolation>()

    val payloadsByTarget = linkedMapOf<TargetId, MutableList<CanonicalValue>>()
    var reportedUnknownOperation: OperationId? = null
    for (node in writes) {
        val writeOp = CanonicalWriteOperations.of(node) ?: continue
        if (rules.writeOperationRule(writeOp) == null) {
            if (reportedUnknownOperation == null) {
                reportedUnknownOperation = writeOp
                violations += UnregisteredWriteOperation(writeOp)
            }
            continue // unknown write semantics: fail closed above
        }
        val payload = when (node) {
            is SetStateNode -> node.state
            is SetValueNode -> node.value
            else -> null
        }
        if (payload != null) {
            payloadsByTarget.getOrPut(node.target) { mutableListOf() }.add(payload)
        }
    }

    for ((target, payloads) in payloadsByTarget) {
        when {
            payloads.size == 1 -> Unit // a single write never conflicts with itself

            payloads.any { it is ExpressionValue } ->
                violations += UnprovableWriteConflict(target)

            else -> {
                val distinct = payloads.distinct()
                if (distinct.size > 1 || payloads.size > rules.writeTolerancePerTarget()) {
                    violations += DuplicateConflictingWrites(target, payloads.size)
                }
            }
        }
    }
    return violations
}

/**
 * plan §10.4/§10.7 — validate that every declared value-writing operation
 * carries its typed payload. [SetStateNode]/[SetValueNode] carry their payload
 * in dedicated typed fields enforced by their constructors; other operations
 * declared with [WriteOperationRule.writesValue] must expose a `value`
 * argument.
 */
fun validateWriteValueRequirements(
    writes: List<CanonicalActionNode>,
    rules: NodeSemanticRules = NodeSemanticRules.default(),
): List<SemanticRuleViolation> {
    val violations = mutableListOf<SemanticRuleViolation>()
    for (node in writes) {
        val operation: OperationId? = when (node) {
            is SetStateNode -> CanonicalWriteOperations.SET_STATE
            is SetValueNode -> CanonicalWriteOperations.SET_VALUE
            is InvokeNode -> node.operation
            is OpenNode -> node.operation
            is SendNode -> node.operation
            is TransformNode -> node.operation
            is InputNode -> node.operation
            // Restore is snapshot-driven; its payload resolves at runtime and
            // carries no static write declaration to check.
            is RestoreNode -> null
        }
        val rule = operation?.let { rules.writeOperationRule(it) } ?: continue
        if (!rule.writesValue) continue
        val hasTypedPayload = when (node) {
            is SetStateNode, is SetValueNode -> true
            else -> node.arguments[CanonicalFieldId("value")] != null
        }
        if (!hasTypedPayload) {
            violations += WriteValueRequired(node.target, operation)
        }
    }
    return violations
}

/**
 * AST-level evaluation. Atomic batch scopes mirror the adjacency definition of
 * the safe-consolidation rules (plan §29):
 *
 * - consecutive action children inside a [SequenceNode] form one scope;
 * - a wait/branch/observation child ends the current scope;
 * - each [BranchNode] body is its own scope;
 * - a nested [SequenceNode] is its own scope.
 *
 * Every scope is checked with [evaluateWriteConflicts]; every action node is
 * checked with [validateWriteValueRequirements]. Event-group and state
 * condition rules apply to declared groups assembled by later compiler phases,
 * so they are exposed as explicit functions instead of being guessed from
 * unstructured AST shapes.
 */
fun evaluateSemanticRules(
    root: CanonicalNode,
    rules: NodeSemanticRules = NodeSemanticRules.default(),
): List<SemanticRuleViolation> {
    val violations = mutableListOf<SemanticRuleViolation>()
    val allActions = mutableListOf<CanonicalActionNode>()
    val pendingBatch = mutableListOf<CanonicalActionNode>()

    fun flushBatch() {
        if (pendingBatch.isNotEmpty()) {
            allActions += pendingBatch.toList()
            violations += evaluateWriteConflicts(pendingBatch.toList(), rules)
            pendingBatch.clear()
        }
    }

    fun visit(node: CanonicalNode) {
        when (node) {
            is SequenceNode -> {
                for (child in node.children) {
                    when (child) {
                        is CanonicalActionNode -> pendingBatch += child
                        else -> {
                            flushBatch()
                            visit(child)
                        }
                    }
                }
                flushBatch()
            }
            is BranchNode -> {
                flushBatch()
                visit(node.ifTrue)
                node.ifFalse?.let { visit(it) }
                flushBatch()
            }
            is CanonicalActionNode -> {
                pendingBatch += node
                flushBatch()
            }
            // Observations, comparisons and waits carry no writes and end any
            // open batch scope (handled by their enclosing sequence/branch).
            else -> flushBatch()
        }
    }

    visit(root)
    flushBatch()
    violations += validateWriteValueRequirements(allActions, rules)
    return violations
}
