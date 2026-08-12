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
 *
 * Step 4 (Appendix A.4.1): when the action configures `outputPath` (a JSONPath
 * into the shared [WorkflowRunContext]), the terminal outcome is published at
 * that path as `{status, body}` — on success **and** failure — so a downstream
 * node can read it via `WorkflowRunContext.get(outputPath)` and branch on
 * `status`. The body in the context is the **full** response body; only the
 * status message is display-truncated.
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
        val outputPath = action.config["outputPath"].orEmpty().trim()
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
            // A single transport call drives both the retry loop and the
            // published outcome, so the final code/body are known here.
            var finalCode = 0
            var finalBody = ""
            val outcome: SystemControlResult = runRetryLoop(
                url = url,
                method = method,
                body = body,
                timeoutMs = timeoutMs.toInt(),
                headers = headers,
                policy = policy
            ) { code, snippet ->
                finalCode = code
                finalBody = snippet
            }
            publishOutput(ctx, outputPath, finalCode, finalBody)
            outcome
        }
    }

    /**
     * Executes attempts with backoff until a terminal outcome is reached.
     * [onAttempt] receives every attempt's code + body so the caller can
     * capture the final one for [WorkflowRunContext] publication.
     */
    private suspend fun runRetryLoop(
        url: String,
        method: String,
        body: String,
        timeoutMs: Int,
        headers: Map<String, String>,
        policy: RetryPolicy,
        onAttempt: (code: Int, body: String) -> Unit,
    ): SystemControlResult {
        for (attempt in 1..policy.maxAttempts) {
            val result = transport.execute(url, method, body, timeoutMs, headers)
            onAttempt(result.code, result.snippet)
            val code = result.code
            if (code in 200..299) {
                return SystemControlResult.ok(formatResult(code, result.snippet))
            }
            val retryable = code == 0 || code in 500..599 || code == 429
            if (retryable && attempt < policy.maxAttempts) {
                delay(retryExecutor.delayMs(attempt, policy))
                continue
            }
            return if (retryable && policy.maxAttempts > 1) {
                SystemControlResult.fail(
                    "${formatResult(code, result.snippet)} (after ${policy.maxAttempts} attempts)"
                )
            } else {
                SystemControlResult.fail(formatResult(code, result.snippet))
            }
        }
        // Unreachable: RetryPolicy guarantees maxAttempts >= 1.
        return SystemControlResult.fail("HTTP failed")
    }

    /**
     * Best-effort publication of the terminal outcome to the shared run
     * context. A write failure (e.g. the 256KB budget) must never turn a
     * successful HTTP call into a failed one, so it is swallowed.
     */
    private fun publishOutput(
        ctx: ActionExecutionContext,
        outputPath: String,
        code: Int,
        body: String,
    ) {
        if (outputPath.isBlank()) return
        val runContext = ctx.runContext ?: return
        runCatching { runContext.put(outputPath, mapOf("status" to code, "body" to body)) }
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
        "HTTP $code" + if (snippet.isNotEmpty()) " - ${snippet.take(80)}" else ""

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
            HttpAttempt(code, response.trim().ifBlank { "" })
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
