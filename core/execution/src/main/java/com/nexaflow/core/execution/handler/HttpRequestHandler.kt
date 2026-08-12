package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.workflow.RetryExecutor
import com.nexaflow.domain.workflow.RetryPolicy
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends an HTTP request (`SYSTEM_HTTP_REQUEST`) with the Phase-3 retry layer.
 * The URL and body support %variable injection (resolved by the engine before
 * dispatch). Runs on the IO dispatcher so a slow endpoint never blocks the
 * calling coroutine.
 *
 * Retry semantics (matching [RetryPolicy] + [RetryExecutor]):
 * - connection-level failures (code 0), HTTP 5xx, and HTTP 429 are **retryable**
 *   with exponential backoff + jitter up to `maxAttempts`;
 * - HTTP 4xx (except 429) are **permanent** — they fail immediately without
 *   burning retries on a request that will never succeed;
 * - every attempt carries the **same `Idempotency-Key`** (hash of
 *   automationId | action | method | url | body) so a server honoring the
 *   header de-duplicates a replayed request.
 *
 * The transport is injectable for atomic tests; [HttpURLConnectionTransport] is
 * the production default.
 */
class HttpRequestHandler(
    private val retryExecutor: RetryExecutor = RetryExecutor(),
    private val transport: HttpTransport = HttpURLConnectionTransport(),
) : ActionHandler {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_HTTP_REQUEST)

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val url = action.config["url"].orEmpty().trim()
        if (url.isEmpty()) return SystemControlResult.fail("No URL configured")
        val method = action.config["method"].orEmpty().uppercase().ifBlank { "GET" }
        val body = action.config["body"].orEmpty()
        val timeoutMs = (action.config["timeoutMs"]?.toLongOrNull() ?: 10_000L).coerceIn(1_000L, 60_000L)
        val policy = retryPolicy(action.config)
        // Stable across every attempt of the same logical call.
        val idempotencyKey = retryExecutor.idempotencyKey(
            ctx.automationId ?: "unknown",
            action.type.name,
            "$method|$url|$body"
        )
        val headers = mapOf(
            "User-Agent" to "NexaFlow/1.0",
            "Idempotency-Key" to idempotencyKey
        )
        return withContext(Dispatchers.IO) {
            for (attempt in 1..policy.maxAttempts) {
                val result = transport.execute(url, method, body, timeoutMs.toInt(), headers)
                val code = result.code
                if (code in 200..299) {
                    return@withContext SystemControlResult.ok(formatResult(code, result.snippet))
                }
                val retryable = code == 0 || code in 500..599 || code == 429
                if (retryable && attempt < policy.maxAttempts) {
                    delay(retryExecutor.delayMs(attempt, policy))
                    continue
                }
                return@withContext if (retryable && policy.maxAttempts > 1) {
                    SystemControlResult.fail(
                        "${formatResult(code, result.snippet)} (after ${policy.maxAttempts} attempts)"
                    )
                } else {
                    SystemControlResult.fail(formatResult(code, result.snippet))
                }
            }
            // Unreachable: RetryPolicy guarantees maxAttempts >= 1.
            SystemControlResult.fail("HTTP failed")
        }
    }

    /**
     * Per-action retry tuning via optional config keys, defaulting to
     * [RetryPolicy] defaults. Keys: `retryAttempts`, `retryBaseDelayMs`,
     * `retryCapMs`.
     */
    private fun retryPolicy(config: Map<String, String>): RetryPolicy {
        val defaults = RetryPolicy()
        return RetryPolicy(
            maxAttempts = config["retryAttempts"]?.toIntOrNull()?.coerceAtLeast(1) ?: defaults.maxAttempts,
            baseDelayMs = config["retryBaseDelayMs"]?.toLongOrNull()?.coerceAtLeast(0) ?: defaults.baseDelayMs,
            capMs = config["retryCapMs"]?.toLongOrNull()?.coerceAtLeast(0) ?: defaults.capMs,
            jitter = defaults.jitter
        )
    }

    private fun formatResult(code: Int, snippet: String): String =
        "HTTP $code" + if (snippet.isNotEmpty()) " - $snippet" else ""

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}

/** The outcome of one HTTP attempt. */
data class HttpAttempt(
    /** HTTP status code, or 0 when the connection itself failed (retryable). */
    val code: Int,
    /** Trimmed response body snippet (empty when there is none). */
    val snippet: String,
)

/** One request attempt — injectable so retry behavior is atomically testable. */
fun interface HttpTransport {
    fun execute(
        url: String,
        method: String,
        body: String,
        timeoutMs: Int,
        headers: Map<String, String>,
    ): HttpAttempt
}

/** Production transport backed by [HttpURLConnection]. */
private class HttpURLConnectionTransport : HttpTransport {

    override fun execute(
        url: String,
        method: String,
        body: String,
        timeoutMs: Int,
        headers: Map<String, String>,
    ): HttpAttempt {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body.isNotEmpty() && method in BODY_METHODS) {
                    doOutput = true
                }
            }
            if (connection.doOutput) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            // Error responses (4xx/5xx) stream via errorStream on some
            // implementations; fall back so failures still report the code.
            val response = try {
                connection.inputStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            } catch (_: IOException) {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            }
            HttpAttempt(code, response.trim().take(80).ifBlank { "" })
        } catch (e: Exception) {
            HttpAttempt(0, e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
