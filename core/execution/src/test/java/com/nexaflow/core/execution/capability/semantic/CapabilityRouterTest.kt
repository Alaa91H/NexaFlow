package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests pinning the router contract: least privilege, policy gating,
 * transport-only fallback, UNKNOWN reconciliation, evidence and health wiring.
 */
class CapabilityRouterTest {

    private val fingerprint = DeviceFingerprint(
        manufacturer = "Google",
        model = "Test Device",
        device = "shiba",
        androidApi = 35,
        securityPatch = "2026-01-01",
        romFamily = RomFamily.STOCK_GOOGLE
    )

    private class FakeStrategy(
        override val id: StrategyId,
        override val supportedOperations: Set<SemanticOperationId>,
        private val available: Boolean = true,
        private val outcome: OperationOutcome? = null,
        private val readValue: Boolean? = null,
        private val failTimes: Int = 0
    ) : CapabilityStrategy {
        var executions = 0
            private set

        override suspend fun availability(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): StrategyAvailability =
            if (available) StrategyAvailability(true)
            else StrategyAvailability(false, "unavailable in test")

        override suspend fun execute(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): OperationOutcome {
            executions++
            return outcome ?: OperationOutcome(
                operation = operation,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = id,
                message = "ok",
                metadata = mapOf("requestedEnabled" to (request.parameters["enabled"] ?: "true"))
            )
        }

        override suspend fun readState(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): Boolean? = readValue
    }

    private fun registry() = OperationRegistry.default()

    private fun router(
        vararg strategies: CapabilityStrategy,
        evidence: CapabilityEvidenceStore = CapabilityEvidenceStore(),
        health: StrategyHealthTracker = StrategyHealthTracker()
    ) = CapabilityRouter(
        registry = registry(),
        strategies = strategies.toList(),
        evidenceStore = evidence,
        healthTracker = health,
        fingerprint = fingerprint
    )

    private fun request(op: SemanticOperationId, privileged: Boolean = false) =
        TypedOperationRequest(
            operation = op,
            parameters = mapOf("enabled" to "true"),
            allowPrivilegedStrategies = privileged
        )

