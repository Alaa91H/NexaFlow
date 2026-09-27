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

enum class AiRouteReason {
    AUTOMATIC_LOCAL,
    AUTOMATIC_CLOUD,
    LOCAL_ONLY,
    CLOUD_ONLY,
    SELECTED,
    NO_AVAILABLE_PROVIDER,
    CLOUD_FALLBACK_DISABLED,
    SELECTED_UNAVAILABLE,
    NO_MODE_MATCH
}

data class AiRouteDecision(
    val providerId: String? = null,
    val reason: AiRouteReason
)
