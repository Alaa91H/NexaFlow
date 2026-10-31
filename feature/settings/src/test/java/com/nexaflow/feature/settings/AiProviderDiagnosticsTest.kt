package com.nexaflow.feature.settings

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
}
