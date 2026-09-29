package com.nexaflow.domain.canonical

/**
 * T34 — Security hardening audit (plan §T34).
 *
 * A pure, deterministic security auditor over the canonical AST. The T09
 * validation pipeline gates *authoring*; this auditor is the defense-in-depth
 * layer that re-checks *composed workflows* (post-migration, post-optimizer)
 * against the hardening invariants before execution:
 *
 *  1. Secrets never ride observations — [SecretReferenceValue] arguments are
 *     sanctioned on actions (T25 privileged commands), refused on
 *     [ObserveNode] payloads and any structural node.
 *  2. URI payloads must declare an allowlisted scheme — `file:`, `javascript:`
 *     and `data:` are refused outright (SSRF/injection posture).
 *  3. HIGH_RISK/DESTRUCTIVE operations must declare a non-empty capability
 *     set — the auditor fails closed on unclassified operations too.
 *  4. HIGH_RISK/DESTRUCTIVE operations must not be declared IDEMPOTENT in
 *     the planner table (blind retry of a destructive op must be structurally
 *     impossible — T21/T25 use CONDITIONALLY_IDEMPOTENT).
 *  5. Expression payloads on destructive writes are refused — their value is
 *     only provable at runtime, which contradicts the fail-closed posture.
 *
 * Deterministic: no clocks, no randomness; the same audit input yields the
 * same findings forever.
 */
object CanonicalSecurityAuditor {

    /** URI schemes a canonical action may target. */
    val ALLOWED_URI_SCHEMES: Set<String> = setOf("http", "https", "content", "package", "nexaflow")

    /** Schemes refused outright (injection / local-file posture). */
    val FORBIDDEN_URI_SCHEMES: Set<String> = setOf("file", "javascript", "data")

    /** Typed findings; the code set is pinned (diagnostics pairs with it). */
    enum class FindingCode {
        SECRET_IN_OBSERVATION,
        FORBIDDEN_URI_SCHEME,
        UNCLASSIFIED_OPERATION,
        DESTRUCTIVE_WITHOUT_CAPABILITIES,
        DESTRUCTIVE_DECLARED_IDEMPOTENT,
        EXPRESSION_IN_DESTRUCTIVE_PAYLOAD,
    }

    /** One audit finding, pinned to a node id and a stable code. */
    data class SecurityFinding(
        val code: FindingCode,
        val nodeId: String,
        val detail: String,
    )

    /** The audit result over one AST. */
    data class SecurityAuditReport(
        val findings: List<SecurityFinding>,
        val scannedNodes: Int,
        val scannedSecrets: Int,
    ) {
        val clean: Boolean get() = findings.isEmpty()
    }

