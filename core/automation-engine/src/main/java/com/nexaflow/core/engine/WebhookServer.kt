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
import java.util.concurrent.Semaphore
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
 * The server is deliberately loopback-only: no external device can reach it.
 * The optional per-trigger token guards against other local apps and is only
 * accepted via the [WebhookRequestGuard.TOKEN_HEADER] header — never the
 * query string, which leaks into logs and diagnostics.
 *
 * Hardening (all enforced before any matching runs):
 *  - bounded request line / header count / header sizes / body drain;
 *  - [Socket.soTimeout] so slow-loris style clients cannot hold sockets;
 *  - a concurrency [Semaphore] capping simultaneous client handlers;
 *  - an allow-list of HTTP methods (anything else gets 405);
 *  - constant-time secret comparison.
 *
 * The automation set is refreshed on ACTION_AUTOMATIONS_CHANGED, so disabling
 * the last webhook task stops the socket immediately.
 *
 * Trigger config keys: `path` (default "/"), `method` (POST/GET/ANY),
 * `token` (optional shared secret sent as the `X-NexaFlow-Token` header).
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

    /** Diagnostics counters — never include tokens or paths. */
    val rejectedAuth = java.util.concurrent.atomic.AtomicLong()
    val rejectedOversize = java.util.concurrent.atomic.AtomicLong()
    val rejectedMethod = java.util.concurrent.atomic.AtomicLong()

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    /** Caps concurrent client handlers; extra connections are closed immediately. */
    private val clientSlots = Semaphore(MAX_CONCURRENT_CLIENTS)

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
                when {
                    clientSlots.tryAcquire() -> scope.launch(Dispatchers.IO) {
                        try {
                            handleClient(client)
                        } finally {
                            clientSlots.release()
                        }
                    }
                    else -> runCatching { respond(client, 503, "Busy") }.also { runCatching { client.close() } }
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
            client.soTimeout = READ_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))

            val requestLine = runCatching { reader.readLine() }.getOrNull()
            val headerLines = readHeaderLines(reader)

            when (val parsed = WebhookRequestGuard.parse(requestLine, headerLines)) {
                is WebhookRequestGuard.Parsed.Rejected -> {
                    when {
                        parsed.code == 405 -> rejectedMethod.incrementAndGet()
                        parsed.code == 431 -> rejectedOversize.incrementAndGet()
                    }
                    respond(client, parsed.code, parsed.reason)
                }
                is WebhookRequestGuard.Parsed.Request -> {
                    val fired = dispatch(parsed.method, parsed.path, parsed.headerToken)
                    respond(client, if (fired) 200 else 404, if (fired) "OK" else "Not found")
                }
            }
        } catch (_: Throwable) {
            runCatching { respond(client, 500, "Internal error") }
        } finally {
            runCatching { client.close() }
        }
    }

    /** Reads at most [WebhookRequestGuard.MAX_HEADER_LINES] header lines. */
    private fun readHeaderLines(reader: BufferedReader): List<String> {
        val lines = ArrayList<String>(WebhookRequestGuard.MAX_HEADER_LINES)
        while (lines.size < WebhookRequestGuard.MAX_HEADER_LINES + 1) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: break
            if (line.isBlank()) break
            lines.add(line)
            // Guard the drain: an oversized header block stops here; the
            // guard still rejects on count/size before any matching.
        }
        return lines
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
                    // A webhook is a one-shot event with no opposite callback.
                    executionEngine.runAutomation(automation, completeExitOnFinish = true)
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

        /** Per-client read timeout — slow-loris clients are cut off. */
        const val READ_TIMEOUT_MS: Int = 10_000

        /** Max simultaneous client handlers; excess connections get 503. */
        const val MAX_CONCURRENT_CLIENTS: Int = 8
    }
}
