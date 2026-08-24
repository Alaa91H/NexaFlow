package com.nexaflow.core.execution.workflow

import com.nexaflow.domain.models.Action

/**
 * Additional workflow node types extending the core [WorkflowNode] sealed interface.
 *
 * These nodes are high-level control flow primitives not yet present in the base
 * [Workflow.kt] file. They are kept separate to allow staged migration of the
 * WorkflowInterpreter without breaking existing serialized workflows.
 */

/**
 * Invokes a named subworkflow identified by [workflowId], passing [inputParameters]
 * and collecting [outputParameters] into the parent workflow's variable scope.
 *
 * Subworkflow context is isolated by default: [isolatedContext] = true means the
 * child cannot read or write the parent's variables; setting it to false shares a
 * snapshot of the parent context, but writes do not propagate back up.
 *
 * Recursion depth is enforced by the interpreter at max [MAX_RECURSION_DEPTH].
 */
data class SubworkflowNode(
    override val id: String,
    val workflowId: String,
    val inputParameters: Map<String, String> = emptyMap(),
    val outputParameters: List<String> = emptyList(),
    val isolatedContext: Boolean = true
) : WorkflowNode {
    init {
        require(workflowId.isNotBlank()) { "subworkflow workflowId must not be blank" }
        require(inputParameters.size <= MAX_PARAM_COUNT) {
            "inputParameters must not exceed $MAX_PARAM_COUNT entries"
        }
        require(outputParameters.size <= MAX_PARAM_COUNT) {
            "outputParameters must not exceed $MAX_PARAM_COUNT entries"
        }
    }

    companion object {
        const val MAX_RECURSION_DEPTH = 8
        const val MAX_PARAM_COUNT = 32
    }
}

/**
 * Pauses workflow execution and emits a human-readable [prompt] notification.
 * Execution resumes only after an explicit APPROVE signal is received within
 * [timeoutMs]. If no signal arrives, [onTimeout] determines the outcome:
 * - FAIL: the entire workflow branch fails
 * - APPROVE: auto-approves (useful for low-risk confirmation gates)
 * - REJECT: treats silence as rejection
 *
 * This gate must be declared in the workflow's risk descriptor; workflows
 * without it cannot include a HumanApprovalNode.
 */
data class HumanApprovalNode(
    override val id: String,
    val prompt: String,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val onTimeout: TimeoutOutcome = TimeoutOutcome.FAIL
) : WorkflowNode {
    init {
        require(prompt.isNotBlank()) { "HumanApprovalNode prompt must not be blank" }
        require(prompt.length <= MAX_PROMPT_LENGTH) {
            "prompt must not exceed $MAX_PROMPT_LENGTH characters"
        }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "timeoutMs must be in $MIN_TIMEOUT_MS..$MAX_TIMEOUT_MS"
        }
    }

    enum class TimeoutOutcome { FAIL, APPROVE, REJECT }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 300_000L    // 5 min
        const val MIN_TIMEOUT_MS = 5_000L          // 5 sec
        const val MAX_TIMEOUT_MS = 86_400_000L     // 24 h
        const val MAX_PROMPT_LENGTH = 512
    }
}

/**
 * Forward action with an optional compensation action.
 * If this node's [body] succeeds but a later node in the workflow fails,
 * the [compensation] is run during saga rollback to undo the side effect.
 *
 * Example:
 *   SagaNode(
 *     body = ActionNode("create-file", createFileAction),
 *     compensation = ActionNode("delete-file", deleteFileAction)
 *   )
 */
data class SagaNode(
    override val id: String,
    val body: WorkflowNode,
    val compensation: WorkflowNode? = null
) : WorkflowNode

/**
 * ForEach node: iterates [items] and executes [body] once per item,
 * binding the current item value to [itemVariable] in the context.
 *
 * Iteration is sequential. Early exit on first failure unless
 * [continueOnFailure] is true.
 */
data class ForEachNode(
    override val id: String,
    val items: List<String>,
    val itemVariable: String,
    val body: WorkflowNode,
    val continueOnFailure: Boolean = false
) : WorkflowNode {
    init {
        require(items.size <= MAX_ITEMS) { "items must not exceed $MAX_ITEMS" }
        require(itemVariable.matches(VAR_PATTERN)) { "itemVariable is not a valid variable name" }
    }

    companion object {
        const val MAX_ITEMS = 1_000
        val VAR_PATTERN = Regex("[a-z][A-Za-z0-9_]{0,63}")
    }
}
