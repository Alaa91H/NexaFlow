package com.nexaflow.domain.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRequirementResolverTest {

    @Test
    fun `all of requires every concrete capability to be available`() {
        val snapshot = snapshotOf(
            CapabilityId.INTENT_LAUNCH to CapabilityAvailability.AVAILABLE,
            CapabilityId.NETWORK_HTTP_REQUEST to CapabilityAvailability.PERMISSION_REQUIRED
        )

        val result = CapabilityRequirementResolver.resolve(
            CapabilityRequirement.AllOf(
                listOf(
                    CapabilityRequirement.Capability(CapabilityId.INTENT_LAUNCH),
                    CapabilityRequirement.Capability(CapabilityId.NETWORK_HTTP_REQUEST)
                )
            ),
            snapshot
        )

        assertFalse(result.available)
        assertEquals(setOf(CapabilityId.NETWORK_HTTP_REQUEST), result.missingCapabilities)
    }

    @Test
    fun `any of admits when one verified capability is available`() {
        val snapshot = snapshotOf(
            CapabilityId.PACKAGE_FORCE_STOP to CapabilityAvailability.UNAVAILABLE,
            CapabilityId.PACKAGE_SET_ENABLED to CapabilityAvailability.AVAILABLE
        )

        val result = CapabilityRequirementResolver.resolve(
            CapabilityRequirement.AnyOf(
                listOf(
                    CapabilityRequirement.Capability(CapabilityId.PACKAGE_FORCE_STOP),
                    CapabilityRequirement.Capability(CapabilityId.PACKAGE_SET_ENABLED)
                )
            ),
            snapshot
        )

        assertTrue(result.available)
        assertTrue(result.missingCapabilities.isEmpty())
    }

    @Test
    fun `partial backend status does not admit executable action`() {
        val result = CapabilityRequirementResolver.resolve(
            CapabilityRequirement.Capability(CapabilityId.PLUGIN_ACTION),
            snapshotOf(CapabilityId.PLUGIN_ACTION to CapabilityAvailability.PARTIAL)
        )

        assertFalse(result.available)
        assertEquals(setOf(CapabilityId.PLUGIN_ACTION), result.missingCapabilities)
    }

    @Test
    fun `not reverses a verified capability result without inventing availability`() {
        val result = CapabilityRequirementResolver.resolve(
            CapabilityRequirement.Not(CapabilityRequirement.Capability(CapabilityId.PACKAGE_INSTALL)),
            snapshotOf(CapabilityId.PACKAGE_INSTALL to CapabilityAvailability.UNSUPPORTED)
        )

        assertTrue(result.available)
    }

    private fun snapshotOf(vararg states: Pair<CapabilityId, CapabilityAvailability>): CapabilitySnapshot =
        CapabilitySnapshot(
            reports = states.associate { (id, availability) ->
                id to CapabilityAvailabilityReport(
                    capability = id,
                    availability = availability,
                    backends = emptyList()
                )
            },
            observedAtMs = 1L
        )
}
