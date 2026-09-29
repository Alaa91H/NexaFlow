package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T33 — Structural performance budget tests: deterministic measurement,
 * typed violations naming limit and observation, and optimizer integration.
 */
class CanonicalPerformanceBudgetTest {

    private val wifi = TargetId("core.connectivity.wifi")

    private fun setState(id: String, value: Boolean) =
        SetStateNode(CanonicalNodeId(id), wifi, BooleanValue(value))

    private fun wait(id: String, ms: Long) =
        WaitNode(CanonicalNodeId(id), DurationValue(ms))

    private fun seq(vararg children: CanonicalNode) =
        SequenceNode(CanonicalNodeId("seq"), children.toList())

    // ------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------

    @Test
    fun astMetricsCountNodesDepthAndWaits() {
        val tree = seq(
            setState("w1", true),
            wait("t1", 100),
            seq(
                wait("t2", 200),
                setState("w2", false),
            ),
        )

        val metrics = CanonicalPerformanceBudget.measureAst(tree)

        // 1 root sequence + w1 + t1 + nested sequence + t2 + w2 = 6.
        assertEquals(6, metrics.nodeCount)
        assertEquals(3, metrics.maxDepth)
        assertEquals(2, metrics.waitCount)
    }

    @Test
    fun singleNodeMeasuresDepthOne() {
        val metrics = CanonicalPerformanceBudget.measureAst(setState("w1", true))

        assertEquals(1, metrics.nodeCount)
        assertEquals(1, metrics.maxDepth)
        assertEquals(0, metrics.waitCount)
    }

    @Test
    fun planMetricsCountGroupsAndCommands() {
        val plan = CanonicalExecutionPlanner.default().plan(
            root = seq(
                setState("w1", true),
                wait("t1", 100),
                setState("w2", false),
            ),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )

        val metrics = CanonicalPerformanceBudget.measurePlan(plan)

        // Batching: [w1] | wait | [w2] — each batch/wait is its own group.
        assertEquals(3, metrics.groupCount)
        assertEquals(3, metrics.commandCount)
    }

    // ------------------------------------------------------------------
    // Budgets
    // ------------------------------------------------------------------

    @Test
    fun withinBudgetReportHasNoViolations() {
        val tree = seq(
            setState("w1", true),
            wait("t1", 100),
        )
        val plan = CanonicalExecutionPlanner.default().plan(
            tree,
            PlanExecutionPolicy.SEQUENTIAL,
            FailurePolicy.FAIL_FAST,
        )

        val report = CanonicalPerformanceBudget.validate(tree, plan)

        assertTrue(report.withinBudget)
        assertEquals(emptyList<CanonicalPerformanceBudget.BudgetViolation>(), report.violations)
        assertEquals(2, report.plan?.commandCount)
    }

    @Test
    fun exceededNodeBudgetProducesATypedViolation() {
        val tree = seq(
            setState("w1", true),
            setState("w2", true),
            setState("w3", true),
        )
        val report = CanonicalPerformanceBudget.validateAstOnly(
            tree,
            budget = CanonicalPerformanceBudget.Budget(maxNodes = 3),
        )

        // Root + 3 children = 4 nodes > 3.
        assertEquals(1, report.violations.size)
        val violation = report.violations.single()
        assertEquals("nodes_exceeded", violation.rule)
        assertEquals(4, violation.observed)
        assertEquals(3, violation.limit)
        assertTrue(violation.toString().contains("nodes_exceeded"))
    }

    @Test
    fun exceededWaitBudgetIsReportedSeparately() {
        val tree = seq(wait("t1", 1), wait("t2", 2), wait("t3", 3))
        val report = CanonicalPerformanceBudget.validateAstOnly(
            tree,
            budget = CanonicalPerformanceBudget.Budget(maxWaits = 2),
        )

        assertEquals(1, report.violations.size)
        assertEquals("waits_exceeded", report.violations.single().rule)
        assertEquals(3, report.violations.single().observed)
    }

    @Test
    fun exceededPlanCommandBudgetIsReported() {
        val tree = seq(
            setState("w1", true),
            setState("w2", true),
            setState("w3", true),
        )
        val plan = CanonicalExecutionPlanner.default().plan(
            tree,
            PlanExecutionPolicy.SEQUENTIAL,
            FailurePolicy.FAIL_FAST,
        )
        val report = CanonicalPerformanceBudget.validate(
            tree,
            plan,
            budget = CanonicalPerformanceBudget.Budget(maxPlanCommands = 2),
        )

        assertEquals(1, report.violations.size)
        assertEquals("plan_commands_exceeded", report.violations.single().rule)
        assertEquals(3, report.violations.single().observed)
    }

    @Test
    fun multipleViolationsStackInFixedRuleOrder() {
        val tree = seq(wait("t1", 1), wait("t2", 2))
        val report = CanonicalPerformanceBudget.validateAstOnly(
            tree,
            budget = CanonicalPerformanceBudget.Budget(maxNodes = 1, maxDepth = 1, maxWaits = 1),
        )

        assertEquals(
            listOf("nodes_exceeded", "depth_exceeded", "waits_exceeded"),
            report.violations.map { it.rule },
        )
    }

    @Test
    fun measurementIsDeterministicAcrossRuns() {
        val tree = seq(
            setState("w1", true),
            wait("t1", 100),
            seq(wait("t2", 200)),
        )

        assertEquals(
            CanonicalPerformanceBudget.measureAst(tree),
            CanonicalPerformanceBudget.measureAst(tree),
        )
    }

    @Test
    fun budgetConstructorRejectsNonPositiveLimits() {
        try {
            CanonicalPerformanceBudget.Budget(maxNodes = 0)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("maxNodes"))
        }
    }

    // ------------------------------------------------------------------
    // Optimizer integration (T29)
    // ------------------------------------------------------------------

    @Test
    fun optimizingAnOverBudgetTreeShrinksTheReport() {
        val tree = seq(
            setState("w1", true),
            setState("w2", true),
            setState("w3", true),
            setState("w4", true),
        )
        val budget = CanonicalPerformanceBudget.Budget(maxNodes = 4)

        val before = CanonicalPerformanceBudget.validateAstOnly(tree, budget)
        assertTrue(!before.withinBudget)

        val optimized = CanonicalConsolidationOptimizer.optimize(tree)
        val after = CanonicalPerformanceBudget.validateAstOnly(optimized.root, budget)

        assertTrue(after.withinBudget)
        // Root + 1 surviving child = 2 nodes.
        assertEquals(2, after.ast.nodeCount)
    }

    @Test
    fun mergedWaitsReduceTheWaitCount() {
        val tree = seq(wait("t1", 100), wait("t2", 200), wait("t3", 300))
        val optimizedRoot = CanonicalConsolidationOptimizer.optimize(tree).root
        val report = CanonicalPerformanceBudget.validateAstOnly(optimizedRoot)

        assertEquals(1, report.ast.waitCount)
        val merged = (optimizedRoot as SequenceNode).children.single() as WaitNode
        assertEquals(600L, merged.duration.milliseconds)
    }
}
