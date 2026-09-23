package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.domain.capability.VerificationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end migration contract for package operations (force-stop /
 * enable-disable / clear-data through the semantic layer): the real typed
 * strategies run under a real [CapabilityRouter], so the tests pin the whole
 * chain — typed dispatch, honest verification verdicts, reconciliation of
 * uncertain dispatches, and evidence scoring — not just isolated strategy
 * behavior.
 */
class PackageSemanticMigrationTest {

    /**
     * Distinguishes write dispatches from read-back calls and lets each
     * return a scripted result, so tests can shape "write succeeded but the
     * read-back disagrees" scenarios precisely.
     */
    private class ScriptedTransport(
        var writeResult: SystemControlResult = SystemControlResult.ok("Operation executed"),
        var readResult: SystemControlResult = SystemControlResult.ok("")
    ) {
        var writeCount = 0
            private set
        var readCount = 0
            private set
        /** The argv of the last WRITE dispatch (read-backs are not writes). */
        var lastWriteArgv: List<String>? = null
            private set
        var lastOperation: PrivilegedOperation? = null
            private set

        fun run(operation: PrivilegedOperation): SystemControlResult {
            lastOperation = operation
            val isRead = operation is PrivilegedOperation.ReadSettingState ||
                operation is PrivilegedOperation.ReadPackageEnabledState
            return if (isRead) {
                readCount++
                readResult
            } else {
                writeCount++
                lastWriteArgv = operation.argv()
                writeResult
            }
        }
    }

    private val fingerprint = DeviceFingerprint(
        manufacturer = "Test",
        model = "Migration Harness",
        device = "harness",
        androidApi = 35,
        securityPatch = "2026-01-01",
        romFamily = RomFamily.STOCK_GOOGLE
    )

    private fun shizukuRouter(
        transport: ScriptedTransport,
        evidence: CapabilityEvidenceStore = CapabilityEvidenceStore(),
        health: StrategyHealthTracker = StrategyHealthTracker()
    ): CapabilityRouter = CapabilityRouter(
        registry = OperationRegistry.default(),
        strategies = listOf(
            com.nexaflow.core.execution.capability.semantic.strategies.ShizukuTypedStrategy(
                shizukuGranted = { true },
                userServiceReady = { true },
                execute = transport::run
            )
        ),
        evidenceStore = evidence,
        healthTracker = health,
        fingerprint = fingerprint
    )

    private fun rootRouter(
        transport: ScriptedTransport,
        evidence: CapabilityEvidenceStore = CapabilityEvidenceStore(),
        health: StrategyHealthTracker = StrategyHealthTracker()
    ): CapabilityRouter = CapabilityRouter(
        registry = OperationRegistry.default(),
        strategies = listOf(
            com.nexaflow.core.execution.capability.semantic.strategies.RootTypedStrategy(
                rootAvailable = { true },
                execute = transport::run
            )
        ),
        evidenceStore = evidence,
        healthTracker = health,
        fingerprint = fingerprint
    )

    private fun request(
        operation: SemanticOperationId,
        pkg: String = "com.example.app",
        enabled: Boolean? = null
    ) = TypedOperationRequest(
        operation = operation,
        parameters = buildMap {
            put("packageName", pkg)
            enabled?.let { put("enabled", it.toString()) }
        },
        allowPrivilegedStrategies = true
    )

    // ---- registry honesty ---------------------------------------------------

