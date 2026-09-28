package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.operation.SemanticOperationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CanonicalIdentityRegistryTest {

    @Test
    fun defaultRegistryContainsEveryT01ReviewedIdentity() {
        val registry = CanonicalIdentityRegistry.default()

        assertEquals(134, registry.targets().size)
        assertEquals(46, registry.operations().size)
        assertEquals(12, registry.predicates().size)
        assertEquals(CapabilityId.entries.size, registry.capabilities().size)
    }

    @Test
    fun allRegistryStableIdsAreUnique() {
        val registry = CanonicalIdentityRegistry.default()

        assertEquals(
            registry.targets().size,
            registry.targets().map { it.id.value }.distinct().size
        )
        assertEquals(
            registry.operations().size,
            registry.operations().map { it.id.value }.distinct().size
        )
        assertEquals(
            registry.predicates().size,
            registry.predicates().map { it.id.value }.distinct().size
        )
        assertEquals(
            registry.capabilities().size,
            registry.capabilities().map { it.id.value }.distinct().size
        )
    }

    @Test
    fun everyExistingSemanticOperationMapsIntoCanonicalRegistry() {
        val registry = CanonicalIdentityRegistry.default()
        val identities = SemanticOperationId.entries.map { it.stableIdentity }

        identities.forEach { identity ->
            assertNotNull(registry.target(identity.target))
            assertNotNull(registry.operation(identity.operation))
        }
        assertEquals(identities.size, identities.distinct().size)
    }

    @Test
    fun everyExistingCapabilityHasAStableUniqueIdentity() {
        val ids = CapabilityId.entries.map { it.stableId }

        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { id ->
            assertTrue(id.value.startsWith("core.capability."))
            assertNotNull(CanonicalIdentityRegistry.default().capability(id))
        }
    }

    @Test
    fun invalidStableIdsFailClosed() {
        expectIllegalArgument { TargetId("WIFI") }
        expectIllegalArgument { OperationId("core") }
        expectIllegalArgument { PredicateId("core..predicate") }
        expectIllegalArgument { CapabilityStableId("core.capability.bad-value") }
    }

    @Test
    fun duplicateRegistrationFailsFast() {
        val target = CanonicalTargetDescriptor(TargetId("core.test.target"))
        expectIllegalArgument {
            CanonicalIdentityRegistry.of(
                targets = listOf(target, target),
                operations = emptyList(),
                predicates = emptyList(),
                capabilities = emptyList(),
            )
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
