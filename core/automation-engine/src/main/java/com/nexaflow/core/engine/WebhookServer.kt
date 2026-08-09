package com.nexaflow.core.engine

import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ACTION_AUTOMATIONS_CHANGED
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.WEBHOOK_DEFAULT_PORT
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Lightweight loopback HTTP webhook (Tasker-webhook style). While monitoring
 * is active AND at least one enabled automation uses a WEBHOOK trigger, a
 * [ServerSocket] listens on 127.0.0.1:[port]; an HTTP request whose path (and
 * optional method/token) matches a trigger fires that task through the engine.
 *
 * The server is deliberately loopback-only: no external device can reach it,
 * and the optional token guards against other local apps. The automation set
 * is refreshed on ACTION_AUTOMATIONS_CHANGED, so disabling the last webhook
 * task stops the socket immediately.
 *
 * Trigger config keys: `path` (default "/"), `method` (POST/GET/ANY),
 * `token` (optional shared-secret header `X-NexaFlow-Token` or ?token= query).
 */
@Singleton
class WebhookServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var running = false

    @Volatile
    private var automations: List<Automation> = emptyList()

    private val lastRunAt = ConcurrentHashMap<String, Long>()

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }

    fun initialize() {
        if (running) return
        running = true
        runCatching {
            // Internal app broadcast (AUTOMATIONS_CHANGED) — never exported.
            ContextCompat.registerReceiver(
                context, changeReceiver, IntentFilter(ACTION_AUTOMATIONS_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        scope.launch { refresh() }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { context.unregisterReceiver(changeReceiver) }
        shutdownServer()
    }

    private suspend fun refresh() {
        val fresh = runCatching { repository.getAutomations().first() }.getOrDefault(emptyList())
        automations = fresh
        val needsServer = fresh.any { it.enabled && it.triggers.any { t -> t.type == TriggerType.WEBHOOK } }
        if (needsServer) startServer() else shutdownServer()
    }

    private fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) { acceptLoop() }
    }

    private fun shutdownServer() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun acceptLoop() {
        // Prefer the fixed, predictable port; fall back to an ephemeral one if
        // it is taken so a port conflict never kills the webhook silently.
        val socket = withContext(Dispatchers.IO) {
            runCatching {
                ServerSocket(WEBHOOK_DEFAULT_PORT, 4, InetAddress.getByName("127.0.0.1"))
            }.getOrNull() ?: runCatching {
                ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            }.getOrNull()
        } ?: return
        serverSocket = socket
        val port = socket.localPort
        // Surface the port for the UI via a simple static holder.
        currentPort = port
        try {
            while (isActive()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                scope.launch(Dispatchers.IO) { handleClient(client) }
            }
        } catch (_: Throwable) {
            // Socket closed on shutdown — expected.
        } finally {
            runCatching { socket.close() }
            currentPort = 0
        }
    }

    private fun isActive(): Boolean = running && serverJob?.isActive == true

    private suspend fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = runCatching { reader.readLine() }.getOrNull() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                respond(client, 400, "Bad request")
                return
            }
            val method = parts[0].uppercase()
            var path = parts[1]
            var token: String? = null
            val queryIdx = path.indexOf('?')
            if (queryIdx >= 0) {
                val query = path.substring(queryIdx + 1)
                path = path.substring(0, queryIdx)
                token = query.split('&')
                    .firstOrNull { it.startsWith("token=") }
                    ?.substringAfter("=")
            }
            // Read headers (and honor a possible token header); the blank line
            // after the header block ends the loop.
            var headerToken: String? = null
            var headerLine = runCatching { reader.readLine() }.getOrNull()
            while (headerLine != null && headerLine.isNotBlank()) {
                if (headerLine.startsWith("X-NexaFlow-Token:", ignoreCase = true)) {
                    headerToken = headerLine.substringAfter(":").trim()
                }
                headerLine = runCatching { reader.readLine() }.getOrNull()
            }
            val effectiveToken = headerToken ?: token
            // Drain the body so the client sees a complete exchange.
            runCatching { while (reader.ready()) reader.read() }

            val fired = dispatch(method, path, effectiveToken)
            respond(client, if (fired) 200 else 404, if (fired) "OK" else "Not found")
        } catch (_: Throwable) {
            runCatching { respond(client, 500, "Internal error") }
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun dispatch(method: String, path: String, token: String?): Boolean {
        val snapshot = automations
        if (snapshot.isEmpty()) return false
        val now = System.currentTimeMillis()
        var anyFired = false
        WebhookTriggerMatcher.webhookAutomations(snapshot).forEach { automation ->
            val matches = automation.triggers
                .filter { it.type == TriggerType.WEBHOOK }
                .any { WebhookTriggerMatcher.matches(it.config, method, path, token) }
            if (matches) {
                val last = lastRunAt[automation.id] ?: 0L
                if (now - last > automation.cooldownMillis) {
                    lastRunAt[automation.id] = now
                    anyFired = true
                    executionEngine.runAutomation(automation)
                }
            }
        }
        return anyFired
    }

    private fun respond(client: Socket, code: Int, body: String) {
        runCatching {
            val response = "HTTP/1.1 $code ${if (code == 200) "OK" else body}\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n" +
                body
            client.getOutputStream().write(response.toByteArray())
            client.getOutputStream().flush()
        }
    }

    companion object {
        /** The port the loopback server is currently bound to (0 = not running). */
        @Volatile
        var currentPort: Int = 0
            private set
    }
}
