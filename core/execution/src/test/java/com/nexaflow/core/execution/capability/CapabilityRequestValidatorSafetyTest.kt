package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRetryPolicy
import com.nexaflow.domain.capability.CapabilityRetrySafety
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilitySideEffectLevel
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.VerificationMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRequestValidatorSafetyTest {
    @Test
    fun unsafeCapabilityCannotOptIntoMultipleAttempts() {
        val descriptor = descriptor(
            retrySafety = CapabilityRetrySafety.UNSAFE,
            verificationMode = VerificationMode.BEST_EFFORT
        )
        val request = CapabilityRequest(
            capability = CapabilityId.PACKAGE_INSTALL,
            policy = ExecutionPolicy(
                retry = CapabilityRetryPolicy(maxAttempts = 2)
            )
        )

        val result = CapabilityRequestValidator.validate(descriptor, request)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == CapabilityValidationCode.RETRY_NOT_SAFE })
    }

    @Test
    fun requiredVerificationCannotBeDisabledByRequest() {
        val descriptor = descriptor(
            retrySafety = CapabilityRetrySafety.SAFE,
            verificationMode = VerificationMode.REQUIRED
        )
        val request = CapabilityRequest(
            capability = CapabilityId.PACKAGE_INSTALL,
            verification = VerificationMode.NONE
        )

        val result = CapabilityRequestValidator.validate(descriptor, request)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == CapabilityValidationCode.VERIFICATION_REQUIRED })
    }

    private fun descriptor(
        retrySafety: CapabilityRetrySafety,
        verificationMode: VerificationMode
    ) = CapabilityDescriptor(
        id = CapabilityId.PACKAGE_INSTALL,
        displayName = "Package install",
        description = "Test capability",
        risk = CapabilityRiskLevel.DESTRUCTIVE,
        supportedBackends = listOf(CapabilityBackendId.PACKAGE_INSTALLER),
        retrySafety = retrySafety,
        verificationMode = verificationMode,
        sideEffectLevel = CapabilitySideEffectLevel.IRREVERSIBLE
    )
}
