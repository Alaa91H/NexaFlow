package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.operation.SemanticOperationId
import kotlinx.serialization.Serializable

/**
 * T09 — Canonical Validation Pipeline (plan §21 closure rule).
 *
 * Six ordered stages; a node only reaches a stage when every earlier stage is
 * clean. The closure condition is structural: [validate] returns a
 * [ValidationVerdict], and the planner (T10) accepts only verdicts whose
 * [ValidationVerdict.isValid] is true — an invalid configuration cannot reach
 * the planner through this API.
 *
 * Stages:
 *  1. Syntax — structural AST rules (bounded depth, unique node ids).
 *  2. Type — every payload is a typed canonical value by construction; the
 *     type stage re-checks constructor-enforced invariants are still the only
 *     values present (no re-parsing of strings).
 *  3. Schema — values validate against the declared NodeSchema (T08).
 *  4. Semantic — batch conflict rules (T06).
 *  5. Capability — declared capability requirements exist in the identity
 *     registry (T03) and the schema's capability linkage is declared.
 *  6. Security — destructive/high-risk security classes must declare at least
 *     one capability requirement, and secret-reference values never appear in
 *     non-SECRET_REFERENCE-typed fields.
 */
enum class ValidationStage {
    SYNTAX,
    TYPE,
    SCHEMA,
    SEMANTIC,
    CAPABILITY,
    SECURITY,
}

/** One failing finding, pinned to its stage and a stable machine rule name. */
@Serializable
data class ValidationFinding(
    val stage: ValidationStage,
    val rule: String,
    val message: String,
)

/**
 * The outcome of validating one workflow against its schema and rules.
 * [isValid] is the only flag the planner consults.
 */
data class ValidationVerdict(
    val findings: List<ValidationFinding>,
) {
    val isValid: Boolean
        get() = findings.isEmpty()

    /** Highest stage that was reached; later stages did not run when invalid. */
    val reachedStage: ValidationStage
        get() = findings.minOfOrNull { it.stage } ?: ValidationStage.SECURITY
}

/** Declared, review-time metadata a workflow carries into the pipeline. */
data class CanonicalWorkflowContract(
    val schema: NodeSchema,
    /** Selection semantics declared by the author (T05). */
    val semantics: NodeSelectionSemantics,
    /** Capability requirement declared for the node (capability stage). */
    val capabilityRequirement: CapabilityRequirement,
    /** Number of targets the node was configured with (T05 cardinality). */
    val selectedTargetCount: Int = 1,
    /** Per-operation cardinality (T05). */
    val cardinality: OperationCardinality? = null,
    /** T05 selection semantics overrides used by the check. */
    val semanticRules: NodeSemanticRules = NodeSemanticRules.default(),
)

/** The pure pipeline entry point. Deterministic: same input, same verdict. */
fun validate(
    ast: CanonicalWorkflowAst,
    contract: CanonicalWorkflowContract,
    values: List<NodeFieldValue>,
): ValidationVerdict {
    val findings = mutableListOf<ValidationFinding>()

    // Stage 1 — Syntax. The AST constructor already fails closed on depth,
    // duplicate ids and size bounds; constructing it is the syntax stage.
    // Re-validating here keeps the stage explicit and testable.
    runCatching { validateCanonicalAst(ast.root) }
        .onFailure {
            findings += ValidationFinding(
                ValidationStage.SYNTAX,
                "ast_structure_invalid",
                it.message ?: "canonical AST structure is invalid",
            )
        }
    if (findings.isNotEmpty()) return ValidationVerdict(findings)

    // Stage 2 — Type. CanonicalValue constructors are the type system; the
    // stage walks every node and argument and re-asserts their invariants.
    runCatching { validateTypedPayloads(ast.root) }
        .onFailure {
            findings += ValidationFinding(
                ValidationStage.TYPE,
                "typed_payload_invalid",
                it.message ?: "a typed payload violated its invariants",
            )
        }
    if (findings.isNotEmpty()) return ValidationVerdict(findings)

    // Stage 3 — Schema (T08).
    findings += validateNodeValues(contract.schema, values).map {
        ValidationFinding(ValidationStage.SCHEMA, it.rule, it.message)
    }
    if (findings.isNotEmpty()) return ValidationVerdict(findings)

    // Stage 4 — Semantic (T06) + selection semantics (T05).
    findings += evaluateSemanticRules(ast.root, contract.semanticRules).map {
        ValidationFinding(ValidationStage.SEMANTIC, it.rule, it.message)
    }
    findings += validateSelectionSemantics(
        semantics = contract.semantics,
        selectedTargetCount = contract.selectedTargetCount,
        cardinality = contract.cardinality,
    ).map {
        ValidationFinding(ValidationStage.SEMANTIC, "selection_semantics_violation", it.message)
    }
    if (findings.isNotEmpty()) return ValidationVerdict(findings)

    // Stage 5 — Capability. The declared requirement must reference registered
    // capabilities only (stable identity registry, T03).
    findings += capabilityFindings(contract.capabilityRequirement)
    if (findings.isNotEmpty()) return ValidationVerdict(findings)

    // Stage 6 — Security. High-risk classes must declare a capability;
    // secret references may only ride secret-typed schema fields.
    findings += securityFindings(contract.schema, values)

    return ValidationVerdict(findings)
}

