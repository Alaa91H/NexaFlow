package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.core.execution.capability.semantic.strategies.RootTypedStrategy
import com.nexaflow.core.execution.capability.semantic.strategies.ShizukuTypedStrategy
import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry parity gates, mirroring the requirement: an OperationSpec that
 * names a strategy without a corresponding production implementation, or an
 * operation with no strategies at all, must fail here — not in production.
 */
class OperationRegistryParityTest {

    /** Strategies with real implementations shipping in production DI. */
    private val implementedStrategies: Set<StrategyId> = setOf(
        StrategyId.ANDROID_PUBLIC_API,
        StrategyId.SHIZUKU_USER_SERVICE,
        StrategyId.ROOT_SHELL,
        StrategyId.SETTINGS_USER_ACTION
    )

    @Test
    fun everySpecHasAtLeastOneImplementedStrategy() {
        OperationRegistry.default().operations().forEach { spec ->
            val implemented = spec.strategies.filter { it in implementedStrategies }
            assertTrue(
                "Operation ${spec.id.name} declares no implemented strategy: ${spec.strategies}",
                implemented.isNotEmpty()
            )
        }
    }

    @Test
    fun everyWriteOperationHasShizukuAndRootImplementation() {
        val shizuku = ShizukuTypedStrategy(
            shizukuGranted = { true },
            userServiceReady = { true },
            execute = { SystemControlResult.ok("ok") }
        )
        val root = RootTypedStrategy(
            rootAvailable = { true },
            execute = { SystemControlResult.ok("ok") }
        )

        OperationRegistry.default().operations()
            .filter { !it.id.isReadOnly }
            .forEach { spec ->
                assertTrue(
                    "${spec.id.name} must expose a Shizuku fallback",
                    StrategyId.SHIZUKU_USER_SERVICE in spec.strategies
                )
                assertTrue(
                    "${spec.id.name} must expose a Root fallback",
                    StrategyId.ROOT_SHELL in spec.strategies
                )
                assertTrue(
                    "${spec.id.name} is declared for Shizuku but not implemented",
                    spec.id in shizuku.supportedOperations
                )
                assertTrue(
                    "${spec.id.name} is declared for Root but not implemented",
                    spec.id in root.supportedOperations
                )
            }
    }

    @Test
    fun registryDoesNotReferenceUnwiredWriteSettingsStrategy() {
        OperationRegistry.default().operations().forEach { spec ->
            assertTrue(
                "${spec.id.name} references WRITE_SETTINGS but production DI has no such strategy",
                StrategyId.WRITE_SETTINGS !in spec.strategies
            )
        }
    }

    @Test
    fun everyReadWriteOperationHasAReadableCounterpart() {
        OperationRegistry.default().operations().filter { !it.id.isReadOnly }.forEach { spec ->
            val counterpart = SemanticOperationId.counterpartOf(spec.id)
            assertNotNull(
                "Write operation ${spec.id.name} has no GET counterpart for reconciliation",
                counterpart
            )
        }
    }

    @Test
    fun writeOperationsRequireVerificationAndReadOperationsDoNot() {
        // One-shot package transitions have no reliable observable
        // post-condition (a killed process may be restarted instantly; the
        // enabled-state probe says nothing about cleared data), so they are
        // the documented BEST_EFFORT exception. Everything else writable must
        // declare strict REQUIRED verification.
        val bestEffortException = setOf(
            SemanticOperationId.PACKAGE_FORCE_STOP,
            SemanticOperationId.PACKAGE_CLEAR_DATA
        )
        OperationRegistry.default().operations().forEach { spec ->
            if (spec.id.isReadOnly) {
                assertEquals(
                    "Read operation ${spec.id.name} must not require verification",
                    com.nexaflow.domain.capability.VerificationMode.NONE,
                    spec.verificationMode
                )
            } else if (spec.id in bestEffortException) {
                assertEquals(
                    "One-shot package transition ${spec.id.name} must stay honestly BEST_EFFORT",
                    com.nexaflow.domain.capability.VerificationMode.BEST_EFFORT,
                    spec.verificationMode
                )
            } else {
                assertEquals(
                    "Write operation ${spec.id.name} must require verification",
                    com.nexaflow.domain.capability.VerificationMode.REQUIRED,
                    spec.verificationMode
                )
                assertEquals(
                    "Write operation ${spec.id.name} must be reversible or explicitly unknown",
                    true,
                    spec.sideEffectLevel != com.nexaflow.domain.capability.CapabilitySideEffectLevel.NONE
                )
            }
        }
    }

    @Test
    fun defaultRegistryRegistersEverySpecExactlyOnce() {
        val registry = OperationRegistry.default()
        val all = registry.operations()
        assertEquals(all.size, all.map { it.id }.distinct().size)
        all.forEach { spec ->
            assertNotNull(registry.specFor(spec.id))
        }
    }

    @Test
    fun privilegedStrategiesAreNotTheOnlyRouteForAnyOperation() {
        // Every operation must retain at least one non-privileged strategy so
        // a device without Shizuku/Root is never locked out by the catalog.
        // Package operations are the documented exception: force-stop, clear
        // data and package enable/disable are platform-privileged by design,
        // so the honest outcome without a privileged runtime is the router's
        // PENDING_USER_ACTION, never a silent fake success.
        val privileged = setOf(StrategyId.SHIZUKU_USER_SERVICE, StrategyId.ROOT_SHELL)
        val packageOperations = setOf(
            SemanticOperationId.PACKAGE_FORCE_STOP,
            SemanticOperationId.PACKAGE_CLEAR_DATA,
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE
        )
        OperationRegistry.default().operations().forEach { spec ->
            if (spec.id in packageOperations) return@forEach
            val nonPrivileged = spec.strategies.filter { it !in privileged }
            assertTrue(
                "Operation ${'$'}{spec.id.name} is only reachable through privileged strategies",
                nonPrivileged.isNotEmpty()
            )
        }
    }

    @Test
    fun packageOperationsHaveTheirGetCounterpartRegistered() {
        val registry = OperationRegistry.default()
        assertEquals(
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        )
        assertEquals(
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_GET_ENABLED_STATE)
        )
        assertNotNull(registry.specFor(SemanticOperationId.PACKAGE_FORCE_STOP))
        assertNotNull(registry.specFor(SemanticOperationId.PACKAGE_CLEAR_DATA))
        assertNotNull(registry.specFor(SemanticOperationId.PACKAGE_SET_ENABLED_STATE))
        assertNotNull(registry.specFor(SemanticOperationId.PACKAGE_GET_ENABLED_STATE))
    }

    @Test
    fun packageClearDataIsHonestlyIrreversible() {
        val spec = OperationRegistry.default().requiresOperation(SemanticOperationId.PACKAGE_CLEAR_DATA)
        assertEquals(
            "Clearing user data must never claim compensation support",
            com.nexaflow.domain.capability.CapabilityCompensationSupport.UNSUPPORTED,
            spec.compensation
        )
        assertEquals(com.nexaflow.domain.capability.CapabilityRiskLevel.HIGH, spec.risk)
    }
}
