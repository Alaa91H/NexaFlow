package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T34 — Security auditor tests: defense-in-depth over composed workflows.
 */
class CanonicalSecurityAuditorTest {

    private val wifi = TargetId("core.connectivity.wifi")
    private val http = TargetId("core.external.http")

    private val sendOp = OperationId("core.operation.send")

    private fun policy(
        idempotency: CommandIdempotency = CommandIdempotency.NON_IDEMPOTENT,
        capabilities: Set<String> = setOf("core.capability.network"),
        classes: Map<OperationId, NodeSecurityClass> = mapOf(
            CanonicalWriteOperations.SET_STATE to NodeSecurityClass.STANDARD,
            sendOp to NodeSecurityClass.SENSITIVE,
        ),
    ) = CanonicalSecurityAuditor.SecurityPolicy(
        securityClasses = classes,
        requiredCapabilities = mapOf(sendOp to capabilities),
        idempotency = mapOf(sendOp to idempotency),
    )

    private fun httpSend(vararg arguments: CanonicalArgument) =
        SendNode(
            id = CanonicalNodeId("send-1"),
            target = http,
            operation = sendOp,
            arguments = CanonicalArguments(arguments.toList()),
        )

    private fun seq(vararg children: CanonicalNode) =
        SequenceNode(CanonicalNodeId("seq"), children.toList())

    // ------------------------------------------------------------------
    // Secrets
    // ------------------------------------------------------------------

    @Test
    fun secretsOnActionsAreSanctioned() {
        val tree = seq(
            httpSend(
                CanonicalArgument(
                    CanonicalFieldId("authToken"),
                    SecretReferenceValue("legacy.http_auth_token"),
                ),
            ),
        )

        val report = CanonicalSecurityAuditor.audit(tree, policy())

        assertTrue(report.clean)
        assertEquals(2, report.scannedNodes) // seq + send
        assertEquals(1, report.scannedSecrets)
    }

    @Test
    fun secretsOnObservationsAreRefused() {
        val tree = seq(
            ObserveNode(
                id = CanonicalNodeId("obs-1"),
                target = wifi,
                predicate = PredicateId("core.predicate.match_state"),
                arguments = CanonicalArguments(
                    listOf(
                        CanonicalArgument(
                            CanonicalFieldId("token"),
                            SecretReferenceValue("legacy.http_auth_token"),
                        ),
                    ),
                ),
            ),
        )

        val report = CanonicalSecurityAuditor.audit(tree, policy())

        val finding = report.findings.single()
        assertEquals(CanonicalSecurityAuditor.FindingCode.SECRET_IN_OBSERVATION, finding.code)
        assertEquals("obs-1", finding.nodeId)
    }

    // ------------------------------------------------------------------
    // URI schemes
    // ------------------------------------------------------------------