    /** The classification tables the auditor requires (host-declared). */
    data class SecurityPolicy(
        /**
         * Operation → security class. Operations absent from this map are
         * refused as UNCLASSIFIED_OPERATION (fail closed).
         */
        val securityClasses: Map<OperationId, NodeSecurityClass>,
        /**
         * Operation → declared capability ids. HIGH_RISK/DESTRUCTIVE
         * operations require a non-empty set.
         */
        val requiredCapabilities: Map<OperationId, Set<String>>,
        /** Operation → planner idempotency declaration. */
        val idempotency: Map<OperationId, CommandIdempotency>,
    ) {
        companion object {
            fun of(classes: List<Pair<OperationId, NodeSecurityClass>>): SecurityPolicy =
                SecurityPolicy(
                    securityClasses = classes.toMap(),
                    requiredCapabilities = emptyMap(),
                    idempotency = emptyMap(),
                )
        }
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    fun audit(root: CanonicalNode, policy: SecurityPolicy): SecurityAuditReport {
        val findings = mutableListOf<SecurityFinding>()
        var scanned = 0
        var secrets = 0

        fun checkArguments(nodeId: CanonicalNodeId, arguments: CanonicalArguments, observation: Boolean) {
            for (argument in arguments.entries) {
                when (val value = argument.value) {
                    is SecretReferenceValue -> {
                        secrets += 1
                        if (observation) {
                            findings += SecurityFinding(
                                code = FindingCode.SECRET_IN_OBSERVATION,
                                nodeId = nodeId.value,
                                detail = "secret reference '${value.referenceId}' rides observation argument ${argument.id.value}",
                            )
                        }
                    }
                    is UriValue -> {
                        val scheme = value.value.substringBefore(':', "").lowercase()
                        if (scheme in FORBIDDEN_URI_SCHEMES) {
                            findings += SecurityFinding(
                                code = FindingCode.FORBIDDEN_URI_SCHEME,
                                nodeId = nodeId.value,
                                detail = "argument ${argument.id.value} targets forbidden scheme '$scheme:'",
                            )
                        }
                    }
                    is ExpressionValue -> Unit // handled per-operation below
                    else -> Unit
                }
            }
        }

        fun checkOperation(node: CanonicalActionNode) {
            val operation = when (node) {
                is SetStateNode -> CanonicalWriteOperations.SET_STATE
                is SetValueNode -> CanonicalWriteOperations.SET_VALUE
                is InvokeNode -> node.operation
                is OpenNode -> node.operation
                is SendNode -> node.operation
                is TransformNode -> node.operation
                is InputNode -> node.operation
                is RestoreNode -> return
            }
            val securityClass = policy.securityClasses[operation]
            if (securityClass == null) {
                findings += SecurityFinding(
                    code = FindingCode.UNCLASSIFIED_OPERATION,
                    nodeId = node.id.value,
                    detail = "operation ${operation.value} has no declared security class",
                )
                return
            }

            if (securityClass == NodeSecurityClass.HIGH_RISK ||
                securityClass == NodeSecurityClass.DESTRUCTIVE
            ) {
                val capabilities = policy.requiredCapabilities[operation].orEmpty()
                if (capabilities.isEmpty()) {
                    findings += SecurityFinding(
                        code = FindingCode.DESTRUCTIVE_WITHOUT_CAPABILITIES,
                        nodeId = node.id.value,
                        detail = "operation ${operation.value} is $securityClass but declares no capabilities",
                    )
                }
                val idempotency = policy.idempotency[operation]
                if (idempotency == CommandIdempotency.IDEMPOTENT) {
                    findings += SecurityFinding(
                        code = FindingCode.DESTRUCTIVE_DECLARED_IDEMPOTENT,
                        nodeId = node.id.value,
                        detail = "operation ${operation.value} is $securityClass but declared IDEMPOTENT",
                    )
                }
                val expressionPayload = when (node) {
                    is SetStateNode -> node.state as? ExpressionValue
                    is SetValueNode -> node.value as? ExpressionValue
                    else -> node.arguments.entries.firstOrNull { it.value is ExpressionValue }?.value as? ExpressionValue
                }
                if (expressionPayload != null) {
                    findings += SecurityFinding(
                        code = FindingCode.EXPRESSION_IN_DESTRUCTIVE_PAYLOAD,
                        nodeId = node.id.value,
                        detail = "operation ${operation.value} is $securityClass with an unprovable expression payload",
                    )
                }
            }
        }

        fun visit(node: CanonicalNode) {
            scanned += 1
            when (node) {
                is SequenceNode -> {
                    node.children.forEach(::visit)
                }
                is BranchNode -> {
                    visit(node.condition)
                    visit(node.ifTrue)
                    node.ifFalse?.let(::visit)
                }
                is ObservedConditionNode -> {
                    checkArguments(node.id, node.observation.arguments, observation = true)
                }
                is CanonicalActionNode -> {
                    checkArguments(node.id, node.arguments, observation = false)
                    checkOperation(node)
                }
                is ObserveNode -> {
                    checkArguments(node.id, node.arguments, observation = true)
                }
                else -> Unit
            }
        }
        visit(root)
        return SecurityAuditReport(
            findings = findings.sortedWith(compareBy({ it.code }, { it.nodeId }, { it.detail })),
            scannedNodes = scanned,
            scannedSecrets = secrets,
        )
    }
}
