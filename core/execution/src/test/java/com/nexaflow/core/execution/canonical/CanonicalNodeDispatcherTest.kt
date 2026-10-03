package com.nexaflow.core.execution.canonical

import com.nexaflow.core.execution.workflow.WorkflowExecutionBudget
import com.nexaflow.domain.canonical.CanonicalArgument
import com.nexaflow.domain.canonical.CanonicalArguments
import com.nexaflow.domain.canonical.CanonicalExecutionPlanner
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalNodeExecutionContract
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.CommandIdempotency
import com.nexaflow.domain.canonical.CommandSemantics
import com.nexaflow.domain.canonical.FailurePolicy
import com.nexaflow.domain.canonical.InvokeNode
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeFieldValue
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.NodeSelectionSemantics
import com.nexaflow.domain.canonical.PlanExecutionPolicy
import com.nexaflow.domain.canonical.OperationId
import com.nexaflow.domain.canonical.TargetId
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.CapabilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalNodeDispatcherTest {
    private val target = TargetId("core.example.target")
    private val operation = OperationId("core.example.operation")
    private val field = CanonicalFieldId("value")
    private val schema = NodeSchema(
        schemaId = "core.example.operation",
        kind = NodeSchemaKind.ACTION,
        target = target,
        operation = operation,
        title = "Example",
        fields = listOf(NodeSchemaField(field, NodeFieldType.TEXT, alwaysRequired = true)),
        summaryTemplate = "{value}",
    )
    private val node = InvokeNode(
        id = CanonicalNodeId("native.action.1"),
        target = target,
        operation = operation,
        arguments = CanonicalArguments(listOf(CanonicalArgument(field, TextValue("safe")))),
    )
    private val native = CanonicalWorkflowNode(
        kind = NodeSchemaKind.ACTION,
        definitionId = "core.example.operation",
        schema = schema,
        node = node,
        arguments = listOf(NodeFieldValue(field, TextValue("safe"))),
    )
    private val contract = CanonicalNodeExecutionContract(
        definitionId = native.definitionId,
        schema = schema,
        semantics = NodeSelectionSemantics(
            targetSelectionMode = com.nexaflow.domain.canonical.TargetSelectionMode.SINGLE,
            executionMode = com.nexaflow.domain.canonical.ExecutionMode.SINGLE,
        ),
        capabilityRequirement = CapabilityRequirement.None,
        executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
        failurePolicy = FailurePolicy.FAIL_FAST,
    )

    @Test
    fun registeredCanonicalNodeIsPlannedAndSentToItsTypedHandler() = runTest {
        var executions = 0
        val handler = object : CanonicalNodeHandler {
            override val definitionId = native.definitionId
            override suspend fun execute(
                node: CanonicalWorkflowNode,
                plan: com.nexaflow.domain.canonical.ExecutionPlan,
                context: CanonicalNodeExecutionContext,
            ): CanonicalNodeExecutionOutcome {
                executions++
                assertEquals(native.node.id.value, plan.allCommands.single().commandId)
                assertEquals(context.runId, "run-1")
                return CanonicalNodeExecutionOutcome(CapabilityStatus.SUCCESS, "done")
            }
        }
        val dispatcher = CanonicalNodeDispatcher(
            contracts = listOf(contract),
            handlers = CanonicalNodeHandlerRegistry(listOf(handler)),
            planner = CanonicalExecutionPlanner.of(
                listOf(CommandSemantics(operation, CommandIdempotency.NON_IDEMPOTENT, reversible = false)),
            ),
        )
        val result = dispatcher.execute(
            native,
            CanonicalNodeExecutionContext("workflow-1", "run-1", WorkflowExecutionBudget.create()),
        )
        assertTrue(result.success)
        assertEquals(1, executions)
    }

    @Test
    fun unregisteredOrMismatchedHandlerNeverRuns() = runTest {
        var executions = 0
        val handler = object : CanonicalNodeHandler {
            override val definitionId = "core.example.other"
            override suspend fun execute(
                node: CanonicalWorkflowNode,
                plan: com.nexaflow.domain.canonical.ExecutionPlan,
                context: CanonicalNodeExecutionContext,
            ): CanonicalNodeExecutionOutcome {
                executions++
                return CanonicalNodeExecutionOutcome(CapabilityStatus.SUCCESS, "unexpected")
            }
        }
        val dispatcher = CanonicalNodeDispatcher(
            contracts = listOf(contract),
            handlers = CanonicalNodeHandlerRegistry(listOf(handler)),
            planner = CanonicalExecutionPlanner.of(
                listOf(CommandSemantics(operation, CommandIdempotency.NON_IDEMPOTENT, reversible = false)),
            ),
        )
        val result = dispatcher.execute(
            native,
            CanonicalNodeExecutionContext("workflow-1", "run-2", WorkflowExecutionBudget.create()),
        )
        assertFalse(result.success)
        assertEquals(CapabilityStatus.UNSUPPORTED, result.status)
        assertEquals(0, executions)
    }
}
