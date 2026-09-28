package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionPlannerTest {

    private val wifi = TargetId("core.connectivity.wifi")
    private val bluetooth = TargetId("core.connectivity.bluetooth")
    private val planner = CanonicalExecutionPlanner.default()

    private val schema = NodeSchema(
        schemaId = "core.schema.wifi.set_state",
        kind = NodeSchemaKind.ACTION,
        target = wifi,
        operation = OperationId("core.operation.set_state"),
        title = "Wi-Fi state",
        summaryTemplate = "Wi-Fi {enabled}",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
    )

    private val contract = CanonicalWorkflowContract(
        schema = schema,
        semantics = NodeSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.SINGLE,
            executionMode = ExecutionMode.SINGLE,
        ),
        capabilityRequirement = com.nexaflow.domain.capability.CapabilityRequirement.None,
    )

    private fun setState(id: String, target: TargetId, state: Boolean) =
        SetStateNode(CanonicalNodeId(id), target, BooleanValue(state))

    private fun values(enabled: Boolean) = listOf(
        NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(enabled)),
    )

    @Test
    fun actionsCompileIntoAtomicCommandsWithDeclaredSemantics() {
        val plan = planner.plan(
            root = setState("w1", wifi, true),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )

        val command = plan.allCommands.single()
        assertEquals("w1", command.commandId)
        assertEquals(wifi, command.target)
        assertEquals(OperationId("core.operation.set_state"), command.operation)
        assertEquals(BooleanValue(true), command.payload)
        assertEquals(CommandIdempotency.IDEMPOTENT, command.idempotency)
        assertTrue(command.reversible)
    }

    @Test
    fun planningIsDeterministic() {
        val root = SequenceNode(
            id = CanonicalNodeId("root"),
            children = listOf(
                setState("w1", wifi, true),
                setState("w2", bluetooth, false),
                WaitNode(CanonicalNodeId("wait"), DurationValue(500)),
                setState("w3", wifi, false),
            ),
        )

        val first = planner.plan(root, PlanExecutionPolicy.SEQUENTIAL, FailurePolicy.FAIL_FAST)
        val second = planner.plan(root, PlanExecutionPolicy.SEQUENTIAL, FailurePolicy.FAIL_FAST)

        assertEquals(first, second)
        assertEquals(3, first.groups.size)
        assertEquals(listOf("w1", "w2"), first.groups[0].commands.map { it.commandId })
        assertEquals(listOf("wait"), first.groups[1].commands.map { it.commandId })
    }

    @Test
    fun waitsSplitScopesAndPreserveOrdering() {
        val plan = planner.plan(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    setState("w1", wifi, true),
                    WaitNode(CanonicalNodeId("wait"), DurationValue(500)),
                    setState("w2", wifi, false),
                ),
            ),
            executionPolicy = PlanExecutionPolicy.PARALLEL_SAFE,
            failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
        )

        assertFalse(plan.groups[0].parallel)
        assertFalse(plan.groups[1].commands.single().let { !it.sideEffectFree }.not().not())
        assertTrue(plan.groups[1].commands.single().sideEffectFree)
        assertEquals(listOf("w2"), plan.groups[2].commands.map { it.commandId })
    }

    @Test
    fun conflictFreeBatchIsProvenParallelUnderParallelSafePolicy() {
        val plan = planner.plan(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    setState("w1", wifi, true),
                    setState("w2", bluetooth, true),
                ),
            ),
            executionPolicy = PlanExecutionPolicy.PARALLEL_SAFE,
            failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
        )

        assertTrue(plan.groups.single().parallel)
    }

    @Test
    fun conflictingBatchIsNotProvenParallel() {
        val plan = planner.plan(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    setState("w1", wifi, true),
                    setState("w2", wifi, false),
                ),
            ),
            executionPolicy = PlanExecutionPolicy.PARALLEL_SAFE,
            failurePolicy = FailurePolicy.CONTINUE_ON_ERROR,
        )

        // T06 rejects the conflicting write pair, so the group cannot be
        // proven parallel-safe; planning itself still succeeds because
        // evaluateWriteConflicts is consulted as proof, not as a gate here.
        assertFalse(plan.groups.single().parallel)
    }

    @Test
    fun sequentialPolicyNeverMarksGroupsParallel() {
        val plan = planner.plan(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    setState("w1", wifi, true),
                    setState("w2", bluetooth, true),
                ),
            ),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )

        assertFalse(plan.groups.single().parallel)
    }

    @Test
    fun bestEffortRequiresContinueOnError() {
        try {
            planner.plan(
                root = setState("w1", wifi, true),
                executionPolicy = PlanExecutionPolicy.BEST_EFFORT,
                failurePolicy = FailurePolicy.FAIL_FAST,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun transactionalRequiresRollbackPolicy() {
        try {
            planner.plan(
                root = setState("w1", wifi, true),
                executionPolicy = PlanExecutionPolicy.TRANSACTIONAL_WHEN_POSSIBLE,
                failurePolicy = FailurePolicy.FAIL_FAST,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun transactionalPlanProducesReverseOrderCompensations() {
        val plan = planner.plan(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    setState("w1", wifi, true),
                    setState("w2", bluetooth, false),
                    setState("w3", wifi, false),
                ),
            ),
            executionPolicy = PlanExecutionPolicy.TRANSACTIONAL_WHEN_POSSIBLE,
            failurePolicy = FailurePolicy.ROLLBACK_WHEN_SUPPORTED,
        )

        assertEquals(3, plan.compensations.size)
        assertEquals(listOf(2, 1, 0), plan.compensations.map { it.reverseRank })
    }

    @Test
    fun undeclaredOperationFailsPlanningClosed() {
        val plannerWithRegistry = CanonicalExecutionPlanner.of(
            listOf(
                CommandSemantics(
                    OperationId("core.operation.set_value"),
                    CommandIdempotency.IDEMPOTENT,
                    reversible = true,
                ),
            ),
        )

        try {
            plannerWithRegistry.plan(
                root = setState("w1", wifi, true),
                executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
                failurePolicy = FailurePolicy.FAIL_FAST,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("set_state"))
        }
    }

    @Test
    fun planValidatedRefusesInvalidVerdicts() {
        try {
            planner.planValidated(
                ast = CanonicalWorkflowAst(
                    root = setState("w1", wifi, true),
                ),
                contract = contract,
                values = emptyList(), // schema violation: missing required field
                executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
                failurePolicy = FailurePolicy.FAIL_FAST,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Refusing to plan"))
        }
    }

    @Test
    fun planValidatedPlansCleanWorkflows() {
        val plan = planner.planValidated(
            ast = CanonicalWorkflowAst(root = setState("w1", wifi, true)),
            contract = contract,
            values = values(true),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )

        assertEquals(listOf("w1"), plan.allCommands.map { it.commandId })
        assertEquals(FailurePolicy.FAIL_FAST, plan.failurePolicy)
    }
}
