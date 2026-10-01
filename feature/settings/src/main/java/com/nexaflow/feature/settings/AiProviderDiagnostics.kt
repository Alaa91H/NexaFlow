package com.nexaflow.feature.settings

/** Maps provider HTTP responses to localized, actionable setup guidance. */
internal fun providerProbeFailureMessageRes(statusCode: Int?): Int = when (statusCode) {
    401, 403 -> R.string.ai_provider_test_auth_failed
    404 -> R.string.ai_provider_test_not_found
    429 -> R.string.ai_provider_test_rate_limited
    else -> R.string.ai_provider_test_failed
}