    @Test
    fun oneShotPackageTransitionsDeclareBestEffortVerification() {
        // Force-stop and clear-data have no reliable observable post-condition
        // (a killed process may be restarted instantly by a sync job; the
        // enabled-state probe says nothing about cleared data). REQUIRED would
        // fabricate verdicts, so the registry must declare BEST_EFFORT.
        val forceStop = OperationRegistry.default()
            .requiresOperation(SemanticOperationId.PACKAGE_FORCE_STOP)
        val clearData = OperationRegistry.default()
            .requiresOperation(SemanticOperationId.PACKAGE_CLEAR_DATA)
        val setEnabled = OperationRegistry.default()
            .requiresOperation(SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        assertEquals(VerificationMode.BEST_EFFORT, forceStop.verificationMode)
        assertEquals(VerificationMode.BEST_EFFORT, clearData.verificationMode)
        // Enable/disable HAS a comparable post-condition (the enabled-state
        // read) and keeps strict REQUIRED verification.
        assertEquals(VerificationMode.REQUIRED, setEnabled.verificationMode)
    }

    // ---- Shizuku route ------------------------------------------------------

    @Test
    fun shizukuForceStopSucceedsWithoutFabricatedVerification() = runTest {
        val transport = ScriptedTransport()
        val outcome = shizukuRouter(transport).execute(request(SemanticOperationId.PACKAGE_FORCE_STOP))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(
            listOf("am", "force-stop", "com.example.app"),
            transport.lastWriteArgv
        )
        assertEquals("exactly one write dispatch", 1, transport.writeCount)
        // BEST_EFFORT: the transport success stays honest-but-unverified —
        // the router must not invent a verified verdict for it.
        val forceStopVerification = outcome.verification
        assertTrue(forceStopVerification == null || !forceStopVerification.verified)
    }

    @Test
    fun shizukuClearDataSucceedsWithoutFabricatedVerification() = runTest {
        val transport = ScriptedTransport()
        val outcome = shizukuRouter(transport).execute(request(SemanticOperationId.PACKAGE_CLEAR_DATA))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(
            listOf("pm", "clear", "com.example.app"),
            transport.lastWriteArgv
        )
        assertEquals(1, transport.writeCount)
        val clearDataVerification = outcome.verification
        assertTrue(clearDataVerification == null || !clearDataVerification.verified)
    }

    @Test
    fun shizukuEnableIsVerifiedAgainstActualEnabledState() = runTest {
        // Write succeeds, read-back returns an empty disabled-list → the
        // package is enabled → matches the request → verified SUCCESS.
        val transport = ScriptedTransport(readResult = SystemControlResult.ok(""))
        val outcome = shizukuRouter(transport)
            .execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(listOf("pm", "enable", "com.example.app"), transport.lastWriteArgv)
        assertEquals(true, outcome.verification?.verified)
    }

    @Test
    fun shizukuDisableContradictedByReadFailsWithVerificationFailed() = runTest {
        // The disable dispatch succeeds, but the read-back says the package
        // is STILL enabled → strict REQUIRED must fail the verdict honestly.
        val transport = ScriptedTransport(readResult = SystemControlResult.ok(""))
        val outcome = shizukuRouter(transport)
            .execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = false))
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.VERIFICATION_FAILED,
            outcome.errorCode
        )
    }

    @Test
    fun shizukuUncertainForceStopIsUnknownWithoutBlindRetry() = runTest {
        // The dispatch drops after a possible side effect (binder death): the
        // outcome is UNKNOWN, never a blind second dispatch.
        val transport = ScriptedTransport(
            writeResult = SystemControlResult.fail("Shizuku UserService failed: binder died")
        )
        val outcome = shizukuRouter(transport).execute(request(SemanticOperationId.PACKAGE_FORCE_STOP))
        assertEquals(OperationOutcomeStatus.UNKNOWN, outcome.status)
        assertFalse(outcome.transportFailure)
        assertEquals("exactly one dispatch — no blind retry", 1, transport.writeCount)
    }

    // ---- Root route ---------------------------------------------------------

    @Test
    fun rootDisableWithMatchingReadBackIsVerified() = runTest {
        // Dispatch pm disable-user, read-back prints the disabled line →
        // matches the request → verified SUCCESS.
        val transport = ScriptedTransport(
            readResult = SystemControlResult.ok("package:com.example.app")
        )
        val outcome = rootRouter(transport)
            .execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = false))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(
            listOf("pm", "disable-user", "--user", "0", "com.example.app"),
            transport.lastWriteArgv
        )
        assertEquals(true, outcome.verification?.verified)
    }

    @Test
    fun rootEnableContradictedByDisabledReadFails() = runTest {
        // The enable dispatch "succeeds", but the read-back says the package
        // IS disabled while the request asked to ENABLE → strict REQUIRED
        // fails it instead of trusting the transport.
        val transport = ScriptedTransport(
            readResult = SystemControlResult.ok("package:com.example.app")
        )
        val outcome = rootRouter(transport)
            .execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = true))
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.VERIFICATION_FAILED,
            outcome.errorCode
        )
    }

    @Test
    fun rootVerifiedEnableScoresVerifiedEvidenceAndHealth() = runTest {
        val transport = ScriptedTransport(readResult = SystemControlResult.ok(""))
        val evidence = CapabilityEvidenceStore()
        val health = StrategyHealthTracker()
        val outcome = rootRouter(transport, evidence, health)
            .execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = true))
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(true, outcome.verification?.verified)
        val record = evidence.evidenceFor(
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
            StrategyId.ROOT_SHELL,
            fingerprint.deviceKey
        )
        assertEquals("A verified dispatch must score verified evidence", 1L, record.verifiedSuccesses)
        assertEquals(
            StrategyHealthTracker.State.HEALTHY,
            health.healthFor(StrategyId.ROOT_SHELL, fingerprint.deviceKey).state
        )
    }

    @Test
    fun rootUnclearDispatchIsUnknownAndNeverRetried() = runTest {
        val transport = ScriptedTransport(
            writeResult = SystemControlResult.fail("su: command timed out")
        )
        val outcome = rootRouter(transport).execute(request(SemanticOperationId.PACKAGE_CLEAR_DATA))
        // A timed-out clear-data may have landed: UNKNOWN, no transport-fail
        // fallback, and exactly one dispatch.
        assertEquals(OperationOutcomeStatus.UNKNOWN, outcome.status)
        assertFalse(outcome.transportFailure)
        assertEquals(1, transport.writeCount)
    }

    // ---- validation gate ----------------------------------------------------

    @Test
    fun invalidPackageNeverReachesEitherTransport() = runTest {
        val shizukuTransport = ScriptedTransport()
        val shizukuOutcome = shizukuRouter(shizukuTransport).execute(
            request(SemanticOperationId.PACKAGE_FORCE_STOP, pkg = "com; rm -rf /")
        )
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            shizukuOutcome.errorCode
        )
        assertNull("No dispatch may happen for an invalid package", shizukuTransport.lastWriteArgv)

        val rootTransport = ScriptedTransport()
        val rootOutcome = rootRouter(rootTransport).execute(
            request(SemanticOperationId.PACKAGE_FORCE_STOP, pkg = "bad name")
        )
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            rootOutcome.errorCode
        )
        assertNull(rootTransport.lastWriteArgv)
    }

    // ---- legacy action mapping through the router ----------------------------

    @Test
    fun legacyClearDataActionRoutesThroughTheRouterEndToEnd() = runTest {
        val transport = ScriptedTransport()
        val router = shizukuRouter(transport)
        val action = com.nexaflow.domain.models.Action(
            type = com.nexaflow.domain.models.ActionType.SYSTEM_CLEAR_APP_DATA,
            config = mapOf("packageName" to "com.example.app")
        )
        val mapped = SemanticActionMapper.requestFor(
            action, workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
        )
        assertNotNull("The migrated action must map to a typed request", mapped)
        val outcome = router.execute(mapped!!)
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(listOf("pm", "clear", "com.example.app"), transport.lastWriteArgv)
    }

    @Test
    fun legacyDisableActionMapsAndVerifiesEndToEnd() = runTest {
        val transport = ScriptedTransport(
            readResult = SystemControlResult.ok("package:com.example.app") // read-back: disabled
        )
        val router = rootRouter(transport)
        val action = com.nexaflow.domain.models.Action(
            type = com.nexaflow.domain.models.ActionType.SYSTEM_DISABLE_APP,
            config = mapOf("package" to "com.example.app")
        )
        val mapped = SemanticActionMapper.requestFor(
            action, workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
        )
        assertNotNull(mapped)
        assertEquals("false", mapped!!.parameters["enabled"])
        val outcome = router.execute(mapped)
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(true, outcome.verification?.verified)
    }
}
