package com.nexaflow.core.execution.workflow

import com.nexaflow.domain.workflow.CompareOp
import com.nexaflow.domain.workflow.ConditionExpr
import com.nexaflow.domain.workflow.PersistedWorkflowNodeV1
import com.nexaflow.domain.workflow.ValueExpr
import com.nexaflow.domain.workflow.WorkflowDocumentV1
import com.nexaflow.domain.workflow.WorkflowDocumentMappers

/**
 * Execution-boundary compiler from the versioned [WorkflowDocumentV1] onto the
 * runtime [WorkflowNode] graph (roadmap P0.1 NF-P0-007). Lives in
 * `core:execution` — the only place where a persisted [ConditionExpr] may
 * become the runtime lambda: the document model itself stays pure data.
 *
 * The compiler re-asserts the runtime's own guards; a document that passed the
 * structural validator can never violate them here.
 */
object WorkflowDocumentCompiler {

    /**
     * Compiles the document root into a runtime node tree. [conditionCompiler]
     * turns named predicate functions into the runtime condition — implemented
     * by the engine with typed access to device state and variables.
     */
    fun compile(
        document: WorkflowDocumentV1,
        conditionCompiler: (ConditionExpr) -> WorkflowCondition,
    ): WorkflowNode {
        val issues = WorkflowDocumentMappers.validateStructure(document)
        require(issues.isEmpty()) {
            "Invalid workflow structure: " + issues.joinToString()
        }
        return document.root.toRuntime(conditionCompiler)
    }

    /**
     * Convenience for documents that only use [ConditionExpr] kinds the
     * default compiler understands. Throws [IllegalArgumentException] when a
     * named function predicate has no registered implementation — never
     * silently evaluates to true.
     */
    fun compile(
        document: WorkflowDocumentV1,
        functionRegistry: Map<String, (Map<String, com.nexaflow.domain.workflow.RuntimeValueV1>) -> Boolean>,
    ): WorkflowNode = compile(document) { expr -> defaultCompiler(expr, functionRegistry) }

    private fun defaultCompiler(
        expr: ConditionExpr,
        registry: Map<String, (Map<String, com.nexaflow.domain.workflow.RuntimeValueV1>) -> Boolean>,
    ): WorkflowCondition = when (expr) {
        is ConditionExpr.Equals -> WorkflowCondition { eq(expr.left, expr.right) }
        is ConditionExpr.NotEquals -> WorkflowCondition { !eq(expr.left, expr.right) }
        is ConditionExpr.Compare -> WorkflowCondition { cmp(expr.left, expr.op, expr.right) }
        is ConditionExpr.And -> WorkflowCondition { expr.terms.all { defaultCompiler(it, registry).evaluate() } }
        is ConditionExpr.Or -> WorkflowCondition { expr.terms.any { defaultCompiler(it, registry).evaluate() } }
        is ConditionExpr.Not -> WorkflowCondition { !defaultCompiler(expr.term, registry).evaluate() }
        is ConditionExpr.Function -> {
            val fn = registry[expr.name]
                ?: throw IllegalArgumentException(
                    "Unknown condition function '${expr.name}' — register it in the compiler registry"
                )
            WorkflowCondition { fn(expr.arguments) }
        }
    }

    private suspend fun eq(left: ValueExpr, right: ValueExpr): Boolean =
        resolve(left) == resolve(right)

    private suspend fun cmp(left: ValueExpr, op: CompareOp, right: ValueExpr): Boolean {
        val l = resolve(left) ?: return false
        val r = resolve(right) ?: return false
        val comparison = compareResolved(l, r) ?: return false
        return when (op) {
            CompareOp.LESS_THAN -> comparison < 0
            CompareOp.LESS_OR_EQUAL -> comparison <= 0
            CompareOp.GREATER_THAN -> comparison > 0
            CompareOp.GREATER_OR_EQUAL -> comparison >= 0
        }
    }

    /**
     * Orders compatible values without relying on erased Comparable casts.
     * JVM Comparable is type-specific (Integer cannot compare itself to Long),
     * so mixed numeric RuntimeValue kinds are normalized to BigDecimal first.
     */
    private fun compareResolved(
        left: com.nexaflow.domain.workflow.RuntimeValueV1,
        right: com.nexaflow.domain.workflow.RuntimeValueV1,
    ): Int? {
        val leftNumber = left.asBigDecimalOrNull()
        val rightNumber = right.asBigDecimalOrNull()
        if (leftNumber != null || rightNumber != null) {
            return if (leftNumber != null && rightNumber != null) {
                leftNumber.compareTo(rightNumber)
            } else {
                null
            }
        }
        return if (
            left is com.nexaflow.domain.workflow.RuntimeValueV1.StringValue &&
            right is com.nexaflow.domain.workflow.RuntimeValueV1.StringValue
        ) {
            left.value.compareTo(right.value)
        } else {
            null
        }
    }