private fun capabilityFindings(
    requirement: CapabilityRequirement,
): List<ValidationFinding> {
    val findings = mutableListOf<ValidationFinding>()
    val registered = CapabilityId.entries.toSet()

    fun visit(requirement: CapabilityRequirement) {
        when (requirement) {
            CapabilityRequirement.None -> Unit
            is CapabilityRequirement.Capability ->
                if (requirement.id !in registered) {
                    findings += ValidationFinding(
                        ValidationStage.CAPABILITY,
                        "unregistered_capability",
                        "capability ${requirement.id} is not registered in the identity registry",
                    )
                }
            is CapabilityRequirement.AllOf -> requirement.requirements.forEach(::visit)
            is CapabilityRequirement.AnyOf -> requirement.requirements.forEach(::visit)
            is CapabilityRequirement.Not -> visit(requirement.requirement)
        }
    }
    visit(requirement)
    return findings
}

/** Internal for tests; part of the security stage's implementation. */
internal fun securityFindings(
    schema: NodeSchema,
    values: List<NodeFieldValue>,
): List<ValidationFinding> {
    val findings = mutableListOf<ValidationFinding>()

    if (schema.securityClass == NodeSecurityClass.HIGH_RISK ||
        schema.securityClass == NodeSecurityClass.DESTRUCTIVE
    ) {
        if (schema.capabilities.isEmpty()) {
            findings += ValidationFinding(
                ValidationStage.SECURITY,
                "security_class_requires_capability",
                "security class ${schema.securityClass} requires at least one capability declaration",
            )
        }
    }

    val secretFieldIds = schema.fields
        .filter { it.type == NodeFieldType.SECRET_REFERENCE }
        .mapTo(mutableSetOf()) { it.id }
    for (value in values) {
        val isSecretValue = value.value is SecretReferenceValue
        val isSecretField = value.field in secretFieldIds
        if (isSecretValue && !isSecretField) {
            findings += ValidationFinding(
                ValidationStage.SECURITY,
                "secret_outside_secret_field",
                "secret reference supplied for non-secret field ${value.field.value}",
            )
        }
    }
    return findings
}

/**
 * Stage 2 walker: re-asserts typed payload invariants across the whole AST.
 * Canonical values validate themselves in their constructors; this stage
 * fails closed if any payload is structurally corrupted after construction.
 */
private fun validateTypedPayloads(root: CanonicalNode) {
    fun visit(node: CanonicalNode) {
        when (node) {
            is SequenceNode -> node.children.forEach(::visit)
            is BranchNode -> {
                visit(node.condition)
                visit(node.ifTrue)
                node.ifFalse?.let(::visit)
            }
            is ObservedConditionNode -> visit(node.observation)
            is ComparisonConditionNode -> {
                val comparison = node.comparison
                val leftIsExpression = comparison.left.kind == CanonicalValueKind.EXPRESSION
                val rightIsExpression = comparison.right.kind == CanonicalValueKind.EXPRESSION
                check(leftIsExpression == rightIsExpression) {
                    "Comparison operands must be consistently typed values"
                }
            }
            is CompareNode -> Unit // operands checked through its condition wrapper
            is WaitNode -> Unit // DurationValue is constructor-validated
            is ObserveNode -> node.arguments.entries.forEach {
                check(it.value.kind != CanonicalValueKind.EXPRESSION || it.value is ExpressionValue)
            }
            is CanonicalActionNode -> node.arguments.entries.forEach {
                check(it.value.kind != CanonicalValueKind.EXPRESSION || it.value is ExpressionValue)
            }
        }
    }
    visit(root)
}
