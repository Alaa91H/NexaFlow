package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends an HTTP request (`SYSTEM_HTTP_REQUEST`). The URL and body support
 * %variable injection (resolved by the engine before dispatch). Runs on the
 * IO dispatcher so a slow endpoint never blocks the calling coroutine.
 */
class HttpRequestHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_HTTP_REQUEST)

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val url = action.config["url"].orEmpty().trim()
        if (url.isEmpty()) return SystemControlResult.fail("No URL configured")
        val method = action.config["method"].orEmpty().uppercase().ifBlank { "GET" }
        val body = action.config["body"].orEmpty()
        val timeoutMs = (action.config["timeoutMs"]?.toLongOrNull() ?: 10_000L).coerceIn(1_000L, 60_000L)
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = timeoutMs.toInt()
                    readTimeout = timeoutMs.toInt()
                    setRequestProperty("User-Agent", "NexaFlow/1.0")
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
                val snippet = response.trim().take(80).ifBlank { "" }
                SystemControlResult.ok("HTTP $code" + if (snippet.isNotEmpty()) " - $snippet" else "")
            } catch (e: Exception) {
                SystemControlResult.fail("HTTP failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private companion object {
        val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
