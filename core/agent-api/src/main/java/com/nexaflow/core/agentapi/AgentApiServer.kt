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

class AgentApiServer(
    private val controller: AgentApiController,
    private val mcpController: AgentMcpController,
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
            val response = if (request.target.substringBefore('?') == MCP_PATH) {
                mcpController.handle(request)
            } else {
                controller.handle(request)
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
        } catch (_: Throwable) {
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
            runCatching { client.close() }
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
        413 -> "Payload Too Large"
        414 -> "URI Too Long"
        422 -> "Unprocessable Content"
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
        private const val LOOPBACK = "127.0.0.1"
        private const val SOCKET_BACKLOG = 8
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_CONCURRENT_CLIENTS = 8

        @Volatile
        var currentPort: Int = 0
            private set
    }
}
