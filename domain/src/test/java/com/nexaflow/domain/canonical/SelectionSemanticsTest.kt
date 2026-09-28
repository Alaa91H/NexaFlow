package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SelectionSemanticsTest {

    private val wifiTarget = TargetId("core.connectivity.wifi")
    private val bluetoothTarget = TargetId("core.connectivity.bluetooth")

    private fun single(
        executionMode: ExecutionMode = ExecutionMode.SINGLE,
        failurePolicy: FailurePolicy? = null,
    ) = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = executionMode,
        failurePolicy = failurePolicy,
    )

    private fun multi(
        executionMode: ExecutionMode,
        failurePolicy: FailurePolicy? = null,
    ) = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.MULTI,
        executionMode = executionMode,
        failurePolicy = failurePolicy,
    )

    private fun setStateNode(
        id: String,
        target: TargetId,
        state: CanonicalValue,
    ) = SetStateNode(
        id = CanonicalNodeId(id),
        target = target,
        state = state,
    )

    @Test
    fun multiSelectWithoutExecutionModeFailsClosed() {
        val semantics = NodeSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.MULTI,
            executionMode = ExecutionMode.SINGLE,
        )

        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 3,
        )

        assertTrue(
            "expected a missing-execution-semantics error",
            errors.any { it is MultiTargetWithoutExecutionSemantics },
        )
    }

    @Test
    fun batchContradictoryWritesFailClosed() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)
        val writes = listOf(
            setStateNode("w1", wifiTarget, BooleanValue(true)),
            setStateNode("w2", wifiTarget, BooleanValue(false)),
        )

        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 2,
            plannedWrites = writes,
        )

        assertTrue(
            "expected a contradictory-batch-write error",
            errors.any { it is ContradictoryBatchWrite && it.target == wifiTarget },
        )
    }

    @Test
    fun multiSelectWithoutFailurePolicyFailsClosed() {
        val errors = validateSelectionSemantics(
            semantics = multi(ExecutionMode.ORDERED),
            selectedTargetCount = 2,
        )

        assertTrue(
            "expected a missing-failure-policy error",
            errors.any { it is MissingFailurePolicy },
        )
    }

    @Test
    fun validMultiSelectOrderedPasses() {
        val semantics = multi(ExecutionMode.ORDERED, FailurePolicy.CONTINUE_ON_ERROR)

        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 3,
            plannedWrites = listOf(
                setStateNode("w1", wifiTarget, BooleanValue(true)),
                setStateNode("w2", bluetoothTarget, BooleanValue(true)),
            ),
        )

        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun singleSelectionIsTheOnlyCompatibleExecutionMode() {
        listOf(
            ExecutionMode.BATCH,
            ExecutionMode.ORDERED,
        ).forEach { mode ->
            val errors = validateSelectionSemantics(
                semantics = single(executionMode = mode),
                selectedTargetCount = 1,
            )
            assertTrue(
                "SINGLE selection with $mode must fail",
                errors.any { it is ExecutionModeRequiresMultiSelection },
            )
        }

        val errors = validateSelectionSemantics(
            semantics = single(),
            selectedTargetCount = 1,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun singleSelectionDoesNotRequireFailurePolicy() {
        val errors = validateSelectionSemantics(
            semantics = single(),
            selectedTargetCount = 1,
        )
        assertTrue(errors.none { it is MissingFailurePolicy })
    }

    @Test
    fun batchConsistentWritesPass() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)
        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 2,
            plannedWrites = listOf(
                setStateNode("w1", wifiTarget, BooleanValue(true)),
                setStateNode("w2", wifiTarget, BooleanValue(true)),
            ),
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun batchWriteExpressionsFailClosedWhenUnprovable() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)
        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 2,
            plannedWrites = listOf(
                setStateNode("w1", wifiTarget, ExpressionValue("battery.level", CanonicalValueKind.INTEGER)),
                setStateNode("w2", wifiTarget, IntegerValue(42)),
            ),
        )
        assertTrue(
            "expected an unverifiable-batch-write error",
            errors.any { it is UnverifiableBatchWrite && it.target == wifiTarget },
        )
    }

    @Test
    fun batchSingleWriteWithExpressionPasses() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)
        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 1,
            plannedWrites = listOf(
                setStateNode("w1", wifiTarget, ExpressionValue("battery.level", CanonicalValueKind.INTEGER)),
            ),
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun cardinalityBoundsAreEnforced() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)

        val tooFew = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 0,
            cardinality = OperationCardinality(minTargets = 1),
        )
        assertTrue(
            "expected a cardinality violation",
            tooFew.any { it is CardinalityViolation },
        )

        val tooMany = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 3,
            cardinality = OperationCardinality.SINGLE_TARGET,
        )
        assertTrue(
            "expected a cardinality violation",
            tooMany.any { it is CardinalityViolation },
        )

        val exact = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 1,
            cardinality = OperationCardinality.SINGLE_TARGET,
        )
        assertTrue("expected no violations, got $exact", exact.isEmpty())
    }

    @Test
    fun requireValidSelectionSemanticsFailsClosedOnFirstError() {
        try {
            requireValidSelectionSemantics(
                semantics = NodeSelectionSemantics(
                    targetSelectionMode = TargetSelectionMode.MULTI,
                    executionMode = ExecutionMode.SINGLE,
                ),
                selectedTargetCount = 2,
            )
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                MultiTargetWithoutExecutionSemantics().message,
                expected.message,
            )
        }
    }

    @Test
    fun eventLogicOnlyDeclaresAnyOf() {
        // ADR-003: mutually exclusive events must not be expressible as ALL;
        // event combination is ANY_OF by design, so no other value exists.
        assertEquals(listOf(EventLogic.ANY_OF), EventLogic.entries.toList())
    }

    @Test
    fun nonWriteNodesAreIgnoredForBatchConflicts() {
        val semantics = multi(ExecutionMode.BATCH, FailurePolicy.FAIL_FAST)
        val writes = listOf(
            InvokeNode(
                id = CanonicalNodeId("i1"),
                target = wifiTarget,
            ),
            OpenNode(
                id = CanonicalNodeId("o1"),
                target = wifiTarget,
            ),
        )
        val errors = validateSelectionSemantics(
            semantics = semantics,
            selectedTargetCount = 2,
            plannedWrites = writes,
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }
}