    private fun com.nexaflow.domain.workflow.RuntimeValueV1.asBigDecimalOrNull(): java.math.BigDecimal? =
        when (this) {
            is com.nexaflow.domain.workflow.RuntimeValueV1.IntValue ->
                java.math.BigDecimal.valueOf(value.toLong())
            is com.nexaflow.domain.workflow.RuntimeValueV1.LongValue ->
                java.math.BigDecimal.valueOf(value)
            is com.nexaflow.domain.workflow.RuntimeValueV1.DoubleValue ->
                java.math.BigDecimal.valueOf(value)
            else -> null
        }

    /**
     * Resolves [ValueExpr] at evaluation time. Literals are themselves;
     * context references are read from the ambient run context (see
     * [WorkflowRunContextHolder]); unresolved references become null and
     * comparisons fail closed rather than guessing.
     */
    private suspend fun resolve(expr: ValueExpr): com.nexaflow.domain.workflow.RuntimeValueV1? = when (expr) {
        is ValueExpr.Literal -> expr.value
        is ValueExpr.ContextRef -> WorkflowRunContextHolder.read(expr.path)
    }

    private fun PersistedWorkflowNodeV1.toRuntime(
        conditionCompiler: (ConditionExpr) -> WorkflowCondition,
    ): WorkflowNode = when (this) {
        is PersistedWorkflowNodeV1.Action -> WorkflowNode.ActionNode(
            id = nodeId,
            action = WorkflowDocumentMappers.run { action.toRuntimeAction() },
        )
        is PersistedWorkflowNodeV1.Sequence -> WorkflowNode.SequenceNode(
            id = nodeId,
            children = children.map { it.toRuntime(conditionCompiler) },
            rollbackOnFailure = rollbackOnFailure,
        )
        is PersistedWorkflowNodeV1.Parallel -> WorkflowNode.ParallelNode(
            id = nodeId,
            children = children.map { it.toRuntime(conditionCompiler) },
        )
        is PersistedWorkflowNodeV1.Branch -> WorkflowNode.BranchNode(
            id = nodeId,
            condition = conditionCompiler(condition),
            whenTrue = whenTrue.toRuntime(conditionCompiler),
            whenFalse = whenFalse?.toRuntime(conditionCompiler),
        )
        is PersistedWorkflowNodeV1.Loop -> WorkflowNode.LoopNode(
            id = nodeId,
            iterations = iterations,
            body = body.toRuntime(conditionCompiler),
        )
        is PersistedWorkflowNodeV1.Delay -> WorkflowNode.DelayNode(id = nodeId, delayMs = delayMs)
        is PersistedWorkflowNodeV1.Retry -> WorkflowNode.RetryNode(
            id = nodeId,
            body = body.toRuntime(conditionCompiler),
            maxAttempts = maxAttempts,
            backoffMs = backoffMs,
        )
        is PersistedWorkflowNodeV1.Timeout -> WorkflowNode.TimeoutNode(
            id = nodeId,
            body = body.toRuntime(conditionCompiler),
            timeoutMs = timeoutMs,
        )
        is PersistedWorkflowNodeV1.Race -> WorkflowNode.RaceNode(
            id = nodeId,
            children = children.map { it.toRuntime(conditionCompiler) },
        )
        is PersistedWorkflowNodeV1.While -> WorkflowNode.WhileNode(
            id = nodeId,
            condition = conditionCompiler(condition),
            body = body.toRuntime(conditionCompiler),
            maxIterations = maxIterations,
        )
        is PersistedWorkflowNodeV1.Try -> WorkflowNode.TryNode(
            id = nodeId,
            body = body.toRuntime(conditionCompiler),
            catchNode = catchNode?.toRuntime(conditionCompiler),
            finallyNode = finallyNode?.toRuntime(conditionCompiler),
        )
        is PersistedWorkflowNodeV1.WaitUntil -> WorkflowNode.WaitUntilNode(
            id = nodeId,
            condition = conditionCompiler(condition),
            timeoutMs = timeoutMs,
            pollIntervalMs = pollIntervalMs,
        )
    }
}
