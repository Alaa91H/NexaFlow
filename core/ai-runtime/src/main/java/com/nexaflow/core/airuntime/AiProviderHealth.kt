package com.nexaflow.core.airuntime

enum class AiProviderHealthState {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    COOLDOWN,
    UNAVAILABLE
}

data class AiProviderHealthSnapshot(
    val providerId: String,
    val state: AiProviderHealthState = AiProviderHealthState.UNKNOWN,
    val verified: Boolean = false,
    val consecutiveFailures: Int = 0,
    val lastSuccessAtMillis: Long? = null,
    val lastFailureAtMillis: Long? = null,
    val lastFailure: AiConnectionFailure? = null,
    val cooldownUntilMillis: Long? = null
) {
    init {
        require(providerId.isNotBlank())
        require(consecutiveFailures >= 0)
        require(lastSuccessAtMillis == null || lastSuccessAtMillis >= 0)
        require(lastFailureAtMillis == null || lastFailureAtMillis >= 0)
        require(cooldownUntilMillis == null || cooldownUntilMillis >= 0)
    }

    fun isCoolingDown(nowMillis: Long): Boolean =
        cooldownUntilMillis?.let { nowMillis < it } == true

    fun isRoutable(nowMillis: Long, requireVerified: Boolean): Boolean {
        if (state == AiProviderHealthState.UNAVAILABLE) return false
        if (isCoolingDown(nowMillis)) return false
        if (requireVerified && !verified) return false
        return true
    }

    fun routingRank(nowMillis: Long): Int = when {
        isCoolingDown(nowMillis) -> 4
        state == AiProviderHealthState.HEALTHY -> 0
        state == AiProviderHealthState.UNKNOWN -> 1
        state == AiProviderHealthState.DEGRADED -> 2
        state == AiProviderHealthState.COOLDOWN -> 2
        state == AiProviderHealthState.UNAVAILABLE -> 5
        else -> 3
    }
}
