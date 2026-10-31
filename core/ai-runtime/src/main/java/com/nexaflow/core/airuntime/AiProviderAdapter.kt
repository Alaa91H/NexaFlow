package com.nexaflow.core.airuntime

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

enum class AiConnectionFailure {
    AUTHENTICATION,
    PERMISSION,
    ENDPOINT_NOT_FOUND,
    MODEL_NOT_FOUND,
    INVALID_REQUEST,
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

class AiProviderRequestException(
    val failure: AiConnectionFailure,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    /** Provider-authored error code (for example `invalid_api_key`), bounded and redacted. */
    val providerCode: String? = null,
    /** Provider-authored explanation for the safe log and diagnostics, never a secret. */
    val providerMessage: String? = null,
    cause: Throwable? = null
) : RuntimeException("AI provider request failed", cause) {
    init {
        require(retryAfterMs == null || retryAfterMs >= 0)
        require(providerCode == null || providerCode.length <= MAX_ERROR_CODE_CHARS)
        require(providerMessage == null || providerMessage.length <= MAX_ERROR_MESSAGE_CHARS)
    }

    companion object {
        const val MAX_ERROR_CODE_CHARS = 64
        const val MAX_ERROR_MESSAGE_CHARS = 512

        fun fromHttpStatus(
            status: Int,
            retryAfterMs: Long? = null
        ): AiProviderRequestException = AiProviderRequestException(
            failure = AiProviderFailureClassifier.fromHttpStatus(status),
            httpStatus = status,
            retryAfterMs = retryAfterMs
        )
    }
}

/** Provider error envelope extracted from a non-2xx response body. */
data class AiProviderErrorPayload(
    val code: String? = null,
    val message: String? = null
)

/**
 * Builds typed failures from a provider HTTP response.
 *
 * Classification uses the provider body when it is available: Gemini answers an
 * invalid key with HTTP 400 and a message instead of 401, and every provider
 * reports "model not found" through both 400 and 404. Without the body these
 * cases collapse into generic failures the user cannot act on.
 */
object AiProviderHttpFailure {
    private val json = Json { ignoreUnknownKeys = true }
    private val secrets = listOf(
        Regex("sk-[A-Za-z0-9_-]{8,}"),
        Regex("AIza[0-9A-Za-z_-]{10,}"),
        Regex("xox[baprs]-[A-Za-z0-9-]{8,}"),
        Regex("Bearer\\s+[A-Za-z0-9._~+/=-]{8,}", RegexOption.IGNORE_CASE)
    )

    fun of(status: Int, body: String, retryAfterMs: Long? = null): AiProviderRequestException {
        val payload = parse(body)
        return AiProviderRequestException(
            failure = AiProviderFailureClassifier.fromHttpStatus(status, payload?.message),
            httpStatus = status,
            retryAfterMs = retryAfterMs,
            providerCode = sanitizeCode(payload?.code),
            providerMessage = sanitizeMessage(payload?.message)
        )
    }

    fun classify(status: Int, body: String): AiConnectionFailure =
        AiProviderFailureClassifier.fromHttpStatus(status, parse(body)?.message)

    fun parse(body: String): AiProviderErrorPayload? {
        if (body.isBlank() || body.length > MAX_BODY_CHARS) return null
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val error = root["error"]
        val errorObject = error as? JsonObject
        // Prefer symbolic codes: Gemini uses a numeric `code` plus a `status`,
        // OpenAI/Anthropic use `code`/`type`.
        val code = listOf("code", "status", "type")
            .asSequence()
            .mapNotNull { key -> errorObject?.get(key)?.let(::primitiveText) }
            .firstOrNull { value -> value.any { !it.isDigit() } }
            ?: root["code"]?.let(::primitiveText)
        val message = errorObject?.get("message")?.let(::primitiveText)
            ?: (error as? JsonPrimitive)?.contentOrNull
            ?: root["message"]?.let(::primitiveText)
            ?: root["error_description"]?.let(::primitiveText)
        if (code == null && message == null) return null
        return AiProviderErrorPayload(code = code, message = message)
    }

