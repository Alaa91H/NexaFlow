package com.nexaflow.core.execution.canonical

import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalNodeExecutionContract
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.CanonicalNativeNodeSchemaRegistry
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.ExecutionMode
import com.nexaflow.domain.canonical.FailurePolicy
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeFieldValue
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.NodeSelectionSemantics
import com.nexaflow.domain.canonical.OperationId
import com.nexaflow.domain.canonical.PlanExecutionPolicy
import com.nexaflow.domain.canonical.TargetId
import com.nexaflow.domain.canonical.TargetSelectionMode
import com.nexaflow.domain.canonical.WaitNode
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.CapabilityStatus
import kotlinx.coroutines.delay

/** Canonical delay action: independent of legacy ActionType identifiers. */
object CanonicalDelayDefinition {
    const val ID = "core.workflow.delay"
    const val MAX_DURATION_MS = 300_000L
    val durationField = CanonicalFieldId("duration_ms")
    val target = TargetId("core.flow.delay")
    val operation = OperationId("core.operation.wait")

    val schema = CanonicalNativeNodeSchemaRegistry.delay

    val contract = CanonicalNodeExecutionContract(
        definitionId = ID,
        schema = schema,
        semantics = NodeSelectionSemantics(
            targetSelectionMode = TargetSelectionMode.SINGLE,
            executionMode = ExecutionMode.SINGLE,
        ),
        capabilityRequirement = CapabilityRequirement.None,
        executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
        failurePolicy = FailurePolicy.FAIL_FAST,
    )

    fun node(durationMs: Long, nodeId: String, sequenceIndex: Int): CanonicalWorkflowNode {
        val value = DurationValue(durationMs)
        val values = listOf(NodeFieldValue(durationField, value))
        return CanonicalWorkflowNode(
            kind = NodeSchemaKind.ACTION,
            definitionId = ID,
            schema = schema,
            node = WaitNode(CanonicalNodeId(nodeId), value),
            arguments = values,
            sequenceIndex = sequenceIndex,
        )
    }
}

class CanonicalDelayHandler : CanonicalNodeHandler {
    override val definitionId: String = CanonicalDelayDefinition.ID

    override suspend fun execute(
        node: CanonicalWorkflowNode,
        plan: com.nexaflow.domain.canonical.ExecutionPlan,
        context: CanonicalNodeExecutionContext,
    ): CanonicalNodeExecutionOutcome {
        val wait = node.node as? WaitNode
            ?: return CanonicalNodeExecutionOutcome(CapabilityStatus.FAILED, "Invalid delay node")
        if (plan.allCommands.singleOrNull()?.operation != CanonicalDelayDefinition.operation ||
            plan.allCommands.singleOrNull()?.target != CanonicalDelayDefinition.target
        ) {
            return CanonicalNodeExecutionOutcome(CapabilityStatus.FAILED, "Invalid delay plan")
        }
        val requested = wait.duration.milliseconds
        if (requested !in 0L..CanonicalDelayDefinition.MAX_DURATION_MS ||
            requested > context.budget.remainingTimeMs()
        ) {
            return CanonicalNodeExecutionOutcome(CapabilityStatus.FAILED, "Delay exceeds workflow execution budget")
        }
        delay(requested)
        return CanonicalNodeExecutionOutcome(CapabilityStatus.SUCCESS, "Delay completed")
    }
}
