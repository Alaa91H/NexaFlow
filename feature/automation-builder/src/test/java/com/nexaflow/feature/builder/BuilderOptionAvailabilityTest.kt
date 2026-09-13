package com.nexaflow.feature.builder

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.CapabilitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BuilderOptionAvailabilityTest {

    @Test
    fun availableCapabilityIsReady() {
        val snapshot = snapshotOf(CapabilityId.PACKAGE_FORCE_STOP to CapabilityAvailability.AVAILABLE)

        assertEquals(
            BuilderOptionAvailability.READY,
            classifyBuilderRequirement(
                CapabilityRequirement.Capability(CapabilityId.PACKAGE_FORCE_STOP),
                snapshot
            )
        )
    }

    @Test
    fun permissionRequiredCapabilityIsGrantableInsteadOfUnsupported() {
        val snapshot = snapshotOf(
            CapabilityId.PACKAGE_FORCE_STOP to CapabilityAvailability.PERMISSION_REQUIRED
        )

        assertEquals(
            BuilderOptionAvailability.PERMISSION_REQUIRED,
            classifyBuilderRequirement(
                CapabilityRequirement.Capability(CapabilityId.PACKAGE_FORCE_STOP),
                snapshot
            )
        )
    }

    @Test
    fun partialCapabilityStaysUnavailableUntilARealBackendIsReady() {
        val snapshot = snapshotOf(CapabilityId.SYSTEM_SETTING_WRITE to CapabilityAvailability.PARTIAL)

        assertEquals(
            BuilderOptionAvailability.UNAVAILABLE,
            classifyBuilderRequirement(
                CapabilityRequirement.Capability(CapabilityId.SYSTEM_SETTING_WRITE),
                snapshot
            )
        )
    }

    @Test
    fun unsupportedCapabilityStaysUnsupported() {
        val snapshot = snapshotOf(CapabilityId.ACCESSIBILITY_GESTURE to CapabilityAvailability.UNSUPPORTED)

        assertEquals(
            BuilderOptionAvailability.UNSUPPORTED,
            classifyBuilderRequirement(
                CapabilityRequirement.Capability(CapabilityId.ACCESSIBILITY_GESTURE),
                snapshot
            )
        )
    }

    @Test
    fun anyOfChoosesPermissionRouteOverUnsupportedAlternative() {
        val snapshot = snapshotOf(
            CapabilityId.PACKAGE_FORCE_STOP to CapabilityAvailability.UNSUPPORTED,
            CapabilityId.SYSTEM_SETTING_WRITE to CapabilityAvailability.PERMISSION_REQUIRED
        )
        val requirement = CapabilityRequirement.AnyOf(
            listOf(
                CapabilityRequirement.Capability(CapabilityId.PACKAGE_FORCE_STOP),
                CapabilityRequirement.Capability(CapabilityId.SYSTEM_SETTING_WRITE)
            )
        )

        assertEquals(
            BuilderOptionAvailability.PERMISSION_REQUIRED,
            classifyBuilderRequirement(requirement, snapshot)
        )
    }

    @Test
    fun allOfUsesWeakestRequiredCapability() {
        val snapshot = snapshotOf(
            CapabilityId.PACKAGE_READ to CapabilityAvailability.AVAILABLE,
            CapabilityId.SYSTEM_SETTING_WRITE to CapabilityAvailability.PERMISSION_REQUIRED,
            CapabilityId.FILE_COPY to CapabilityAvailability.UNSUPPORTED
        )
        val requirement = CapabilityRequirement.AllOf(
            listOf(
                CapabilityRequirement.Capability(CapabilityId.PACKAGE_READ),
                CapabilityRequirement.Capability(CapabilityId.SYSTEM_SETTING_WRITE),
                CapabilityRequirement.Capability(CapabilityId.FILE_COPY)
            )
        )

        assertEquals(
            BuilderOptionAvailability.UNSUPPORTED,
            classifyBuilderRequirement(requirement, snapshot)
        )
    }

    @Test
    fun notRequirementUsesResolvedTruthInsteadOfBackendSeverity() {
        val snapshot = snapshotOf(CapabilityId.PACKAGE_INSTALL to CapabilityAvailability.UNAVAILABLE)
        val requirement = CapabilityRequirement.Not(
            CapabilityRequirement.Capability(CapabilityId.PACKAGE_INSTALL)
        )

        assertEquals(
            BuilderOptionAvailability.READY,
            classifyBuilderRequirement(requirement, snapshot)
        )
    }

    private fun snapshotOf(
        vararg states: Pair<CapabilityId, CapabilityAvailability>
    ): CapabilitySnapshot = CapabilitySnapshot(
        reports = states.associate { (id, availability) ->
            id to CapabilityAvailabilityReport(
                capability = id,
                availability = availability,
                backends = listOf(
                    BackendAvailability(
                        backend = CapabilityBackendId.ANDROID_API,
                        availability = availability
                    )
                )
            )
        },
        observedAtMs = 1L
    )
}
