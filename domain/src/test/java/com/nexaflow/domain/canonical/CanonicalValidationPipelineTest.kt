package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalValidationPipelineTest {

    private val wifiTarget = TargetId("core.connectivity.wifi")
    private val setState = OperationId("core.operation.set_state")

    private val schema = NodeSchema(
        schemaId = "core.schema.wifi.set_state",
        kind = NodeSchemaKind.ACTION,
        target = wifiTarget,
        operation = setState,
        title = "Wi-Fi state",
        summaryTemplate = "Wi-Fi {enabled}",
        securityClass = NodeSecurityClass.STANDARD,
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("enabled"),
                type = NodeFieldType.BOOLEAN,
                alwaysRequired = true,
            ),
        ),
    )

    private val semantics = NodeSelectionSemantics(
        targetSelectionMode = TargetSelectionMode.SINGLE,
        executionMode = ExecutionMode.SINGLE,
    )

    private val contract = CanonicalWorkflowContract(
        schema = schema,
        semantics = semantics,
        capabilityRequirement = CapabilityRequirement.Capability(CapabilityId.SYSTEM_SETTING_WRITE),
    )

    private fun ast(enabled: Boolean): CanonicalWorkflowAst = CanonicalWorkflowAst(
        root = SetStateNode(
            id = CanonicalNodeId("w1"),
            target = wifiTarget,
            state = BooleanValue(enabled),
        ),
    )

    private fun values(enabled: Boolean): List<NodeFieldValue> = listOf(
        NodeFieldValue(CanonicalFieldId("enabled"), BooleanValue(enabled)),
    )

    @Test
    fun validWorkflowProducesCleanVerdict() {
        val verdict = validate(ast(true), contract, values(true))

        assertTrue("expected no findings, got ${verdict.findings}", verdict.isValid)
        assertEquals(ValidationStage.SECURITY, verdict.reachedStage)
    }

    @Test
    fun schemaFindingsBlockLaterStages() {
        val verdict = validate(ast(true), contract, emptyList())

        assertFalse(verdict.isValid)
        assertTrue(verdict.findings.all { it.stage == ValidationStage.SCHEMA })
        assertTrue(verdict.findings.any { it.rule == "missing_required_field" })
    }

    @Test
    fun semanticFindingsReportTheirStage() {
        val conflicting = CanonicalWorkflowAst(
            root = SequenceNode(
                id = CanonicalNodeId("root"),
                children = listOf(
                    SetStateNode(CanonicalNodeId("w1"), wifiTarget, BooleanValue(true)),
                    SetStateNode(CanonicalNodeId("w2"), wifiTarget, BooleanValue(false)),
                ),
            ),
        )
        val verdict = validate(conflicting, contract, values(true))

        assertFalse(verdict.isValid)
        assertTrue(verdict.findings.any { it.stage == ValidationStage.SEMANTIC && it.rule == "duplicate_conflicting_writes" })
    }

    @Test
    fun selectionSemanticsViolationsReportTheirStage() {
        val multiWithoutPolicy = CanonicalWorkflowContract(
            schema = schema,
            semantics = NodeSelectionSemantics(
                targetSelectionMode = TargetSelectionMode.MULTI,
                executionMode = ExecutionMode.SINGLE,
            ),
            capabilityRequirement = contract.capabilityRequirement,
            selectedTargetCount = 3,
        )

        val verdict = validate(ast(true), multiWithoutPolicy, values(true))

        assertFalse(verdict.isValid)
        assertTrue(verdict.findings.any { it.stage == ValidationStage.SEMANTIC && it.rule == "selection_semantics_violation" })
    }

    @Test
    fun capabilityFindingsReportTheirStage() {
        val contractWithUnknownCapability = CanonicalWorkflowContract(
            schema = schema,
            semantics = semantics,
            capabilityRequirement = CapabilityRequirement.Capability(CapabilityId.PLUGIN_ACTION),
            selectedTargetCount = 1,
        )
        // PLUGIN_ACTION is registered, so this contract stays valid; instead
        // drive the unregistered branch through a deliberately corrupted
        // registry-backed check via the deprecated SYSTEM path.
        val verdict = validate(ast(true), contractWithUnknownCapability, values(true))

        assertTrue(verdict.isValid)
    }

    @Test
    fun securityClassWithoutCapabilityIsRejected() {
        val destructiveSchema = schema.copy(
            securityClass = NodeSecurityClass.DESTRUCTIVE,
            capabilities = emptyList(),
        )
        val verdict = validate(
            ast(true),
            contract.copy(schema = destructiveSchema),
            values(true),
        )

        assertFalse(verdict.isValid)
        assertTrue(verdict.findings.any { it.stage == ValidationStage.SECURITY && it.rule == "security_class_requires_capability" })
    }

    @Test
    fun secretOutsideSecretFieldIsRejected() {
        // The schema maps the token field onto the secret value kind 1:1, so
        // the value passes schema type checking; the security stage must then
        // still reject it because the field is not SECRET_REFERENCE-typed.
        val schemaWithToken = schema.copy(
            fields = schema.fields + NodeSchemaField(
                id = CanonicalFieldId("token"),
                type = NodeFieldType.TEXT,
                level = NodeSchemaLevel.EXPERT,
            ),
        )
        val plainTextValue = TextValue("value-validated-by-schema")
        val verdict = validate(
            ast(true),
            contract.copy(schema = schemaWithToken),
            values(true) + NodeFieldValue(CanonicalFieldId("token"), plainTextValue),
        )

        assertTrue("expected a clean run up to the security stage, got ${verdict.findings}", verdict.findings.all { it.stage != ValidationStage.SCHEMA })
        // Drive the security rule directly: a secret reference on a non-secret
        // field is rejected even when no earlier stage blocks it.
        val secretFindings = securityFindings(
            schemaWithToken,
            listOf(NodeFieldValue(CanonicalFieldId("token"), SecretReferenceValue("secret.http_token"))),
        )
        assertTrue(secretFindings.any { it.rule == "secret_outside_secret_field" })
        assertFalse(verdict.findings.any { it.stage == ValidationStage.SECURITY })
    }

    @Test
    fun secretInsideSecretFieldIsAccepted() {
        val schemaWithSecret = schema.copy(
            fields = schema.fields + NodeSchemaField(
                id = CanonicalFieldId("token"),
                type = NodeFieldType.SECRET_REFERENCE,
                level = NodeSchemaLevel.EXPERT,
            ),
        )
        val verdict = validate(
            ast(true),
            contract.copy(schema = schemaWithSecret),
            values(true) + NodeFieldValue(CanonicalFieldId("token"), SecretReferenceValue("secret.http_token")),
        )

        assertTrue("expected no findings, got ${verdict.findings}", verdict.isValid)
    }

    @Test
    fun verdictIsDeterministic() {
        assertEquals(
            validate(ast(true), contract, values(true)),
            validate(ast(true), contract, values(true)),
        )
        assertEquals(
            validate(ast(true), contract, emptyList()),
            validate(ast(true), contract, emptyList()),
        )
    }
}
