package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.VerificationResult
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

    @Test
    fun mappingRetainsBackendErrorAndVerificationProvenance() {
        val legacy = CapabilityResult(
            status = CapabilityStatus.FAILED,
            backend = CapabilityBackendId.ROOT,
            errorCode = CapabilityErrorCode.VERIFICATION_FAILED,
            message = "post-condition mismatch",
            verification = VerificationResult(
                attempted = true,
                verified = false,
                message = "mismatch"
            )
        ).toSystemControlResult()

        assertEquals("ROOT", legacy.executionChannel)
        assertEquals("VERIFICATION_FAILED", legacy.errorCode)
        assertEquals(true, legacy.verificationAttempted)
        assertEquals(false, legacy.verified)
    }
}
