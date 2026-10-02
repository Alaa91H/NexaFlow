package com.nexaflow.feature.settings

internal object AiProviderSetupPolicy {
    fun canDiscoverModels(probeState: AiProviderProbeState): Boolean =
        probeState == AiProviderProbeState.SUCCESS

    fun enableOnSave(probeState: AiProviderProbeState): Boolean =
        probeState == AiProviderProbeState.SUCCESS
}
