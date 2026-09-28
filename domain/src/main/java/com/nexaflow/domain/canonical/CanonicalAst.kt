package com.nexaflow.domain.canonical

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Version of the canonical AST wire shape introduced by T04.
 *
 * This is intentionally independent from legacy workflow/database versions.
 * ADR-013 expands version dimensions in later persistence phases.
 */
const val CANONICAL_AST_SCHEMA_VERSION: Int = 1

@Serializable
@JvmInline
value class CanonicalNodeId(val value: String) {
    init {
        require(ID.matches(value)) { "CanonicalNodeId must be a bounded stable local id" }
    }
    override fun toString(): String = value

    private companion object {
        val ID = Regex("[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}")
    }
}

@Serializable
enum class CanonicalPrimitive {
    SET_STATE,
    SET_VALUE,
    INVOKE,
    OPEN,
    SEND,
    TRANSFORM,
    INPUT,
    WAIT,
    OBSERVE,
    COMPARE,
    ACTION_SELECTION,
    EVENT_SELECTION,
    CONDITION_GROUP,
    SEQUENCE,
    BRANCH,
    RESTORE
}

/**
 * Canonical workflow AST. It is intentionally detached from legacy
 * TriggerType/ActionType and from UI families.
 */
@Serializable
data class CanonicalWorkflowAst(
    val schemaVersion: Int = CANONICAL_AST_SCHEMA_VERSION,
    val root: CanonicalNode
) {
    init {
        require(schemaVersion == CANONICAL_AST_SCHEMA_VERSION) {
            "Unsupported canonical AST schemaVersion=$schemaVersion"
        }
        validateCanonicalAst(root)
    }
}

@Serializable
sealed interface CanonicalNode {
    val id: CanonicalNodeId
    val primitive: CanonicalPrimitive
}

/** Trigger/event/state observation expressed through a registered predicate. */
@Serializable
@SerialName("observe")
data class ObserveNode(
    override val id: CanonicalNodeId,
    val target: TargetId,
    val predicate: PredicateId,
    val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.OBSERVE
}

/**
 * Pure comparison over typed values. Semantic operator validation is performed
 * by later schema/compiler phases; both operands are already type-tagged.
 */
@Serializable
@SerialName("compare")
data class CompareNode(
    override val id: CanonicalNodeId,
    val left: CanonicalValue,
    val operator: CompareOperator,
    val right: CanonicalValue
) : CanonicalNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.COMPARE
}

@Serializable
enum class CompareOperator {
    EQUALS,
    NOT_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    MATCHES
}

@Serializable
sealed interface CanonicalActionNode : CanonicalNode {
    val target: TargetId
    val arguments: CanonicalArguments
}

@Serializable
@SerialName("set_state")
data class SetStateNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val state: CanonicalValue,
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.SET_STATE
}

@Serializable
@SerialName("set_value")
data class SetValueNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val value: CanonicalValue,
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.SET_VALUE
}

@Serializable
@SerialName("invoke")
data class InvokeNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val operation: OperationId = OperationId("core.operation.invoke"),
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.INVOKE
}

@Serializable
@SerialName("open")
data class OpenNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val operation: OperationId = OperationId("core.operation.open"),
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.OPEN
}

@Serializable
@SerialName("send")
data class SendNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val operation: OperationId = OperationId("core.operation.send"),
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.SEND
}

@Serializable
@SerialName("transform")
data class TransformNode(
    override val id: CanonicalNodeId,
    override val target: TargetId = TargetId("core.data.transform"),
    val operation: OperationId = OperationId("core.operation.transform"),
    override val arguments: CanonicalArguments
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.TRANSFORM
}

@Serializable
@SerialName("input")
data class InputNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    val operation: OperationId = OperationId("core.operation.input"),
    override val arguments: CanonicalArguments
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.INPUT
}

@Serializable
@SerialName("wait")
data class WaitNode(
    override val id: CanonicalNodeId,
    val duration: DurationValue
) : CanonicalNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.WAIT
}

@Serializable
@SerialName("restore")
data class RestoreNode(
    override val id: CanonicalNodeId,
    override val target: TargetId,
    override val arguments: CanonicalArguments = CanonicalArguments.EMPTY
) : CanonicalActionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.RESTORE
}

/**
 * Structural workflow ordering. Multi-selection execution semantics are encoded
 * separately by [ActionSelectionNode] so an authored sequence is never
 * confused with a picker-generated ORDERED selection.
 */
@Serializable
@SerialName("sequence")
data class SequenceNode(
    override val id: CanonicalNodeId,
    val children: List<CanonicalNode>
) : CanonicalNode {
    init {
        require(children.isNotEmpty()) { "SequenceNode requires at least one child" }
        require(children.size <= MAX_CHILDREN) { "SequenceNode exceeds $MAX_CHILDREN children" }
    }
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.SEQUENCE

    private companion object {
        const val MAX_CHILDREN = 1024
    }
}

@Serializable
@SerialName("branch")
data class BranchNode(
    override val id: CanonicalNodeId,
    val condition: CanonicalConditionNode,
    val ifTrue: CanonicalNode,
    val ifFalse: CanonicalNode? = null
) : CanonicalNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.BRANCH
}

@Serializable
sealed interface CanonicalConditionNode : CanonicalNode

/**
 * Condition wrapper keeps the root AST strongly typed without allowing action
 * nodes to accidentally occupy condition positions.
 */
@Serializable
@SerialName("observed_condition")
data class ObservedConditionNode(
    override val id: CanonicalNodeId,
    val observation: ObserveNode
) : CanonicalConditionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.OBSERVE
}

@Serializable
@SerialName("comparison_condition")
data class ComparisonConditionNode(
    override val id: CanonicalNodeId,
    val comparison: CompareNode
) : CanonicalConditionNode {
    override val primitive: CanonicalPrimitive = CanonicalPrimitive.COMPARE
}

/** Bounded structural validation performed before compiler work in later T06+. */
fun validateCanonicalAst(root: CanonicalNode) {
    val seen = linkedSetOf<CanonicalNodeId>()

    fun visit(node: CanonicalNode, depth: Int) {
        require(depth <= MAX_AST_DEPTH) { "Canonical AST exceeds max depth $MAX_AST_DEPTH" }
        require(seen.add(node.id)) { "Duplicate CanonicalNodeId: ${node.id}" }

        when (node) {
            is SequenceNode -> node.children.forEach { visit(it, depth + 1) }
            is ActionSelectionNode -> node.actions.forEach { visit(it, depth + 1) }
            is EventSelectionNode -> node.events.forEach { visit(it, depth + 1) }
            is ConditionGroupNode -> node.conditions.forEach { visit(it, depth + 1) }
            is BranchNode -> {
                visit(node.condition, depth + 1)
                visit(node.ifTrue, depth + 1)
                node.ifFalse?.let { visit(it, depth + 1) }
            }
            is ObservedConditionNode -> {
                // The wrapped observation is payload of this condition, not a
                // second graph node, so its local id must match the wrapper.
                require(node.observation.id == node.id) {
                    "ObservedConditionNode and observation must share the same id"
                }
            }
            is ComparisonConditionNode -> {
                require(node.comparison.id == node.id) {
                    "ComparisonConditionNode and comparison must share the same id"
                }
            }
            else -> Unit
        }
    }

    visit(root, depth = 0)
}

private const val MAX_AST_DEPTH = 64
