package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T10 — Execution Planner (plan §17, ADR-008).
 *
 * Compiles a validated canonical AST into atomic commands and a deterministic
 * execution plan. The runtime never executes a family or a node directly; it
 * executes this plan. Planning invariants:
 *
 * 1. Deterministic — the same (AST, policies, semantics table) always yields
 *    the same plan; ordering ties break on stable ids.
 * 2. Fail closed — every planned operation must have declared command
 *    semantics (idempotency/reversibility). Undeclared operations abort
 *    planning instead of inheriting silent defaults (plan rule 46.14: no
 *    unsafe retry of a NON_IDEMPOTENT operation).
 * 3. Parallel safety is proven, not assumed — a parallel group is emitted
 *    only when the T06 conflict rules prove the group conflict-free.
 * 4. Validation-first — [planValidated] refuses ASTs whose T09 verdict is
 *    invalid, materializing the closure rule "invalid config cannot reach
 *    the planner".
 */
@Serializable
data class CommandSemantics(
    val operation: OperationId,
    val idempotency: CommandIdempotency,
    val reversible: Boolean,
)

@Serializable
enum class CommandIdempotency {
    IDEMPOTENT,
    NON_IDEMPOTENT,
    CONDITIONALLY_IDEMPOTENT,
}

@Serializable
enum class PlanExecutionPolicy {
    /** Commands run in authored order, one at a time. */
    SEQUENTIAL,

    /**
     * Adjacent conflict-free commands may run in parallel; ordering with
     * respect to waits and branches is still preserved.
     */
    PARALLEL_SAFE,

    /**
     * Attempt every command, tolerate failures. Requires the failure policy
     * to be CONTINUE_ON_ERROR; a best-effort plan with FAIL_FAST is a
     * contradiction and fails planning.
     */
    BEST_EFFORT,

    /**
     * Snapshot before execution, compensate on failure. Requires
     * ROLLBACK_WHEN_SUPPORTED and only reversible commands; any
     * non-reversible command fails planning (fail closed).
     */
    TRANSACTIONAL_WHEN_POSSIBLE,
}

/** One atomic, fully-typed executable unit. */
@Serializable
data class AtomicCommand(
    /** Stable local id inherited from the source node. */
    val commandId: String,
    val target: TargetId,
    val operation: OperationId,
    val payload: CanonicalValue? = null,
    val arguments: CanonicalArguments = CanonicalArguments.EMPTY,
    val idempotency: CommandIdempotency,
    val reversible: Boolean,
    /** True for wait/barrier commands that carry no side effect. */
    val sideEffectFree: Boolean = false,
)

/** A planned group: ordered inside itself; groups run in plan order. */
@Serializable
data class CommandGroup(
    val groupId: String,
    val commands: List<AtomicCommand>,
    /** True when the group is proven safe for parallel execution. */
    val parallel: Boolean = false,
    /** Set when the group belongs to one side of a branch. */
    val branchId: String? = null,
)

/** Planned compensation for a reversible command (plan §20). */
@Serializable
data class CompensationCommand(
    /** The command this compensation restores. */
    val commandId: String,
    val target: TargetId,
    /** Compensation runs in reverse plan order; this is its 0-based rank. */
    val reverseRank: Int,
)

/** The complete, deterministic execution plan a runtime executes. */
@Serializable
data class ExecutionPlan(
    val groups: List<CommandGroup>,
    val failurePolicy: FailurePolicy,
    val executionPolicy: PlanExecutionPolicy,
    val compensations: List<CompensationCommand> = emptyList(),
) {
    val allCommands: List<AtomicCommand>
        get() = groups.flatMap { it.commands }
}

/**
 * The pure planner. [commandSemantics] is the declared semantics table;
 * construct through [Companion.of] or [Companion.default].
 */
