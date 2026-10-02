package com.nexaflow.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSetupPolicyTest {

    @Test
    fun modelDiscoveryRequiresSuccessfulVerification() {
        assertFalse(AiProviderSetupPolicy.canDiscoverModels(AiProviderProbeState.IDLE))
        assertFalse(AiProviderSetupPolicy.canDiscoverModels(AiProviderProbeState.TESTING))
        assertFalse(AiProviderSetupPolicy.canDiscoverModels(AiProviderProbeState.FAILED))
        assertTrue(AiProviderSetupPolicy.canDiscoverModels(AiProviderProbeState.SUCCESS))
    }

    @Test
    fun providerCanOnlyBeEnabledAfterSuccessfulVerification() {
        assertFalse(AiProviderSetupPolicy.enableOnSave(AiProviderProbeState.IDLE))
        assertFalse(AiProviderSetupPolicy.enableOnSave(AiProviderProbeState.TESTING))
        assertFalse(AiProviderSetupPolicy.enableOnSave(AiProviderProbeState.FAILED))
        assertTrue(AiProviderSetupPolicy.enableOnSave(AiProviderProbeState.SUCCESS))
    }
}
