package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiConnectionFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderDiagnosticsTest {

    @Test
    fun `maps provider HTTP failures to actionable localized messages`() {
        assertEquals(R.string.ai_provider_test_auth_failed, providerProbeFailureMessageRes(401))
        assertEquals(R.string.ai_provider_test_auth_failed, providerProbeFailureMessageRes(403))
        assertEquals(R.string.ai_provider_test_not_found, providerProbeFailureMessageRes(404))
        assertEquals(R.string.ai_provider_test_rate_limited, providerProbeFailureMessageRes(429))
        assertEquals(R.string.ai_provider_test_failed, providerProbeFailureMessageRes(500))
        assertEquals(R.string.ai_provider_test_failed, providerProbeFailureMessageRes(null))
    }

    @Test
    fun `typed failure explains what an unknown status cannot`() {
        assertEquals(
            R.string.ai_provider_test_model_missing,
            providerProbeFailureMessageRes(200, AiConnectionFailure.MODEL_NOT_FOUND)
        )
        assertEquals(
            R.string.ai_provider_test_timeout,
            providerProbeFailureMessageRes(null, AiConnectionFailure.TIMEOUT)
        )
        assertEquals(
            R.string.ai_provider_test_network,
            providerProbeFailureMessageRes(null, AiConnectionFailure.DNS)
        )
        assertEquals(
            R.string.ai_provider_test_network,
            providerProbeFailureMessageRes(503, AiConnectionFailure.SERVER_ERROR)
        )
        assertEquals(
            R.string.ai_provider_test_auth_failed,
            providerProbeFailureMessageRes(400, AiConnectionFailure.AUTHENTICATION)
        )
        assertEquals(
            R.string.ai_provider_test_failed,
            providerProbeFailureMessageRes(400, AiConnectionFailure.INVALID_REQUEST)
        )
    }
}