class CanonicalExecutionPlanner private constructor(
    private val commandSemantics: Map<OperationId, CommandSemantics>,
) {

    companion object {
        private val SET_STATE = OperationId("core.operation.set_state")
        private val SET_VALUE = OperationId("core.operation.set_value")
        private val WAIT = OperationId("core.operation.wait")

        /**
         * Default semantics: desired-state writes are idempotent and
         * reversible (re-applying the same desired state is safe, plan §18),
         * waits are side-effect free. Everything else must be declared
         * explicitly by a provider — the default planner refuses undeclared
         * operations (e.g. send/invoke stay unplannable until a provider
         * declares their idempotency policy).
         */
        fun default(): CanonicalExecutionPlanner = of(defaultSemantics())

        /** The default declarations, exposed for composition (T26 cutover). */
        fun defaultSemantics(): List<CommandSemantics> = listOf(
            CommandSemantics(SET_STATE, CommandIdempotency.IDEMPOTENT, reversible = true),
            CommandSemantics(SET_VALUE, CommandIdempotency.IDEMPOTENT, reversible = true),
            CommandSemantics(WAIT, CommandIdempotency.IDEMPOTENT, reversible = false),
        )

        /** Builds a planner with an explicit semantics table. */
        fun of(semantics: List<CommandSemantics>): CanonicalExecutionPlanner {
            val byOperation = semantics.associateBy { it.operation }
            require(byOperation.size == semantics.size) {
                "Duplicate command semantics declaration"
            }
            return CanonicalExecutionPlanner(byOperation)
        }
    }

    /**
     * Plans a T09-validated workflow. This is the only entry point runners
     * should use: it materializes "invalid configuration never reaches the
     * planner" by failing closed on an invalid verdict.
     */
    fun planValidated(
        ast: CanonicalWorkflowAst,
        contract: CanonicalWorkflowContract,
        values: List<NodeFieldValue>,
        executionPolicy: PlanExecutionPolicy,
        failurePolicy: FailurePolicy,
    ): ExecutionPlan {
        val verdict = validate(ast, contract, values)
        require(verdict.isValid) {
            "Refusing to plan an invalid workflow: " + verdict.findings.first().message
        }
        return plan(ast.root, executionPolicy, failurePolicy)
    }

    /** Operations with declared command semantics (T26 cutover inspection). */
    fun declaredOperations(): Set<OperationId> = commandSemantics.keys

    /**
     * Plans a raw AST root. Deterministic; fails closed on undeclared
     * operations and contradictory policy combinations.
     */
    fun plan(
        root: CanonicalNode,
        executionPolicy: PlanExecutionPolicy,
        failurePolicy: FailurePolicy,
    ): ExecutionPlan {
        // Policy-contradiction gates (fail closed, plan §17).
        if (executionPolicy == PlanExecutionPolicy.BEST_EFFORT) {
            require(failurePolicy == FailurePolicy.CONTINUE_ON_ERROR) {
                "BEST_EFFORT requires CONTINUE_ON_ERROR; refusing a contradictory plan"
            }
        }
        if (executionPolicy == PlanExecutionPolicy.TRANSACTIONAL_WHEN_POSSIBLE) {
            require(failurePolicy == FailurePolicy.ROLLBACK_WHEN_SUPPORTED) {
                "TRANSACTIONAL_WHEN_POSSIBLE requires ROLLBACK_WHEN_SUPPORTED"
            }
        }

        val groups = mutableListOf<CommandGroup>()
        val compensations = mutableListOf<CompensationCommand>()
        var groupCounter = 0

        fun newGroupId(prefix: String): String = "$prefix-${groupCounter++}"

        fun commandOf(node: CanonicalActionNode): AtomicCommand {
            val operation = when (node) {
                is SetStateNode -> OperationId("core.operation.set_state")
                is SetValueNode -> OperationId("core.operation.set_value")
                is InvokeNode -> node.operation
                is OpenNode -> node.operation
                is SendNode -> node.operation
                is TransformNode -> node.operation
                is InputNode -> node.operation
                is RestoreNode -> OperationId("core.operation.set_state")
            }
            val semantics = commandSemantics[operation]
                ?: throw IllegalArgumentException(
                    "Operation $operation has no declared command semantics; " +
                        "planning fails closed instead of guessing idempotency",
                )
            val payload = when (node) {
                is SetStateNode -> node.state
                is SetValueNode -> node.value
                else -> null
            }
            return AtomicCommand(
                commandId = node.id.value,
                target = node.target,
                operation = operation,
                payload = payload,
                arguments = node.arguments,
                idempotency = semantics.idempotency,
                reversible = semantics.reversible,
            )
        }

        fun waitCommand(node: WaitNode): AtomicCommand {
            val semantics = commandSemantics[WAIT]
                ?: throw IllegalArgumentException("Wait semantics must be declared")
            return AtomicCommand(
                commandId = node.id.value,
                target = TargetId("core.flow.delay"),
                operation = WAIT,
                payload = DurationValue(node.duration.milliseconds),
                idempotency = semantics.idempotency,
                reversible = false,
                sideEffectFree = true,
            )
        }

        // Plans one batch scope of adjacent actions: parallel only when the
        // policy allows AND the T06 rules prove the scope conflict-free.
        fun planScope(actions: List<CanonicalActionNode>, branchId: String?) {
            if (actions.isEmpty()) return
            val commands = actions.map(::commandOf)
            val parallel = executionPolicy == PlanExecutionPolicy.PARALLEL_SAFE &&
                actions.size >= 2 &&
                evaluateWriteConflicts(actions).isEmpty()
            groups += CommandGroup(
                groupId = newGroupId(if (branchId != null) "branch-$branchId" else "seq"),
                commands = commands,
                parallel = parallel,
                branchId = branchId,
            )
        }

        fun visit(node: CanonicalNode, branchId: String?) {
            when (node) {
                is SequenceNode -> {
                    val batch = mutableListOf<CanonicalActionNode>()
                    for (child in node.children) {
                        when (child) {
                            is CanonicalActionNode -> batch += child
                            is WaitNode -> {
                                planScope(batch, branchId)
                                batch.clear()
                                groups += CommandGroup(
                                    groupId = newGroupId("wait"),
                                    commands = listOf(waitCommand(child)),
                                    branchId = branchId,
                                )
                            }
                            else -> {
                                planScope(batch, branchId)
                                batch.clear()
                                visit(child, branchId)
                            }
                        }
                    }
                    planScope(batch, branchId)
                }
                is BranchNode -> {
                    val branch = node.id.value
                    visit(node.ifTrue, branch)
                    node.ifFalse?.let { visit(it, "$branch-else") }
                }
                is WaitNode -> groups += CommandGroup(
                    groupId = newGroupId("wait"),
                    commands = listOf(waitCommand(node)),
                    branchId = branchId,
                )
                is CanonicalActionNode -> planScope(listOf(node), branchId)
                // Conditions and comparisons produce no commands; the runtime
                // evaluates them before running the planned groups.
                else -> Unit
            }
        }

        visit(root, branchId = null)

        // Compensations: only for rollback-capable plans, only reversible
        // commands, in reverse order (plan §20).
        if (executionPolicy == PlanExecutionPolicy.TRANSACTIONAL_WHEN_POSSIBLE) {
            val reversible = groups.flatMap { it.commands }.withIndex()
                .filter { it.value.reversible && !it.value.sideEffectFree }
            val total = reversible.size
            require(total == groups.flatMap { it.commands }.count { !it.sideEffectFree }) {
                "TRANSACTIONAL_WHEN_POSSIBLE requires every side-effecting command to be reversible; " +
                    "refusing a plan that cannot honor rollback"
            }
            compensations += reversible.map { (index, command) ->
                CompensationCommand(
                    commandId = command.commandId,
                    target = command.target,
                    reverseRank = total - 1 - index,
                )
            }
        }

        return ExecutionPlan(
            groups = groups,
            failurePolicy = failurePolicy,
            executionPolicy = executionPolicy,
            compensations = compensations,
        )
    }
}
