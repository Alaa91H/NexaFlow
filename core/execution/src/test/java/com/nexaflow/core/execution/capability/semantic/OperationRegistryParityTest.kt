package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
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
        OperationRegistry.default().operations().forEach { spec ->
            if (spec.id.isReadOnly) {
                assertEquals(
                    "Read operation ${spec.id.name} must not require verification",
                    com.nexaflow.domain.capability.VerificationMode.NONE,
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
        val privileged = setOf(StrategyId.SHIZUKU_USER_SERVICE, StrategyId.ROOT_SHELL)
        OperationRegistry.default().operations().forEach { spec ->
            val nonPrivileged = spec.strategies.filter { it !in privileged }
            assertTrue(
                "Operation ${spec.id.name} is only reachable through privileged strategies",
                nonPrivileged.isNotEmpty()
            )
        }
    }
}
