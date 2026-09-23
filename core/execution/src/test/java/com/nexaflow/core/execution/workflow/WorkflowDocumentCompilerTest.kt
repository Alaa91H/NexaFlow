package com.nexaflow.core.execution.workflow

import com.nexaflow.core.logging.ExecutionTraceEvent
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.logging.TracePhase
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.core.logging.TraceRecorder
import com.nexaflow.domain.workflow.ConditionExpr
import com.nexaflow.domain.workflow.PersistedWorkflowNodeV1
import com.nexaflow.domain.workflow.RuntimeValueV1
import com.nexaflow.domain.workflow.ValueExpr
import com.nexaflow.domain.workflow.WorkflowDocumentMappers
import com.nexaflow.domain.workflow.WorkflowDocumentV1
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the execution-boundary document compiler and the typed
 * trace recorder (roadmap P0.4). Pins: data conditions evaluate through the
 * runtime; unknown named predicates fail loudly instead of guessing; trace
 * events land on the existing timeline with redaction applied at source.
 */
class WorkflowDocumentCompilerTest {

    // ------------------------------------------------------------------
    // Document → runtime compilation
    // ------------------------------------------------------------------

    private fun document(root: PersistedWorkflowNodeV1) = WorkflowDocumentV1(
        id = "doc-1",
        metadata = com.nexaflow.domain.workflow.WorkflowMetadataV1(name = "t"),
        root = root,
    )

