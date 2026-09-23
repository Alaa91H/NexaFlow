package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
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
}