    @Test
    fun allowedUriSchemesPass() {
        val tree = seq(
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("https://api.example.com"))),
        )

        assertTrue(CanonicalSecurityAuditor.audit(tree, policy()).clean)
    }

    @Test
    fun forbiddenUriSchemesAreRefused() {
        val tree = seq(
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("file:///data/keys.txt"))),
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("javascript:alert(1)"))),
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("data:text/html,x"))),
        )

        val report = CanonicalSecurityAuditor.audit(tree, policy())

        assertEquals(3, report.findings.count {
            it.code == CanonicalSecurityAuditor.FindingCode.FORBIDDEN_URI_SCHEME
        })
    }

    // ------------------------------------------------------------------
    // Classification + idempotency
    // ------------------------------------------------------------------

    @Test
    fun unclassifiedOperationsFailClosed() {
        val tree = seq(httpSend())
        val emptyPolicy = CanonicalSecurityAuditor.SecurityPolicy(
            securityClasses = emptyMap(),
            requiredCapabilities = emptyMap(),
            idempotency = emptyMap(),
        )

        val report = CanonicalSecurityAuditor.audit(tree, emptyPolicy)

        assertEquals(
            CanonicalSecurityAuditor.FindingCode.UNCLASSIFIED_OPERATION,
            report.findings.single().code,
        )
    }

    @Test
    fun destructiveWithoutCapabilitiesIsRefused() {
        val destructiveOp = OperationId("core.operation.uninstall")
        val tree = seq(
            InvokeNode(
                id = CanonicalNodeId("uninstall-1"),
                target = TargetId("core.application.package"),
                operation = destructiveOp,
            ),
        )
        val report = CanonicalSecurityAuditor.audit(
            tree,
            CanonicalSecurityAuditor.SecurityPolicy(
                securityClasses = mapOf(destructiveOp to NodeSecurityClass.DESTRUCTIVE),
                requiredCapabilities = mapOf(destructiveOp to emptySet()),
                idempotency = mapOf(destructiveOp to CommandIdempotency.CONDITIONALLY_IDEMPOTENT),
            ),
        )

        assertEquals(
            CanonicalSecurityAuditor.FindingCode.DESTRUCTIVE_WITHOUT_CAPABILITIES,
            report.findings.single().code,
        )
    }

    @Test
    fun destructiveDeclaredIdempotentIsRefused() {
        val destructiveOp = OperationId("core.operation.uninstall")
        val tree = seq(
            InvokeNode(
                id = CanonicalNodeId("uninstall-1"),
                target = TargetId("core.application.package"),
                operation = destructiveOp,
            ),
        )
        val report = CanonicalSecurityAuditor.audit(
            tree,
            CanonicalSecurityAuditor.SecurityPolicy(
                securityClasses = mapOf(destructiveOp to NodeSecurityClass.DESTRUCTIVE),
                requiredCapabilities = mapOf(destructiveOp to setOf("core.capability.apps")),
                idempotency = mapOf(destructiveOp to CommandIdempotency.IDEMPOTENT),
            ),
        )

        assertTrue(
            report.findings.any {
                it.code == CanonicalSecurityAuditor.FindingCode.DESTRUCTIVE_DECLARED_IDEMPOTENT
            },
        )
    }

    @Test
    fun conditionallyIdempotentDestructivePasses() {
        val destructiveOp = OperationId("core.operation.uninstall")
        val tree = seq(
            InvokeNode(
                id = CanonicalNodeId("uninstall-1"),
                target = TargetId("core.application.package"),
                operation = destructiveOp,
            ),
        )
        val report = CanonicalSecurityAuditor.audit(
            tree,
            CanonicalSecurityAuditor.SecurityPolicy(
                securityClasses = mapOf(destructiveOp to NodeSecurityClass.DESTRUCTIVE),
                requiredCapabilities = mapOf(destructiveOp to setOf("core.capability.apps")),
                idempotency = mapOf(destructiveOp to CommandIdempotency.CONDITIONALLY_IDEMPOTENT),
            ),
        )

        assertTrue(report.clean)
    }

    // ------------------------------------------------------------------
    // Expressions on destructive payloads
    // ------------------------------------------------------------------

    @Test
    fun expressionPayloadOnDestructiveWriteIsRefused() {
        val report = CanonicalSecurityAuditor.audit(
            seq(
                SetValueNode(
                    id = CanonicalNodeId("v1"),
                    target = http,
                    value = ExpressionValue("device.storage - 1", CanonicalValueKind.INTEGER),
                ),
            ),
            CanonicalSecurityAuditor.SecurityPolicy(
                securityClasses = mapOf(
                    CanonicalWriteOperations.SET_VALUE to NodeSecurityClass.DESTRUCTIVE,
                ),
                requiredCapabilities = mapOf(
                    CanonicalWriteOperations.SET_VALUE to setOf("core.capability.storage"),
                ),
                idempotency = mapOf(
                    CanonicalWriteOperations.SET_VALUE to CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
                ),
            ),
        )

        assertEquals(
            CanonicalSecurityAuditor.FindingCode.EXPRESSION_IN_DESTRUCTIVE_PAYLOAD,
            report.findings.single().code,
        )
    }

    // ------------------------------------------------------------------
    // Determinism + structure
    // ------------------------------------------------------------------

    @Test
    fun auditIsDeterministicAndFindingsSortStably() {
        val tree = seq(
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("file:///x"))),
            httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("data:y"))),
        )

        assertEquals(
            CanonicalSecurityAuditor.audit(tree, policy()),
            CanonicalSecurityAuditor.audit(tree, policy()),
        )
        val findings = CanonicalSecurityAuditor.audit(tree, policy()).findings
        assertEquals(2, findings.size)
        // Sorted by detail: data: < file:.
        assertTrue(findings[0].detail <= findings[1].detail)
    }

    @Test
    fun nestedBranchesAreAuditedRecursively() {
        val tree = BranchNode(
            id = CanonicalNodeId("branch"),
            condition = ObservedConditionNode(
                id = CanonicalNodeId("obs"),
                observation = ObserveNode(
                    id = CanonicalNodeId("obs"),
                    target = wifi,
                    predicate = PredicateId("core.predicate.match_state"),
                ),
            ),
            ifTrue = seq(
                httpSend(CanonicalArgument(CanonicalFieldId("url"), UriValue("file:///etc/passwd"))),
            ),
        )

        val report = CanonicalSecurityAuditor.audit(tree, policy())

        assertEquals(1, report.findings.size)
        assertEquals("send-1", report.findings.single().nodeId)
    }
}
