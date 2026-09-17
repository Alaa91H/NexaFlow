package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.workflow.RetryExecutor
import com.nexaflow.domain.workflow.RetryPolicy
import okhttp3.RequestBody.Companion.toRequestBody
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
 * The transport is injectable for atomic tests; [SecureHttpTransport] is
 * the production default.
 *
 * Step 4 (Appendix A.4.1): when the action configures `outputPath` (a JSONPath
 * into the shared [WorkflowRunContext]), the terminal outcome is published at
 * that path as `{status, body, truncated, contentType, finalUrl, bytesRead}` — on success **and** failure — so a downstream
 * node can read it via `WorkflowRunContext.get(outputPath)` and branch on
 * `status`. Oversized responses fail with an empty body and truncated=true.
 */
class HttpRequestHandler(
    private val retryExecutor: RetryExecutor = RetryExecutor(),
    private val transport: HttpTransport? = null,
) : ActionHandler {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_HTTP_REQUEST)

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val url = action.config["url"].orEmpty().trim()
        if (url.isEmpty()) return SystemControlResult.fail("No URL configured")
        val required = com.nexaflow.domain.security.HttpAccessPolicy.runtimePermissions(action.config, android.os.Build.VERSION.SDK_INT)
        if (required.any { ctx.appContext.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED })
            return SystemControlResult.fail("LOCAL_NETWORK_PERMISSION_REQUIRED")
        val requestTransport = transport ?: SecureHttpTransport(action.config["allowPrivateNetwork"] == "true")
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
            var finalAttempt = HttpAttempt(0, "")
            val outcome: SystemControlResult = runRetryLoop(
                url = url,
                method = method,
                body = body,
                timeoutMs = timeoutMs.toInt(),
                headers = headers,
                policy = policy,
                requestTransport = requestTransport
            ) { result ->
                finalCode = result.code
                finalBody = result.snippet
                finalAttempt = result
            }
            publishOutput(ctx, outputPath, finalCode, finalBody, finalAttempt)
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
        requestTransport: HttpTransport,
        onAttempt: (HttpAttempt) -> Unit,
    ): SystemControlResult {
        for (attempt in 1..policy.maxAttempts) {
            val result = requestTransport.execute(url, method, body, timeoutMs, headers)
            onAttempt(result)
            if (result.truncated) return SystemControlResult.fail("HTTP_RESPONSE_TOO_LARGE")
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
        attempt: HttpAttempt,
    ) {
        if (outputPath.isBlank()) return
        val runContext = ctx.runContext ?: return
        runCatching { runContext.put(outputPath, mapOf("status" to code, "body" to body, "truncated" to attempt.truncated,
            "contentType" to attempt.contentType, "finalUrl" to attempt.finalUrl, "bytesRead" to attempt.bytesRead)) }
    }

    /**
     * Per-action retry tuning via optional config keys, defaulting to
     * [RetryPolicy] defaults. Keys: `retryAttempts`, `retryBaseDelayMs`,
     * `retryCapMs`.
     */
    private fun retryPolicy(config: Map<String, String>): RetryPolicy {
        val defaults = RetryPolicy()
        return RetryPolicy(
            maxAttempts = config["retryAttempts"]?.toIntOrNull()?.coerceIn(1, 5) ?: defaults.maxAttempts,
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
    val truncated: Boolean = false,
    val contentType: String = "",
    val finalUrl: String = "",
    val bytesRead: Int = 0,
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

/** A fresh per-hop client pins validated DNS results, with proxies and implicit redirects disabled. */
internal class SecureHttpTransport(
    private val allowPrivateNetwork: Boolean,
    private val resolve: (String) -> List<java.net.InetAddress> = { java.net.InetAddress.getAllByName(it).toList() },
    private val clientBuilder: () -> okhttp3.OkHttpClient.Builder = { okhttp3.OkHttpClient.Builder() }
) : HttpTransport {
    override fun execute(url: String, method: String, body: String, timeoutMs: Int,
        headers: Map<String, String>): HttpAttempt {
        var current = url
        var requestMethod = method
        var requestBody = body
        return try {
            repeat(6) { hop ->
                val destination = HttpUrlPolicy.inspect(current, allowPrivateNetwork, resolve)
                val client = clientBuilder()
                    .proxy(java.net.Proxy.NO_PROXY)
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String): List<java.net.InetAddress> {
                            require(hostname.equals(destination.uri.host.removeSurrounding("[", "]"), true))
                            return destination.addresses
                        }
                    })
                    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                    .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                try {
                    val payload = if (requestMethod in setOf("POST", "PUT", "PATCH", "DELETE"))
                        requestBody.toRequestBody(null) else null
                    val request = okhttp3.Request.Builder().url(current).method(requestMethod, payload)
                        .apply { headers.forEach { (key, value) -> header(key, value) } }.build()
                    client.newCall(request).execute().use { response ->
                        if (response.code in setOf(301, 302, 303, 307, 308)) {
                            require(hop < 5) { "REDIRECT_LIMIT" }
                            val location = response.header("Location") ?: error("INVALID_REDIRECT")
                            val next = destination.uri.resolve(location)
                            require(next.scheme.equals("https", true)) { "HTTPS_REQUIRED" }
                            current = next.toString()
                            if (response.code == 303 || response.code in setOf(301, 302) && requestMethod == "POST") {
                                requestMethod = "GET"; requestBody = ""
                            }
                        } else {
                            val bytes = java.io.ByteArrayOutputStream()
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(8192)
                                while (bytes.size() <= MAX_RESPONSE_BYTES) {
                                    val n = input.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES + 1 - bytes.size()))
                                    if (n < 0) break
                                    bytes.write(buffer, 0, n)
                                }
                            }
                            val truncated = bytes.size() > MAX_RESPONSE_BYTES
                            return HttpAttempt(response.code, if (truncated) "" else bytes.toString("UTF-8"),
                                truncated, response.header("Content-Type").orEmpty(), current, bytes.size())
                        }
                    }
                } finally {
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
            HttpAttempt(-1, "REDIRECT_LIMIT")
        } catch (_: IllegalArgumentException) {
            HttpAttempt(-1, "HTTP_POLICY_REJECTED")
        } catch (_: java.net.UnknownHostException) {
            HttpAttempt(-1, "DNS_UNRESOLVED")
        } catch (_: Exception) {
            HttpAttempt(0, "HTTP_CONNECTION_FAILED")
        }
    }
    companion object { const val MAX_RESPONSE_BYTES = 128 * 1024 }
}