    @Test
    fun documentCompilesToRuntimeGraph() {
        val doc = document(
            PersistedWorkflowNodeV1.Sequence(
                nodeId = "seq",
                children = listOf(
                    PersistedWorkflowNodeV1.Delay(nodeId = "d1", delayMs = 10),
                    PersistedWorkflowNodeV1.Action(
                        nodeId = "a1",
                        action = com.nexaflow.domain.workflow.PersistedActionV1(
                            nodeId = "a1",
                            type = "SYSTEM_WIFI",
                            config = mapOf("enabled" to "true"),
                        ),
                    ),
                ),
            ),
        )
        val node = WorkflowDocumentCompiler.compile(doc, functionRegistry = emptyMap())
        val seq = node as WorkflowNode.SequenceNode
        assertEquals(2, seq.children.size)
        assertEquals("d1", seq.children[0].id)
        assertEquals("a1", seq.children[1].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun compilerRejectsDuplicateNodeIdsBeforeBuildingRuntimeGraph() {
        val duplicate = PersistedWorkflowNodeV1.Action(
            nodeId = "same-id",
            action = com.nexaflow.domain.workflow.PersistedActionV1(
                nodeId = "action-payload-1",
                type = "SYSTEM_WIFI",
                config = mapOf("enabled" to "true"),
            ),
        )
        val doc = document(
            PersistedWorkflowNodeV1.Sequence(
                nodeId = "root",
                children = listOf(duplicate, duplicate),
            ),
        )
        WorkflowDocumentCompiler.compile(doc, functionRegistry = emptyMap())
    }

    @Test
    fun dataConditionsEvaluateWithLiteralValues() = runTest {
        val condition = ConditionExpr.And(
            terms = listOf(
                ConditionExpr.Compare(
                    left = ValueExpr.Literal(RuntimeValueV1.IntValue(60)),
                    op = com.nexaflow.domain.workflow.CompareOp.GREATER_THAN,
                    right = ValueExpr.Literal(RuntimeValueV1.IntValue(50)),
                ),
                ConditionExpr.Not(
                    ConditionExpr.Equals(
                        left = ValueExpr.Literal(RuntimeValueV1.StringValue("idle")),
                        right = ValueExpr.Literal(RuntimeValueV1.StringValue("active")),
                    ),
                ),
            ),
        )
        val node = WorkflowDocumentCompiler.compile(
            document(
                PersistedWorkflowNodeV1.Branch(
                    nodeId = "b",
                    condition = condition,
                    whenTrue = PersistedWorkflowNodeV1.Sequence(nodeId = "t", children = emptyList()),
                ),
            ),
            functionRegistry = emptyMap(),
        )
        val branch = node as WorkflowNode.BranchNode
        assertEquals(true, branch.condition.evaluate())
    }

    @Test
    fun mixedNumericConditionTypesCompareWithoutRuntimeCastFailures() = runTest {
        val condition = ConditionExpr.And(
            terms = listOf(
                ConditionExpr.Compare(
                    left = ValueExpr.Literal(RuntimeValueV1.IntValue(60)),
                    op = com.nexaflow.domain.workflow.CompareOp.LESS_THAN,
                    right = ValueExpr.Literal(RuntimeValueV1.LongValue(61L)),
                ),
                ConditionExpr.Compare(
                    left = ValueExpr.Literal(RuntimeValueV1.LongValue(61L)),
                    op = com.nexaflow.domain.workflow.CompareOp.LESS_THAN,
                    right = ValueExpr.Literal(RuntimeValueV1.DoubleValue(61.5)),
                ),
            ),
        )
        val node = WorkflowDocumentCompiler.compile(
            document(
                PersistedWorkflowNodeV1.Branch(
                    nodeId = "mixed-numeric",
                    condition = condition,
                    whenTrue = PersistedWorkflowNodeV1.Sequence(nodeId = "t", children = emptyList()),
                ),
            ),
            functionRegistry = emptyMap(),
        )
        val branch = node as WorkflowNode.BranchNode
        assertEquals(true, branch.condition.evaluate())
    }

    @Test
    fun mixedNumericAndStringComparisonFailsClosed() = runTest {
        val condition = ConditionExpr.Compare(
            left = ValueExpr.Literal(RuntimeValueV1.IntValue(10)),
            op = com.nexaflow.domain.workflow.CompareOp.LESS_THAN,
            right = ValueExpr.Literal(RuntimeValueV1.StringValue("20")),
        )
        val node = WorkflowDocumentCompiler.compile(
            document(
                PersistedWorkflowNodeV1.Branch(
                    nodeId = "mixed-incompatible",
                    condition = condition,
                    whenTrue = PersistedWorkflowNodeV1.Sequence(nodeId = "t", children = emptyList()),
                ),
            ),
            functionRegistry = emptyMap(),
        )
        val branch = node as WorkflowNode.BranchNode
        assertEquals(false, branch.condition.evaluate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownNamedPredicateFailsLoudlyInsteadOfGuessing() {
        val doc = document(
            PersistedWorkflowNodeV1.While(
                nodeId = "w",
                condition = ConditionExpr.Function(name = "device.isRootedButUndefined"),
                body = PersistedWorkflowNodeV1.Sequence(nodeId = "w:body", children = emptyList()),
            ),
        )
        WorkflowDocumentCompiler.compile(doc, functionRegistry = emptyMap())
    }

    @Test
    fun registeredNamedPredicateEvaluatesThroughRegistry() = runTest {
        val doc = document(
            PersistedWorkflowNodeV1.While(
                nodeId = "w",
                condition = ConditionExpr.Function(
                    name = "test.alwaysTrue",
                    arguments = mapOf("flag" to RuntimeValueV1.BooleanValue(true)),
                ),
                body = PersistedWorkflowNodeV1.Sequence(nodeId = "w:body", children = emptyList()),
            ),
        )
        val node = WorkflowDocumentCompiler.compile(
            doc,
            functionRegistry = mapOf("test.alwaysTrue" to { args -> args["flag"] == RuntimeValueV1.BooleanValue(true) }),
        )
        val whileNode = node as WorkflowNode.WhileNode
        assertEquals(true, whileNode.condition.evaluate())
    }

    @Test
    fun contextRefOutsideRunFailsClosed() = runTest {
        val condition = ConditionExpr.Equals(
            left = ValueExpr.ContextRef(path = "$.battery.level"),
            right = ValueExpr.Literal(RuntimeValueV1.IntValue(50)),
        )
        val node = WorkflowDocumentCompiler.compile(
            document(
                PersistedWorkflowNodeV1.While(
                    nodeId = "w",
                    condition = condition,
                    body = PersistedWorkflowNodeV1.Sequence(nodeId = "w:body", children = emptyList()),
                ),
            ),
            functionRegistry = emptyMap(),
        )
        val whileNode = node as WorkflowNode.WhileNode
        // No ambient run context: unresolved reference → false (fail closed).
        assertEquals(false, whileNode.condition.evaluate())
    }

    // ------------------------------------------------------------------
    // TraceRecorder (P0.4)
    // ------------------------------------------------------------------

    @Test
    fun traceEventsLandOnTheExistingTimelineWithRedaction() = runTest {
        val logStore = InMemoryLogStore()
        val recorder = TraceRecorder(logStore)

        // Use the public generic record() path directly. The recorder itself,
        // not only convenience builders or RedactingLogStore, owns redaction.
        recorder.record(
            ExecutionTraceEvent(
                id = "trace-1",
                runId = "run-1",
                automationId = "task-1",
                sequence = 0,
                phase = TracePhase.GATE_BLOCKED,
                reasonCode = TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
                detail = "charging mismatch; token=supersecret123",
                atEpochMs = 1_000L,
            ),
        )

        val timeline = logStore.timeline().first()
        assertEquals(1, timeline.size)
        val entry = timeline[0]
        assertTrue(entry.kind.startsWith("TRACE:"))
        assertTrue(entry.message.startsWith(TraceReasons.TRIGGER_ALL_GATE_BLOCKED))
        // Redaction at the recorder boundary, not just the UI.
        assertTrue("token=supersecret123" !in entry.message)
        assertTrue("[REDACTED]" in entry.message)
    }

    @Test
    fun traceEventPhasesCarryStructuredData() {
        val event = ExecutionTraceEvent(
            id = "e1",
            runId = "run-1",
            automationId = "task-1",
            sequence = 3,
            phase = TracePhase.VERIFICATION,
            reasonCode = TraceReasons.VERIFICATION_FAILED,
            detail = "read-back mismatch",
            backend = "SHIZUKU",
            nodeId = "action:task-1:0:SYSTEM_WIFI",
            atEpochMs = 5_000L,
            durationMs = 120L,
        )
        assertEquals(TracePhase.VERIFICATION, event.phase)
        assertEquals("SHIZUKU", event.backend)
        assertEquals("action:task-1:0:SYSTEM_WIFI", event.nodeId)
    }

    @Test
    fun documentEncodeDecodeRoundTripThroughCompilerSurface() {
        val legacy = com.nexaflow.domain.models.Automation(
            id = "rt",
            name = "rt",
            description = "",
            icon = "",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "general",
            priority = 5,
            enabled = false,
            triggers = emptyList(),
            actions = listOf(
                com.nexaflow.domain.models.Action(
                    type = com.nexaflow.domain.models.ActionType.SYSTEM_WIFI,
                    config = mapOf("enabled" to "false"),
                ),
            ),
            createdAt = 1L,
            updatedAt = 1L,
        )
        val doc = with(WorkflowDocumentMappers) { legacy.toDocument() }
        val decoded = WorkflowDocumentMappers.decode(WorkflowDocumentMappers.encode(doc))
        assertEquals(doc, decoded)
    }
}