    @Test
    fun prefersLeastPrivilegedAvailableStrategy() = runTest {
        val androidApi = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            readValue = true
        )
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE))
        val outcome = router(androidApi, root).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(StrategyId.ANDROID_PUBLIC_API, outcome.strategy)
        assertEquals(1, androidApi.executions)
        assertEquals(0, root.executions)
    }

    @Test
    fun privilegedStrategiesAreBlockedWithoutExplicitOptIn() = runTest {
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE))
        val outcome = router(root).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = false))
        assertEquals(OperationOutcomeStatus.PENDING_USER_ACTION, outcome.status)
        assertEquals(0, root.executions)
    }

    @Test
    fun transportFailureAdvancesToNextCandidate() = runTest {
        val androidApi = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome.failed(
                SemanticOperationId.WIFI_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                "public toggle rejected",
                strategy = StrategyId.ANDROID_PUBLIC_API,
                transportFailure = true
            ),
            readValue = true
        )
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE), readValue = true)
        val outcome = router(androidApi, root).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(StrategyId.ROOT_SHELL, outcome.strategy)
        assertEquals(1, androidApi.executions)
        assertEquals(1, root.executions)
    }

    @Test
    fun postconditionFailureDoesNotBlindlyFallbackForIdempotentSafeSet() = runTest {
        // WIFI_SET_STATE is idempotent+safe, so a definite failure (not
        // transport) MAY advance — pin that it advanced and succeeded.
        val androidApi = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome.failed(
                SemanticOperationId.WIFI_SET_STATE,
                com.nexaflow.domain.capability.CapabilityErrorCode.PERMISSION_DENIED,
                "write rejected",
                strategy = StrategyId.ANDROID_PUBLIC_API
            )
        )
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE))
        val outcome = router(androidApi, root).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(StrategyId.ROOT_SHELL, outcome.strategy)
    }

    @Test
    fun unknownOutcomeIsReconciledThroughReadBack() = runTest {
        val root = FakeStrategy(
            StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.UNKNOWN,
                strategy = StrategyId.ROOT_SHELL,
                message = "root timed out after side effect"
            ),
            readValue = true
        )
        val evidence = CapabilityEvidenceStore()
        val outcome = router(root, evidence = evidence)
            .execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertTrue(outcome.verification?.verified == true)
        assertEquals(1, root.executions) // exactly one execution, no blind retry
    }

    @Test
    fun unknownOutcomeWithMismatchedReadFailsHonestly() = runTest {
        val root = FakeStrategy(
            StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.UNKNOWN,
                strategy = StrategyId.ROOT_SHELL,
                message = "root timed out"
            ),
            readValue = false // requested ON, observed OFF
        )
        val outcome = router(root).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(1, root.executions)
    }

    @Test
    fun evidenceIsRecordedForVerifiedSuccess() = runTest {
        val androidApi = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = StrategyId.ANDROID_PUBLIC_API,
                verification = com.nexaflow.domain.capability.VerificationResult(true, true, "verified"),
                message = "ok",
                metadata = mapOf("requestedEnabled" to "true")
            ),
            readValue = true
        )
        val evidence = CapabilityEvidenceStore()
        router(androidApi, evidence = evidence).execute(request(SemanticOperationId.WIFI_SET_STATE))
        val record = evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey
        )
        assertEquals(1L, record.verifiedSuccesses)
        assertNotNull(record.lastVerifiedSuccessAtMs)
    }

    @Test
    fun coolingStrategyIsDeprioritizedAgainstHealthyPeer() = runTest {
        val health = StrategyHealthTracker()
        val androidApi = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            readValue = true
        )
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE), readValue = true)
        // Cool the public-API strategy down for this device.
        health.recordFailure(StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey)
        health.recordFailure(StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey)
        assertTrue(health.isCoolingDown(StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey))
        val router = CapabilityRouter(
            registry = registry(),
            strategies = listOf(androidApi, root),
            evidenceStore = CapabilityEvidenceStore(),
            healthTracker = health,
            fingerprint = fingerprint
        )
        val outcome = router.execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        // Degraded confidence pushes the healthy root candidate ahead.
        assertEquals(StrategyId.ROOT_SHELL, outcome.strategy)
    }

    @Test
    fun unregisteredOperationIsUnsupported() = runTest {
        val strategy = FakeStrategy(StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE))
        val outcome = router(strategy).execute(
            TypedOperationRequest(operation = SemanticOperationId.BLUETOOTH_SET_STATE, parameters = mapOf("enabled" to "true"))
        )
        assertEquals(OperationOutcomeStatus.UNSUPPORTED, outcome.status)
    }

    @Test
    fun missingRequiredParameterIsRejectedBeforeExecution() = runTest {
        val strategy = FakeStrategy(StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE))
        val outcome = router(strategy).execute(
            TypedOperationRequest(operation = SemanticOperationId.WIFI_SET_STATE, parameters = emptyMap())
        )
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            outcome.errorCode
        )
        assertEquals(0, strategy.executions)
    }

    @Test
    fun pendingPermissionOutcomeIsNotReportedAsSuccessfulLegacyExecution() = runTest {
        val root = FakeStrategy(
            StrategyId.ROOT_SHELL,
            setOf(SemanticOperationId.WIFI_SET_STATE)
        )
        val semantic = SemanticActionRouter(
            router = router(root),
            privilegedPolicyEnabled = { false }
        )
        val result = semantic.routeIfSupported(
            Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true")),
            workflowId = "wf",
            executionId = "run"
        )
        assertNotNull(result)
        assertEquals(false, result!!.success)
        assertEquals(0, root.executions)
    }

    @Test
    fun brightnessAndTimeoutMappingDoesNotRequireBooleanEnabledFlag() {
        val brightness = SemanticActionMapper.requestFor(
            Action(
                ActionType.SYSTEM_BRIGHTNESS,
                mapOf("value" to "120", "configVersion" to "2")
            ),
            workflowId = null,
            executionId = null,
            allowPrivilegedStrategies = true
        )
        assertNotNull(brightness)
        assertEquals(mapOf("value" to "120"), brightness!!.parameters)

        val timeout = SemanticActionMapper.requestFor(
            Action(
                ActionType.SYSTEM_SCREEN_TIMEOUT,
                mapOf("seconds" to "30", "configVersion" to "2")
            ),
            workflowId = null,
            executionId = null,
            allowPrivilegedStrategies = true
        )
        assertNotNull(timeout)
        assertEquals(mapOf("seconds" to "30"), timeout!!.parameters)
    }

    @Test
    fun counterpartPairsReadAndWriteOperations() {
        assertEquals(
            SemanticOperationId.WIFI_SET_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.WIFI_GET_STATE)
        )
        assertEquals(
            SemanticOperationId.WIFI_GET_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.WIFI_SET_STATE)
        )
    }

    // ---- NF-P0-002: evidence/health only after verification -----------------

    @Test
    fun transportSuccessWithoutReadBackIsUnknownAndNeverScoresPositiveEvidence() = runTest {
        // REQUIRED verification with a strategy that has NO read-back: the
        // strict contract reclassifies the transport success as UNKNOWN, and
        // the evidence store must NOT record a success for it.
        val strategy = FakeStrategy(StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE), readValue = null)
        val evidence = CapabilityEvidenceStore()
        val outcome = router(strategy, evidence = evidence)
            .execute(request(SemanticOperationId.WIFI_SET_STATE))
        assertEquals(OperationOutcomeStatus.UNKNOWN, outcome.status)
        assertEquals(0L, evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey
        ).verifiedSuccesses)
        assertEquals(0L, evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey
        ).unverifiedSuccesses)
    }

    @Test
    fun strictVerificationFailsWhenReadBackContradictsTheRequest() = runTest {
        val strategy = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            readValue = false // requested ON, observed OFF
        )
        val outcome = router(strategy).execute(request(SemanticOperationId.WIFI_SET_STATE))
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.VERIFICATION_FAILED,
            outcome.errorCode
        )
    }

    @Test
    fun strictVerificationSucceedsWhenReadBackMatches() = runTest {
        val strategy = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            readValue = true // requested ON, observed ON
        )
        val outcome = router(strategy).execute(request(SemanticOperationId.WIFI_SET_STATE))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertTrue(outcome.verification?.verified == true)
    }

    @Test
    fun unknownReconciliationWithMatchScoresVerifiedEvidence() = runTest {
        val strategy = FakeStrategy(
            StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.UNKNOWN,
                strategy = StrategyId.ROOT_SHELL,
                message = "transport dropped after side effect"
            ),
            readValue = true
        )
        val evidence = CapabilityEvidenceStore()
        val health = StrategyHealthTracker()
        val outcome = CapabilityRouter(
            registry = registry(),
            strategies = listOf(strategy),
            evidenceStore = evidence,
            healthTracker = health,
            fingerprint = fingerprint
        ).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        // Reconciliation IS the verification pass: verified evidence + health.
        assertEquals(1L, evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, fingerprint.deviceKey
        ).verifiedSuccesses)
        assertEquals(
            StrategyHealthTracker.State.HEALTHY,
            health.healthFor(StrategyId.ROOT_SHELL, fingerprint.deviceKey).state
        )
    }

    @Test
    fun unknownReconciliationWithoutMatchScoresFailure() = runTest {
        val strategy = FakeStrategy(
            StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.UNKNOWN,
                strategy = StrategyId.ROOT_SHELL,
                message = "transport dropped after side effect"
            ),
            readValue = false
        )
        val evidence = CapabilityEvidenceStore()
        val health = StrategyHealthTracker()
        val outcome = CapabilityRouter(
            registry = registry(),
            strategies = listOf(strategy),
            evidenceStore = evidence,
            healthTracker = health,
            fingerprint = fingerprint
        ).execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(1L, evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ROOT_SHELL, fingerprint.deviceKey
        ).failures)
        assertEquals(
            StrategyHealthTracker.State.FAILED,
            health.healthFor(StrategyId.ROOT_SHELL, fingerprint.deviceKey).state
        )
    }

    @Test
    fun failedVerificationScoresFailureEvidenceNotSuccess() = runTest {
        val strategy = FakeStrategy(
            StrategyId.ANDROID_PUBLIC_API, setOf(SemanticOperationId.WIFI_SET_STATE),
            readValue = false // contradicted
        )
        val evidence = CapabilityEvidenceStore()
        router(strategy, evidence = evidence).execute(request(SemanticOperationId.WIFI_SET_STATE))
        val record = evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.ANDROID_PUBLIC_API, fingerprint.deviceKey
        )
        assertEquals("A contradicted success must score as failure", 1L, record.failures)
        assertEquals(0L, record.verifiedSuccesses)
        assertEquals(0L, record.unverifiedSuccesses)
    }

    @Test
    fun pendingUserActionIsTerminalAndScoresNoFailure() = runTest {
        // The settings fallback reports PENDING_USER_ACTION: nothing failed,
        // so neither evidence nor health may record a failure, and the router
        // must not advance to a privileged candidate afterwards.
        val settings = FakeStrategy(
            StrategyId.SETTINGS_USER_ACTION, setOf(SemanticOperationId.WIFI_SET_STATE),
            outcome = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = OperationOutcomeStatus.PENDING_USER_ACTION,
                strategy = StrategyId.SETTINGS_USER_ACTION,
                message = "user action required"
            )
        )
        val root = FakeStrategy(StrategyId.ROOT_SHELL, setOf(SemanticOperationId.WIFI_SET_STATE))
        val evidence = CapabilityEvidenceStore()
        val outcome = router(settings, root, evidence = evidence)
            .execute(request(SemanticOperationId.WIFI_SET_STATE, privileged = true))
        assertEquals(OperationOutcomeStatus.PENDING_USER_ACTION, outcome.status)
        assertEquals("PENDING_USER_ACTION must not open the privileged route", 0, root.executions)
        val record = evidence.evidenceFor(
            SemanticOperationId.WIFI_SET_STATE, StrategyId.SETTINGS_USER_ACTION, fingerprint.deviceKey
        )
        assertEquals(0L, record.failures)
        assertEquals(0L, record.verifiedSuccesses)
    }
}
