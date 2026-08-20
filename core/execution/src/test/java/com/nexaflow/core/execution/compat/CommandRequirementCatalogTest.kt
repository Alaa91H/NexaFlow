package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirementResolver
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRequirementCatalogTest {

    @Test
    fun `public URL handoff is admitted only when intent capability is available`() {
        val available = CapabilityRequirementResolver.resolve(
            CommandRequirementCatalog.requirementFor(ActionType.SYSTEM_OPEN_URL),
            snapshot(CapabilityId.INTENT_LAUNCH, CapabilityAvailability.AVAILABLE)
        )
        val unavailable = CapabilityRequirementResolver.resolve(
            CommandRequirementCatalog.requirementFor(ActionType.SYSTEM_OPEN_URL),
            CapabilitySnapshot()
        )

        assertTrue(available.available)
        assertFalse(unavailable.available)
    }

    @Test
    fun `unmapped elevated command is never admitted from generic root state`() {
        val result = CapabilityRequirementResolver.resolve(
            CommandRequirementCatalog.requirementFor(ActionType.SYSTEM_REBOOT),
            snapshot(CapabilityId.PACKAGE_FORCE_STOP, CapabilityAvailability.AVAILABLE)
        )

        assertFalse(result.available)
    }

    @Test
    fun `Google Play update action remains hidden until a documented typed backend exists`() {
        val result = CapabilityRequirementResolver.resolve(
            CommandRequirementCatalog.requirementFor(ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS),
            CapabilitySnapshot()
        )

        assertFalse(result.available)
    }

    @Test
    fun `universal and public trigger commands require no extra capability snapshot`() {
        assertTrue(
            CapabilityRequirementResolver.resolve(
                CommandRequirementCatalog.requirementFor(ActionType.SYSTEM_SEND_NOTIFICATION),
                CapabilitySnapshot()
            ).available
        )
        assertTrue(
            CapabilityRequirementResolver.resolve(
                CommandRequirementCatalog.requirementFor(TriggerType.TIME),
                CapabilitySnapshot()
            ).available
        )
    }

    private fun snapshot(id: CapabilityId, availability: CapabilityAvailability): CapabilitySnapshot = CapabilitySnapshot(
        reports = mapOf(
            id to CapabilityAvailabilityReport(
                capability = id,
                availability = availability,
                backends = listOf(BackendAvailability(CapabilityBackendId.INTENT, availability))
            )
        )
    )
}
