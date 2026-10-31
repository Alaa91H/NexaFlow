package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiConnectionFailure

/** Maps a provider failure (typed reason first, HTTP status as fallback) to setup guidance. */
internal fun providerProbeFailureMessageRes(
    statusCode: Int?,
    failure: AiConnectionFailure? = null
): Int = when (failure) {
    AiConnectionFailure.AUTHENTICATION,
    AiConnectionFailure.PERMISSION -> R.string.ai_provider_test_auth_failed
    AiConnectionFailure.RATE_LIMITED,
    AiConnectionFailure.QUOTA_EXCEEDED -> R.string.ai_provider_test_rate_limited
    AiConnectionFailure.MODEL_NOT_FOUND -> R.string.ai_provider_test_model_missing
    AiConnectionFailure.ENDPOINT_NOT_FOUND -> R.string.ai_provider_test_not_found
    AiConnectionFailure.TIMEOUT -> R.string.ai_provider_test_timeout
    AiConnectionFailure.NETWORK,
    AiConnectionFailure.DNS,
    AiConnectionFailure.TLS,
    AiConnectionFailure.SERVER_ERROR -> R.string.ai_provider_test_network
    else -> when (statusCode) {
        401, 403 -> R.string.ai_provider_test_auth_failed
        404 -> R.string.ai_provider_test_not_found
        429 -> R.string.ai_provider_test_rate_limited
        else -> R.string.ai_provider_test_failed
    }
}
