package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionJournalTest {

    private val wifi = TargetId("core.connectivity.wifi")

    @Test
    fun errorCodeVocabularyCoversThePlanContract() {
        val expected = listOf(
            "INVALID_CONFIGURATION",
            "UNSUPPORTED",
            "PERMISSION_MISSING",
            "CAPABILITY_MISSING",
            "SECURITY_REJECTED",
            "TARGET_NOT_FOUND",
            "TIMEOUT",
            "TRANSIENT_FAILURE",
            "PROVIDER_UNAVAILABLE",
            "CANCELLED",
            "CONFLICT",
            "MIGRATION_FAILED",
        )
        assertEquals(expected, ExecutionErrorCode.entries.map { it.name })
    }

    @Test
    fun journalPhasesFollowTheLifecycleOrder() {
        assertEquals(
            listOf(
                "TRIGGER_EVALUATION",
                "SEMANTIC_VALIDATION",
                "CAPABILITY_RESOLUTION",
                "PLANNING",
                "COMMAND_EXECUTION",
                "FINAL_RESULT",
            ),
            ExecutionPhase.entries.map { it.name },
        )
    }

    @Test
    fun runRecordComputesDuration() {
        val record = ExecutionRunRecord(
            runId = "run-1",
            workflowNodeCount = 3,
            startedAtMs = 1_000L,
            completedAtMs = 1_750L,
        )

        assertEquals(750L, record.durationMs)
    }

    @Test
    fun completedBeforeStartIsRejected() {
        try {
            ExecutionRunRecord(
                runId = "run-1",
                workflowNodeCount = 1,
                startedAtMs = 2_000L,
                completedAtMs = 1_000L,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun validationBlockedRunExplainsWhyItDidNotRun() {
        val schema = NodeSchema(
            schemaId = "core.schema.wifi.set_state",
            kind = NodeSchemaKind.ACTION,
            target = wifi,
            operation = OperationId("core.operation.set_state"),
            title = "Wi-Fi",
            summaryTemplate = "Wi-Fi {enabled}",
            fields = listOf(
                NodeSchemaField(
                    id = CanonicalFieldId("enabled"),
                    type = NodeFieldType.BOOLEAN,
                    alwaysRequired = true,
                ),
            ),
        )
        val ast = CanonicalWorkflowAst(
            root = SetStateNode(CanonicalNodeId("w1"), wifi, BooleanValue(true)),
        )
        val contract = CanonicalWorkflowContract(
            schema = schema,
            semantics = NodeSelectionSemantics(
                targetSelectionMode = TargetSelectionMode.SINGLE,
                executionMode = ExecutionMode.SINGLE,
            ),
            capabilityRequirement = com.nexaflow.domain.capability.CapabilityRequirement.None,
        )
        val verdict = validate(ast, contract, emptyList())

        val record = validationBlockedRun("run-1", verdict, startedAtMs = 42L)

        assertEquals(ExecutionPhaseStatus.SKIPPED, record.finalStatus)
        assertEquals(ExecutionErrorCode.INVALID_CONFIGURATION, record.finalErrorCode)
        assertEquals("required field enabled is missing", record.skipReason!!.message)
        assertNull(record.completedAtMs)
    }

    @Test
    fun capabilityBlockedRunListsProviderExclusions() {
        val resolution = CanonicalCapabilityResolver(
            listOf(
                OperationCapabilityRequirements(
                    operation = OperationId("core.operation.set_state"),
                    providers = listOf(
                        ProviderDescriptor(
                            providerId = "core.provider.shizuku",
                            strategy = com.nexaflow.domain.capability.operation.StrategyId.SHIZUKU_USER_SERVICE,
                            capabilities = setOf(
                                ProviderCapability(
                                    com.nexaflow.domain.capability.CapabilityBackendId.SHIZUKU,
                                    com.nexaflow.domain.capability.PrivilegeLevel.SHIZUKU,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).resolve(
            OperationId("core.operation.set_state"),
            CapabilitySelectionPolicy(),
            CapabilityGraphSnapshot(
                backendAvailability = mapOf(
                    com.nexaflow.domain.capability.CapabilityBackendId.SHIZUKU to
                        com.nexaflow.domain.capability.CapabilityAvailability.AVAILABLE,
                ),
            ),
        )

        val record = capabilityBlockedRun("run-2", resolution, startedAtMs = 7L)

        assertEquals(ExecutionPhaseStatus.SKIPPED, record.finalStatus)
        assertEquals(ExecutionErrorCode.PERMISSION_MISSING, record.skipReason!!.errorCode)
        assertTrue(record.capabilityExclusions.isNotEmpty())
        assertTrue(
            record.capabilityExclusions.any {
                it.errorCode == CapabilityResolutionError.PRIVILEGE_NOT_GRANTED
            },
        )
    }

    @Test
    fun journalRejectsSecretLookingMetadataKeys() {
        try {
            ExecutionRunRecord(
                runId = "run-1",
                workflowNodeCount = 1,
                startedAtMs = 0L,
                commandRecords = listOf(
                    CommandRecord(
                        commandId = "c1",
                        target = wifi,
                        operation = OperationId("core.operation.set_state"),
                        status = ExecutionPhaseStatus.PASSED,
                        metadata = mapOf("api_token" to "value"),
                    ),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun journalRejectsSecretLookingMetadataValues() {
        try {
            ExecutionRunRecord(
                runId = "run-1",
                workflowNodeCount = 1,
                startedAtMs = 0L,
                commandRecords = listOf(
                    CommandRecord(
                        commandId = "c1",
                        target = wifi,
                        operation = OperationId("core.operation.set_state"),
                        status = ExecutionPhaseStatus.PASSED,
                        metadata = mapOf("authorization" to "Bearer abc123"),
                    ),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun journalRejectsSecretsInsideValidationMessages() {
        try {
            ExecutionRunRecord(
                runId = "run-1",
                workflowNodeCount = 1,
                startedAtMs = 0L,
                validationFailures = listOf(
                    ValidationFinding(
                        stage = ValidationStage.SECURITY,
                        rule = "leak",
                        message = "request failed with Authorization: Bearer abc123",
                    ),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun cleanMetadataIsAccepted() {
        val record = ExecutionRunRecord(
            runId = "run-1",
            workflowNodeCount = 1,
            startedAtMs = 0L,
            commandRecords = listOf(
                CommandRecord(
                    commandId = "c1",
                    target = wifi,
                    operation = OperationId("core.operation.set_state"),
                    status = ExecutionPhaseStatus.PASSED,
                    providerLabel = "ANDROID_API",
                    durationMs = 120,
                    metadata = mapOf("attempt" to "1", "verified" to "true"),
                ),
            ),
            finalStatus = ExecutionPhaseStatus.PASSED,
        )

        assertEquals(ExecutionPhaseStatus.PASSED, record.finalStatus)
    }

    @Test
    fun journalConstructionIsDeterministic() {
        val first = ExecutionRunRecord(
            runId = "run-1",
            workflowNodeCount = 2,
            startedAtMs = 10L,
            completedAtMs = 30L,
            triggerEvaluation = TriggerEvaluation(
                observedPredicates = listOf(PredicateId("core.predicate.match_state")),
                monitoredTargets = listOf(wifi),
                conditionOutcomes = mapOf("connected" to true, "vpn" to false),
            ),
            finalStatus = ExecutionPhaseStatus.PASSED,
        )
        val second = ExecutionRunRecord(
            runId = "run-1",
            workflowNodeCount = 2,
            startedAtMs = 10L,
            completedAtMs = 30L,
            triggerEvaluation = TriggerEvaluation(
                observedPredicates = listOf(PredicateId("core.predicate.match_state")),
                monitoredTargets = listOf(wifi),
                conditionOutcomes = mapOf("connected" to true, "vpn" to false),
            ),
            finalStatus = ExecutionPhaseStatus.PASSED,
        )

        assertEquals(first, second)
    }
}
