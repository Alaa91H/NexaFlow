package com.nexaflow.core.airuntime

enum class AiRoutingMode {
    AUTOMATIC,
    LOCAL_ONLY,
    CLOUD_ONLY,
    SELECTED_PROVIDER
}

data class AiRoutingPolicy(
    val mode: AiRoutingMode = AiRoutingMode.AUTOMATIC,
    val selectedProviderId: String? = null,
    val allowCloudFallback: Boolean = false
)

data class AiRoutingRequirements(
    val requireTools: Boolean = false,
    val requireStructuredOutput: Boolean = false,
    val requireStreaming: Boolean = false,
    val requireVision: Boolean = false,
    val requireReasoning: Boolean = false,
    val requireVerified: Boolean = false,
    val requireLocal: Boolean = false
) {
    fun matches(descriptor: AiProviderDescriptor): Boolean {
        val capabilities = descriptor.capabilities
        if (requireLocal && !capabilities.local) return false
        if (requireTools && !capabilities.toolCalling && !capabilities.structuredOutput) return false
        if (requireStructuredOutput && !capabilities.structuredOutput) return false
        if (requireStreaming && !capabilities.streaming) return false
        if (requireVision && !capabilities.vision) return false
        if (requireReasoning && !capabilities.reasoning) return false
        return true
    }
}

enum class AiRouteReason {
    AUTOMATIC_LOCAL,
    AUTOMATIC_CLOUD,
    AUTOMATIC_HEALTH_FALLBACK,
    LOCAL_ONLY,
    CLOUD_ONLY,
    SELECTED,
    NO_AVAILABLE_PROVIDER,
    CLOUD_FALLBACK_DISABLED,
    SELECTED_UNAVAILABLE,
    SELECTED_UNHEALTHY,
    CAPABILITY_UNAVAILABLE,
    HEALTH_UNAVAILABLE,
    NO_MODE_MATCH
}

data class AiRouteDecision(
    val providerId: String? = null,
    val reason: AiRouteReason,
    val healthState: AiProviderHealthState? = null,
    val excludedProviderIds: List<String> = emptyList()
)
