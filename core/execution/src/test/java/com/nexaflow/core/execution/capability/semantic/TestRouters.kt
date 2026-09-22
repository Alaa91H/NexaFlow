package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Shared test scaffolding for router contract tests. Deliberately tiny: a
 * counting fake strategy plus a router factory pinned to a JVM-safe
 * fingerprint (Build.MANUFACTURER is null on the host).
 */
object TestRouters {

    /** JVM-safe fingerprint; never touches android.os.Build. */
    val TEST_FINGERPRINT = DeviceFingerprint(
        manufacturer = "Test",
        model = "Router Harness",
        device = "harness",
        androidApi = 35,
        securityPatch = "2026-01-01",
        romFamily = RomFamily.STOCK_GOOGLE
    )

    /**
     * Strategy that counts availability probes and executions so tests can
     * prove validation happens before any dispatch.
     */
    class CountingStrategy(
        override val supportedOperations: Set<SemanticOperationId>,
        override val id: StrategyId = StrategyId.ANDROID_PUBLIC_API,
        private val outcome: OperationOutcome? = null
    ) : CapabilityStrategy {
        var availabilityProbes = 0
            private set
        var executions = 0
            private set

        override suspend fun availability(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): StrategyAvailability {
            availabilityProbes++
            return StrategyAvailability(true)
        }

        override suspend fun execute(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): OperationOutcome {
            executions++
            return outcome ?: OperationOutcome(
                operation = operation,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = id,
                message = "ok"
            )
        }
    }

    fun router(vararg strategies: CapabilityStrategy): CapabilityRouter = CapabilityRouter(
        registry = com.nexaflow.core.execution.capability.semantic.OperationRegistry.default(),
        strategies = strategies.toList(),
        evidenceStore = CapabilityEvidenceStore(),
        healthTracker = StrategyHealthTracker(),
        fingerprint = TEST_FINGERPRINT
    )
}
