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
 * method and mandatory token) matches a trigger fires that task through the engine.
 *
 * The server is deliberately loopback-only: no external device can reach it,
 * and the mandatory token guards against other local apps. The automation set
 * is refreshed on ACTION_AUTOMATIONS_CHANGED, so disabling the last webhook
 * task stops the socket immediately.
 *
 * Trigger config keys: `path` (default "/"), `method` (POST/GET/ANY),
 * `token` (mandatory shared-secret header `X-NexaFlow-Token` or ?token= query).
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

    private val handlers = java.util.concurrent.Semaphore(8)
    val rejectedAuth = java.util.concurrent.atomic.AtomicLong()
    val rejectedOversize = java.util.concurrent.atomic.AtomicLong()
    val rejectedTimeout = java.util.concurrent.atomic.AtomicLong()
    val rejectedRateLimit = java.util.concurrent.atomic.AtomicLong()
    private var windowStart = 0L
    private var windowRequests = 0
    @Synchronized private fun admit(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - windowStart >= 60_000) { windowStart = now; windowRequests = 0 }
        return ++windowRequests <= 120
    }

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
                if (!admit() || !handlers.tryAcquire()) {
                    rejectedRateLimit.incrementAndGet()
                    client.close()
                    continue
                }
                client.soTimeout = 3_000
                scope.launch(Dispatchers.IO) {
                    try { handleClient(client) } finally { handlers.release() }
                }
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
            val deadline = System.nanoTime() + 3_000_000_000L
            val input = object : java.io.FilterInputStream(client.getInputStream()) {
                override fun read(): Int {
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    if (remaining <= 0) throw java.net.SocketTimeoutException()
                    client.soTimeout = remaining.coerceAtLeast(1).toInt()
                    return super.read()
                }
            }
            val request = WebhookRequestGuard.readRequest(input)
            val fired = dispatch(request.method, request.path, request.token)
            respond(client, if (fired) 200 else 404, if (fired) "OK" else "Not found")
        } catch (_: WebhookRequestGuard.OversizedRequest) {
            rejectedOversize.incrementAndGet()
            respond(client, 413, "Request too large")
        } catch (_: java.net.SocketTimeoutException) {
            rejectedTimeout.incrementAndGet()
            respond(client, 408, "Request timeout")
        } catch (_: Exception) {
            respond(client, 400, "Bad request")
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun dispatch(method: String, path: String, token: String?): Boolean {
        val snapshot = automations
        if (snapshot.isEmpty()) return false
        val now = System.currentTimeMillis()
        var anyFired = false
        var authenticated = false
        WebhookTriggerMatcher.webhookAutomations(snapshot).forEach { automation ->
            val matches = automation.triggers
                .filter { it.type == TriggerType.WEBHOOK }
                .any { WebhookTriggerMatcher.matches(it.config, method, path, token) }
            if (matches) {
                authenticated = true
                var admitted = false
                lastRunAt.compute(automation.id) { _, last ->
                    if (last == null || now - last > automation.cooldownMillis) { admitted = true; now } else last
                }
                if (admitted) {
                    anyFired = true
                    // A webhook is a one-shot event with no opposite callback.
                    executionEngine.runAutomation(automation, completeExitOnFinish = true)
                }
            }
        }
        if (!authenticated) rejectedAuth.incrementAndGet()
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
