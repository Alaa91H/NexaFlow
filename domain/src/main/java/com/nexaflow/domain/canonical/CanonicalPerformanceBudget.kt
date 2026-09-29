package com.nexaflow.domain.canonical

/**
 * T33 — Structural performance budgets (plan §T33).
 *
 * Pure, deterministic cost model over the canonical AST and its execution
 * plan. Wall-clock benchmarking belongs to device harnesses; the domain's
 * enforceable contract is *structural budget*: tree size, depth, command
 * count and plan-shape costs that predict runtime cost and fail closed
 * before a workflow can be saved, migrated or planned at a size the runtime
 * cannot honor.
 *
 * Contracts pinned by tests:
 *  - Deterministic: the same tree measures identically forever (no clocks);
 *  - Fail closed: every exceeded budget produces a typed violation naming
 *    the limit, the observed value and the bound;
 *  - Actionable: budgets integrate with T29 — optimizing a tree re-measures
 *    to a smaller (or equal) report.
 */
object CanonicalPerformanceBudget {

    /** Default budgets (mirror the AST/planner hard invariants). */
    const val DEFAULT_MAX_NODES: Int = 2_048
    const val DEFAULT_MAX_DEPTH: Int = 64
    const val DEFAULT_MAX_WAITS: Int = 256
    const val DEFAULT_MAX_PLAN_COMMANDS: Int = 2_048
    const val DEFAULT_MAX_PLAN_GROUPS: Int = 2_048

    /** Configurable budget set (defaults are the shipped limits). */
    data class Budget(
        val maxNodes: Int = DEFAULT_MAX_NODES,
        val maxDepth: Int = DEFAULT_MAX_DEPTH,
        val maxWaits: Int = DEFAULT_MAX_WAITS,
        val maxPlanCommands: Int = DEFAULT_MAX_PLAN_COMMANDS,
        val maxPlanGroups: Int = DEFAULT_MAX_PLAN_GROUPS,
    ) {
        init {
            require(maxNodes >= 1) { "maxNodes must be >= 1" }
            require(maxDepth >= 1) { "maxDepth must be >= 1" }
            require(maxWaits >= 1) { "maxWaits must be >= 1" }
            require(maxPlanCommands >= 1) { "maxPlanCommands must be >= 1" }
            require(maxPlanGroups >= 1) { "maxPlanGroups must be >= 1" }
        }
    }

    /** Structural measurement of one AST. */
    data class AstMetrics(
        val nodeCount: Int,
        val maxDepth: Int,
        val waitCount: Int,
    )

    /** Structural measurement of one execution plan. */
    data class PlanMetrics(
        val groupCount: Int,
        val commandCount: Int,
        val compensationCount: Int,
    )

    /** Typed violation with limit and observation. */
    data class BudgetViolation(
        val rule: String,
        val observed: Int,
        val limit: Int,
    ) {
        override fun toString(): String = "$rule observed=$observed limit=$limit"
    }

    /** Full measurement of one workflow (AST + optional plan). */
    data class PerformanceReport(
        val ast: AstMetrics,
        val plan: PlanMetrics?,
        val violations: List<BudgetViolation>,
    ) {
        val withinBudget: Boolean get() = violations.isEmpty()
    }

    // ------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------

    fun measureAst(root: CanonicalNode): AstMetrics {
        var nodes = 0
        var maxDepth = 0
        var waits = 0

        fun visit(node: CanonicalNode, depth: Int) {
            nodes += 1
            if (depth > maxDepth) maxDepth = depth
            when (node) {
                is WaitNode -> waits += 1
                is SequenceNode -> node.children.forEach { visit(it, depth + 1) }
                is BranchNode -> {
                    visit(node.condition, depth + 1)
                    visit(node.ifTrue, depth + 1)
                    node.ifFalse?.let { visit(it, depth + 1) }
                }
                else -> Unit
            }
        }
        visit(root, depth = 1)
        return AstMetrics(nodeCount = nodes, maxDepth = maxDepth, waitCount = waits)
    }

    fun measurePlan(plan: ExecutionPlan): PlanMetrics = PlanMetrics(
        groupCount = plan.groups.size,
        commandCount = plan.allCommands.size,
        compensationCount = plan.compensations.size,
    )

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    fun validate(
        ast: CanonicalNode,
        plan: ExecutionPlan?,
        budget: Budget = Budget(),
    ): PerformanceReport {
        val astMetrics = measureAst(ast)
        val planMetrics = plan?.let(::measurePlan)
        val violations = mutableListOf<BudgetViolation>()

        if (astMetrics.nodeCount > budget.maxNodes) {
            violations += BudgetViolation("nodes_exceeded", astMetrics.nodeCount, budget.maxNodes)
        }
        if (astMetrics.maxDepth > budget.maxDepth) {
            violations += BudgetViolation("depth_exceeded", astMetrics.maxDepth, budget.maxDepth)
        }
        if (astMetrics.waitCount > budget.maxWaits) {
            violations += BudgetViolation("waits_exceeded", astMetrics.waitCount, budget.maxWaits)
        }
        if (planMetrics != null) {
            if (planMetrics.commandCount > budget.maxPlanCommands) {
                violations += BudgetViolation(
                    "plan_commands_exceeded",
                    planMetrics.commandCount,
                    budget.maxPlanCommands,
                )
            }
            if (planMetrics.groupCount > budget.maxPlanGroups) {
                violations += BudgetViolation(
                    "plan_groups_exceeded",
                    planMetrics.groupCount,
                    budget.maxPlanGroups,
                )
            }
        }
        return PerformanceReport(astMetrics, planMetrics, violations)
    }

    /**
     * Convenience: plan a tree through the default planner shape (single
     * sequential scope) is not needed here — callers that hold a plan pass
     * it; callers without one get AST-only budgets.
     */
    fun validateAstOnly(ast: CanonicalNode, budget: Budget = Budget()): PerformanceReport =
        validate(ast, plan = null, budget = budget)
}