    private fun primitiveText(element: kotlinx.serialization.json.JsonElement): String? =
        (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    fun sanitizeCode(value: String?): String? = value
        ?.replace(Regex("[^A-Za-z0-9._:-]"), "_")
        ?.replace(Regex("_+"), "_")
        ?.trim('_')
        ?.take(AiProviderRequestException.MAX_ERROR_CODE_CHARS)
        ?.takeIf(String::isNotEmpty)

    fun sanitizeMessage(value: String?): String? {
        var text = value
            ?.replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return null
        secrets.forEach { pattern -> text = text.replace(pattern, "[REDACTED]") }
        return text.take(AiProviderRequestException.MAX_ERROR_MESSAGE_CHARS).takeIf(String::isNotEmpty)
    }

    private const val MAX_BODY_CHARS = 64 * 1024
}

/** Parses the `Retry-After` header (delay-seconds or HTTP-date) into milliseconds. */
object AiRetryAfter {
    const val MAX_RETRY_AFTER_MS = 24L * 60L * 60L * 1_000L

    fun parse(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        raw.toLongOrNull()?.let { seconds ->
            if (seconds < 0L) return null
            return (seconds * 1_000L).coerceAtMost(MAX_RETRY_AFTER_MS)
        }
        val target = try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
        } catch (_: DateTimeParseException) {
            return null
        }
        return (target.toInstant().toEpochMilli() - nowMillis).coerceIn(0L, MAX_RETRY_AFTER_MS)
    }
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
    private val authKeywords = listOf(
        "api key not valid", "api key is invalid", "invalid api key", "incorrect api key",
        "invalid_api_key", "invalid x-api-key", "authentication failed", "unauthenticated"
    )
    private val quotaKeywords = listOf(
        "insufficient_quota", "quota exceeded", "exceeded your current quota", "resource exhausted",
        "resource_exhausted", "billing_not_active", "insufficient balance"
    )
    private val rateKeywords = listOf(
        "rate limit", "rate_limit", "too many requests", "temporarily rate-limited"
    )
    private val modelNotFoundKeywords = listOf(
        "not found", "does not exist", "not supported", "not available", "unknown model",
        "invalid model", "no such model"
    )

    /**
     * Classifies a provider failure. [providerMessage] is optional, but when it
     * is available it is authoritative: Gemini reports an invalid key as 400 and
     * every provider mixes endpoint and model 404s.
     */
    fun fromHttpStatus(
        status: Int?,
        providerMessage: String? = null
    ): AiConnectionFailure {
        val text = providerMessage?.lowercase().orEmpty()
        return when {
            text.containsAny(authKeywords) -> AiConnectionFailure.AUTHENTICATION
            text.containsAny(quotaKeywords) -> AiConnectionFailure.QUOTA_EXCEEDED
            status == 429 || text.containsAny(rateKeywords) -> AiConnectionFailure.RATE_LIMITED
            status != null && status in MODEL_LOOKUP_STATUSES &&
                text.contains("model") && text.containsAny(modelNotFoundKeywords) ->
                AiConnectionFailure.MODEL_NOT_FOUND
            else -> fromStatusOnly(status)
        }
    }

    private fun fromStatusOnly(status: Int?): AiConnectionFailure = when (status) {
        401 -> AiConnectionFailure.AUTHENTICATION
        403 -> AiConnectionFailure.PERMISSION
        404 -> AiConnectionFailure.ENDPOINT_NOT_FOUND
        408 -> AiConnectionFailure.TIMEOUT
        429 -> AiConnectionFailure.RATE_LIMITED
        400, 413, 422 -> AiConnectionFailure.INVALID_REQUEST
        in 500..599 -> AiConnectionFailure.SERVER_ERROR
        else -> AiConnectionFailure.UNKNOWN
    }

    private fun String.containsAny(candidates: List<String>): Boolean =
        candidates.any { contains(it) }

    private val MODEL_LOOKUP_STATUSES = setOf(400, 404, 422)

    fun fromThrowable(failure: Throwable): AiConnectionFailure {
        if (failure is CancellationException) throw failure
        return when (failure) {
            is AiProviderRequestException -> failure.failure
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
