package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticActionResultMappingTest {

    @Test
    fun onlyCompletedSemanticSuccessMapsToLegacySuccess() {
        OperationOutcomeStatus.values().forEach { status ->
            val legacy = OperationOutcome(
                operation = SemanticOperationId.WIFI_SET_STATE,
                status = status,
                message = status.name
            ).toSystemControlResult()

            assertEquals(
                "Unexpected legacy success mapping for $status",
                status == OperationOutcomeStatus.SUCCESS,
                legacy.success
            )
            assertEquals(status.name, legacy.message)
        }
    }

    @Test
    fun unknownSemanticOutcomePreservesUncertainSideEffectSignal() {
        val legacy = OperationOutcome(
            operation = SemanticOperationId.HOTSPOT_SET_STATE,
            status = OperationOutcomeStatus.UNKNOWN,
            strategy = StrategyId.SHIZUKU_USER_SERVICE,
            message = "transport dropped after dispatch"
        ).toSystemControlResult()

        assertEquals(false, legacy.success)
        assertEquals(true, legacy.outcomeUncertain)
    }

    @Test
    fun mappingRetainsStrategyAndVerificationProvenance() {
        val legacy = OperationOutcome(
            operation = SemanticOperationId.NFC_SET_STATE,
            status = OperationOutcomeStatus.SUCCESS,
            strategy = StrategyId.ROOT_SHELL,
            errorCode = CapabilityErrorCode.VERIFICATION_FAILED,
            message = "verified",
            verification = VerificationResult(
                attempted = true,
                verified = true,
                message = "read-back matched"
            )
        ).toSystemControlResult()

        assertEquals("ROOT_SHELL", legacy.executionChannel)
        assertEquals("VERIFICATION_FAILED", legacy.errorCode)
        assertEquals(true, legacy.verificationAttempted)
        assertEquals(true, legacy.verified)
    }
}
