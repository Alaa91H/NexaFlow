package com.nexaflow.core.airuntime

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

enum class AiConnectionFailure {
    AUTHENTICATION,
    PERMISSION,
    ENDPOINT_NOT_FOUND,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    TIMEOUT,
    DNS,
    TLS,
    NETWORK,
    INVALID_RESPONSE,
    UNSUPPORTED_PROTOCOL,
    SERVER_ERROR,
    UNKNOWN
}

data class AiConnectionTestResult(
    val success: Boolean,
    val providerId: String,
    val dialect: AiApiDialect,
    val httpStatus: Int? = null,
    val failure: AiConnectionFailure? = null,
    val retryAfterMs: Long? = null,
    val latencyMs: Long = 0L
) {
    init {
        require(latencyMs >= 0)
        require(retryAfterMs == null || retryAfterMs >= 0)
        require(!success || failure == null)
    }
}

data class AiDiscoveredModel(
    val id: String,
    val ownedBy: String? = null,
    val contextTokens: Int? = null
)

data class AiModelDiscoveryResult(
    val success: Boolean,
    val models: List<AiDiscoveredModel> = emptyList(),
    val httpStatus: Int? = null,
    val failure: AiConnectionFailure? = null
) {
    init {
        require(!success || failure == null)
    }
}

data class AiCapabilityResult(
    val success: Boolean,
    val modelId: String,
    val capabilities: AiProviderCapabilities = AiProviderCapabilities(),
    val failure: AiConnectionFailure? = null
) {
    init {
        require(!success || failure == null)
    }
}

interface AiProviderAdapter : AiModelProvider {
    suspend fun verifyConnection(): AiConnectionTestResult
    suspend fun listModels(): AiModelDiscoveryResult
    suspend fun discoverCapabilities(model: AiModelDescriptorV2): AiCapabilityResult
}

object AiProviderFailureClassifier {
    fun fromHttpStatus(status: Int?): AiConnectionFailure = when (status) {
        401 -> AiConnectionFailure.AUTHENTICATION
        403 -> AiConnectionFailure.PERMISSION
        404 -> AiConnectionFailure.ENDPOINT_NOT_FOUND
        408 -> AiConnectionFailure.TIMEOUT
        429 -> AiConnectionFailure.RATE_LIMITED
        in 500..599 -> AiConnectionFailure.SERVER_ERROR
        else -> AiConnectionFailure.UNKNOWN
    }

    fun fromThrowable(failure: Throwable): AiConnectionFailure {
        if (failure is CancellationException) throw failure
        return when (failure) {
            is SocketTimeoutException -> AiConnectionFailure.TIMEOUT
            is UnknownHostException -> AiConnectionFailure.DNS
            is SSLException -> AiConnectionFailure.TLS
            is IOException -> AiConnectionFailure.NETWORK
            else -> AiConnectionFailure.UNKNOWN
        }
    }
}

internal fun elapsedMillis(startedAtNanos: Long): Long =
    ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
