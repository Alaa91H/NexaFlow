package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityResultMappingTest {

    @Test
    fun onlyCompletedCapabilitySuccessMapsToLegacySuccess() {
        CapabilityStatus.values().forEach { status ->
            val legacy = CapabilityResult(
                status = status,
                message = status.name
            ).toSystemControlResult()

            assertEquals(
                "Unexpected legacy success mapping for $status",
                status == CapabilityStatus.SUCCESS,
                legacy.success
            )
            assertEquals(status.name, legacy.message)
        }
    }
}
