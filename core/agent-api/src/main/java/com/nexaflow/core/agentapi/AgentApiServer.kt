package com.nexaflow.core.agentapi

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentApiServer(
    private val controller: AgentApiController,
    private val mcpController: AgentMcpController,
    private val a2aController: AgentA2AController? = null,
    private val hostPolicy: AgentApiHostPolicy,
    private val scope: CoroutineScope,
    private val preferredPort: Int = DEFAULT_PORT
) {
    private val handlers = Semaphore(MAX_CONCURRENT_CLIENTS)
    private val activeByAddress = ConcurrentHashMap<String, Int>()

    @Volatile
    private var running = false

    @Volatile
    private var lanAccessEnabled = false

    private var job: Job? = null
    private var socket: ServerSocket? = null

    @Synchronized
    fun initialize() {
        if (running) return
        hostPolicy.setLanAccessEnabled(lanAccessEnabled)
        lanAccessEnabled = hostPolicy.lanAccessEnabled
        running = true
        job = scope.launch(Dispatchers.IO) { acceptLoop() }
    }

    @Synchronized
    fun setLanAccessEnabled(enabled: Boolean) {
        hostPolicy.setLanAccessEnabled(enabled)
        val permitted = hostPolicy.lanAccessEnabled
        if (lanAccessEnabled == permitted && running) return
        lanAccessEnabled = permitted
        if (running) {
            stopLocked()
            initialize()
        }
    }

    fun isLanAccessEnabled(): Boolean = lanAccessEnabled

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        running = false
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        currentPort = 0
    }

    private suspend fun acceptLoop() {
        // Never trust the preference alone to widen a cleartext listener.
        val bindAddress = LOOPBACK
        val address = InetAddress.getByName(bindAddress)
        val server = runCatching {
            ServerSocket(preferredPort, SOCKET_BACKLOG, address)
        }.getOrNull() ?: runCatching {
            ServerSocket(0, SOCKET_BACKLOG, address)
        }.getOrNull() ?: return

        socket = server
        currentPort = server.localPort
        try {
            while (running && scope.isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!tryAcquire(client)) {
                    respond(
                        client,
                        AgentHttpResponse(
                            503,
                            """{"error":{"code":"server_busy","message":"Agent API is busy"}}"""
                                .toByteArray(StandardCharsets.UTF_8),
                            mapOf("Content-Type" to "application/json; charset=utf-8")
                        )
                    )
                    runCatching { client.close() }
                    continue
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        handleClient(client)
                    } finally {
                        release(client)
                    }
                }
            }
        } finally {
            runCatching { server.close() }
            if (socket === server) socket = null
            currentPort = 0
        }
    }

    private suspend fun handleClient(client: Socket) {
        val deadline = REQUEST_DEADLINE_EXECUTOR.schedule(
            { runCatching { client.close() } },
            REQUEST_DEADLINE_MS,
            TimeUnit.MILLISECONDS
        )
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val request = AgentHttpRequestParser.read(client.getInputStream())
            val target = request.target.substringBefore('?')
            val response = when (target) {
                MCP_PATH -> mcpController.handle(request)
                A2A_PATH -> a2aController?.handle(request)
                    ?: controller.handle(request)
                CARD_PATH -> a2aController?.card("http://127.0.0.1:$currentPort")
                    ?: controller.handle(request)
                else -> controller.handle(request)
            }
            respond(client, response)
        } catch (error: AgentHttpProtocolException) {
            respond(
                client,
                AgentHttpResponse(
                    error.status,
                    """{"error":{"code":"${escape(error.code)}","message":"${escape(error.message.orEmpty())}"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } catch (_: java.net.SocketTimeoutException) {
            respond(
                client,
                AgentHttpResponse(
                    408,
                    """{"error":{"code":"request_timeout","message":"Request timed out"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            respond(
                client,
                AgentHttpResponse(
                    400,
                    """{"error":{"code":"bad_request","message":"Request could not be processed"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } finally {
            deadline.cancel(false)
            runCatching { client.close() }
        }
    }

    private fun tryAcquire(client: Socket): Boolean {
        if (!handlers.tryAcquire()) return false
        val address = client.inetAddress?.hostAddress.orEmpty()
        val allowed = AtomicBoolean(false)
        activeByAddress.compute(address) { _, current ->
            val count = current ?: 0
            if (count >= MAX_CLIENTS_PER_ADDRESS) {
                current
            } else {
                allowed.set(true)
                count + 1
            }
        }
        if (!allowed.get()) handlers.release()
        return allowed.get()
    }

    private fun release(client: Socket) {
        handlers.release()
        val address = client.inetAddress?.hostAddress.orEmpty()
        activeByAddress.computeIfPresent(address) { _, count ->
            if (count <= 1) null else count - 1
        }
    }

    private fun respond(client: Socket, response: AgentHttpResponse) {
        runCatching {
            val head = buildString {
                append("HTTP/1.1 ").append(response.status).append(' ')
                    .append(reasonPhrase(response.status)).append("\r\n")
                response.headers.forEach { (name, value) ->
                    append(name).append(": ").append(value).append("\r\n")
                }
                append("Content-Length: ").append(response.body.size).append("\r\n")
                append("Cache-Control: no-store\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
            client.getOutputStream().apply {
                write(head)
                write(response.body)
                flush()
            }
        }
    }

    private fun reasonPhrase(status: Int) = when (status) {
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        413 -> "Payload Too Large"
        414 -> "URI Too Long"
        422 -> "Unprocessable Content"
        423 -> "Locked"
        428 -> "Precondition Required"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    private fun escape(value: String) =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val DEFAULT_PORT = 8766
        const val MCP_PATH = "/mcp"
        const val A2A_PATH = "/a2a"
        const val CARD_PATH = "/.well-known/agent-card.json"
        private const val LOOPBACK = "127.0.0.1"
        private const val ALL_INTERFACES = "0.0.0.0"
        private const val SOCKET_BACKLOG = 8
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_CONCURRENT_CLIENTS = 8
        private const val MAX_CLIENTS_PER_ADDRESS = 2
        private const val REQUEST_DEADLINE_MS = 15_000L
        private val REQUEST_DEADLINE_EXECUTOR = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "NexaFlow-Agent-RequestDeadline").apply { isDaemon = true }
        }

        @Volatile
        var currentPort: Int = 0
            private set
    }
}
