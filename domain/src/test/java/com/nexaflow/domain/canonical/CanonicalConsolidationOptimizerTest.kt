package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T29 — Safe consolidation optimizer tests. Every rule is pinned against its
 * execution-equivalence argument: keep-last preserves the final desired
 * state; wait merging preserves the exact total delay.
 */
class CanonicalConsolidationOptimizerTest {

    private val wifi = TargetId("core.connectivity.wifi")
    private val bt = TargetId("core.connectivity.bluetooth")

    private fun setState(id: String, target: TargetId, value: Boolean) =
        SetStateNode(CanonicalNodeId(id), target, BooleanValue(value))

    private fun setValue(id: String, target: TargetId, value: Long) =
        SetValueNode(CanonicalNodeId(id), target, IntegerValue(value))

    private fun wait(id: String, ms: Long) =
        WaitNode(CanonicalNodeId(id), DurationValue(ms))

    private fun seq(vararg children: CanonicalNode) =
        SequenceNode(CanonicalNodeId("seq"), children.toList())

    // ------------------------------------------------------------------
    // Rule 1 — redundant desired-state rewrites
    // ------------------------------------------------------------------

    @Test
    fun adjacentIdenticalWritesCollapseToTheLast() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setState("w2", wifi, true),
            ),
        )

        val children = (result.root as SequenceNode).children
        assertEquals(1, children.size)
        assertEquals("w2", (children.single() as SetStateNode).id.value)
        assertEquals(1, result.report.eliminatedRedefinitions)
    }

    @Test
    fun differentPayloadOnSameTargetIsNeverCollapsed() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setState("w2", wifi, false),
            ),
        )

        assertEquals(2, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    @Test
    fun differentTargetsWithSamePayloadAreNeverCollapsed() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setState("w2", bt, true),
            ),
        )

        assertEquals(2, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    @Test
    fun nonAdjacentDuplicatesSurviveUntouched() {
        // w1 ... w3: a wait between them makes collapsing change timing, so
        // the optimizer must leave both writes alone.
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                wait("t1", 500),
                setState("w3", wifi, true),
            ),
        )

        assertEquals(3, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    @Test
    fun expressionPayloadsAreNeverCollapsed() {
        val expr = ExpressionValue("device.state + 1", CanonicalValueKind.INTEGER)
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                SetValueNode(CanonicalNodeId("v1"), wifi, expr),
                SetValueNode(CanonicalNodeId("v2"), wifi, IntegerValue(4)),
            ),
        )

        assertEquals(2, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    @Test
    fun mixedPrimitivesAreNeverCollapsed() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("s1", wifi, true),
                setValue("v1", wifi, 1),
            ),
        )

        assertEquals(2, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    @Test
    fun chainedDuplicatesCollapseInOnePassToTheSingleSurvivor() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setState("w2", wifi, true),
                setState("w3", wifi, true),
            ),
        )

        val children = (result.root as SequenceNode).children
        assertEquals(1, children.size)
        assertEquals("w3", (children.single() as SetStateNode).id.value)
        assertEquals(2, result.report.eliminatedRedefinitions)
    }

    // ------------------------------------------------------------------
    // Rule 2 — adjacent waits
    // ------------------------------------------------------------------

    @Test
    fun adjacentWaitsMergeToTheExactSum() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                wait("t1", 300),
                wait("t2", 500),
            ),
        )

        val children = (result.root as SequenceNode).children
        val merged = children.single() as WaitNode
        assertEquals(800L, merged.duration.milliseconds)
        assertEquals("t2", merged.id.value)
        assertEquals(1, result.report.mergedWaits)
    }

    @Test
    fun mergedWaitThenWriteThenWaitIsUntouched() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                wait("t1", 300),
                setState("w1", wifi, true),
                wait("t2", 500),
            ),
        )

        assertEquals(3, (result.root as SequenceNode).children.size)
        assertEquals(0, result.report.removedNodes)
    }

    // ------------------------------------------------------------------
    // Safety contract
    // ------------------------------------------------------------------

    @Test
    fun optimizedTreeRevalidatesAgainstTheAstContract() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setState("w2", wifi, true),
                wait("t1", 100),
                wait("t2", 200),
                setValue("v1", bt, 7),
            ),
        )

        // Throws when a structural invariant is broken — it must not.
        validateCanonicalAst(result.root)
        assertEquals(3, (result.root as SequenceNode).children.size)
    }

    @Test
    fun optimizationIsIdempotentWithAZeroSecondReport() {
        val original = seq(
            setState("w1", wifi, true),
            setState("w2", wifi, true),
            wait("t1", 100),
            wait("t2", 200),
        )
        val first = CanonicalConsolidationOptimizer.optimize(original)
        val second = CanonicalConsolidationOptimizer.optimize(first.root)

        assertEquals(first.root, second.root)
        assertEquals(0, second.report.removedNodes)
        assertTrue(second.report.passesUsed >= 1)
    }

    @Test
    fun nestedBranchesAreOptimizedRecursively() {
        val branch = BranchNode(
            id = CanonicalNodeId("branch"),
            condition = ObservedConditionNode(
                id = CanonicalNodeId("obs"),
                observation = ObserveNode(
                    id = CanonicalNodeId("obs"),
                    target = wifi,
                    predicate = PredicateId("core.predicate.match_state"),
                    arguments = CanonicalArguments(
                        listOf(
                            CanonicalArgument(
                                CanonicalFieldId("connected"),
                                BooleanValue(true),
                            ),
                        ),
                    ),
                ),
            ),
            ifTrue = seq(
                setState("t1", wifi, true),
                setState("t2", wifi, true),
            ),
        )
        val result = CanonicalConsolidationOptimizer.optimize(branch)

        val optimizedBranch = result.root as BranchNode
        assertEquals(1, (optimizedBranch.ifTrue as SequenceNode).children.size)
        assertEquals(1, result.report.eliminatedRedefinitions)
    }

    @Test
    fun emptyReportOnAnAlreadyCleanTree() {
        val result = CanonicalConsolidationOptimizer.optimize(
            seq(
                setState("w1", wifi, true),
                setValue("v1", bt, 7),
            ),
        )

        assertEquals(0, result.report.removedNodes)
        assertEquals(1, result.report.passesUsed)
    }
}
