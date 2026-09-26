package com.nexaflow.core.agentapi

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Loopback-only HTTP transport for the versioned agent API.
 *
 * It never binds 0.0.0.0 and never enables CORS. LAN/relay transports are
 * separate future adapters over the same controller.
 */
class AgentApiServer(
    private val controller: AgentApiController,
    private val scope: CoroutineScope,
    private val preferredPort: Int = DEFAULT_PORT
) {
    private val handlers = Semaphore(MAX_CONCURRENT_CLIENTS)

    @Volatile
    private var running = false
    private var job: Job? = null
    private var socket: ServerSocket? = null

    fun initialize() {
        if (running) return
        running = true
        job = scope.launch(Dispatchers.IO) { acceptLoop() }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        currentPort = 0
    }

    private suspend fun acceptLoop() {
        val server = runCatching {
            ServerSocket(preferredPort, SOCKET_BACKLOG, InetAddress.getByName(LOOPBACK))
        }.getOrNull() ?: runCatching {
            ServerSocket(0, SOCKET_BACKLOG, InetAddress.getByName(LOOPBACK))
        }.getOrNull() ?: return

        socket = server
        currentPort = server.localPort
        try {
            while (running && scope.isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!handlers.tryAcquire()) {
                    respond(
                        client,
                        AgentHttpResponse(
                            status = 503,
                            body = """{"error":{"code":"server_busy","message":"Agent API is busy"}}"""
                                .toByteArray(StandardCharsets.UTF_8),
                            headers = mapOf("Content-Type" to "application/json; charset=utf-8")
                        )
                    )
                    runCatching { client.close() }
                    continue
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        handleClient(client)
                    } finally {
                        handlers.release()
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
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val request = AgentHttpRequestParser.read(client.getInputStream())
            respond(client, controller.handle(request))
        } catch (error: AgentHttpProtocolException) {
            respond(
                client,
                AgentHttpResponse(
                    status = error.status,
                    body = """{"error":{"code":"${escape(error.code)}","message":"${escape(error.message.orEmpty())}"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } catch (_: java.net.SocketTimeoutException) {
            respond(
                client,
                AgentHttpResponse(
                    status = 408,
                    body = """{"error":{"code":"request_timeout","message":"Request timed out"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } catch (_: Throwable) {
            respond(
                client,
                AgentHttpResponse(
                    status = 400,
                    body = """{"error":{"code":"bad_request","message":"Request could not be processed"}}"""
                        .toByteArray(StandardCharsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8")
                )
            )
        } finally {
            runCatching { client.close() }
        }
    }

    private fun respond(client: Socket, response: AgentHttpResponse) {
        runCatching {
            val reason = reasonPhrase(response.status)
            val head = buildString {
                append("HTTP/1.1 ").append(response.status).append(' ').append(reason).append("\r\n")
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

    private fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        201 -> "Created"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        414 -> "URI Too Long"
        422 -> "Unprocessable Content"
        428 -> "Precondition Required"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val DEFAULT_PORT = 8766
        private const val LOOPBACK = "127.0.0.1"
        private const val SOCKET_BACKLOG = 8
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_CONCURRENT_CLIENTS = 8

        @Volatile
        var currentPort: Int = 0
            private set
    }
}
