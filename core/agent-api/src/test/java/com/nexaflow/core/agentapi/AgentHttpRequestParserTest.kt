package com.nexaflow.core.agentapi

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentHttpRequestParserTest {

    @Test
    fun parsesBoundedJsonRequest() {
        val body = """{"name":"agent"}"""
        val raw = (
            "POST /api/v1/tasks HTTP/1.1\r\n" +
                "Host: 127.0.0.1:8766\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n\r\n" +
                body
            ).toByteArray()

        val request = AgentHttpRequestParser.read(ByteArrayInputStream(raw))

        assertEquals("POST", request.method)
        assertEquals("/api/v1/tasks", request.target)
        assertEquals("127.0.0.1:8766", request.header("host"))
        assertEquals(body, request.body.decodeToString())
    }

    @Test
    fun rejectsChunkedTransferEncoding() {
        val raw = (
            "POST /api/v1/tasks HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n"
            ).toByteArray()

        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }

        assertEquals(400, error.status)
        assertEquals("unsupported_transfer_encoding", error.code)
    }

    @Test
    fun rejectsOversizedContentLengthBeforeAllocation() {
        val raw = (
            "POST /api/v1/tasks HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "Content-Length: ${AgentHttpRequestParser.MAX_BODY_BYTES + 1}\r\n\r\n"
            ).toByteArray()

        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }

        assertEquals(413, error.status)
        assertEquals("payload_too_large", error.code)
    }

    @Test
    fun rejectsDuplicateContentLength() {
        val raw = (
            "POST /api/v1/tasks HTTP/1.1\r\n" +
                "Host: 127.0.0.1\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Length: 0\r\n\r\n"
            ).toByteArray()

        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }

        assertEquals("duplicate_header", error.code)
    }

    @Test
    fun rejectsNegativeAndNonNumericContentLength() {
        listOf("-1", "one").forEach { length ->
            val raw = (
                "POST /api/v1/tasks HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: $length\r\n\r\n"
                ).toByteArray()
            val error = assertThrows(AgentHttpProtocolException::class.java) {
                AgentHttpRequestParser.read(ByteArrayInputStream(raw))
            }
            assertEquals("invalid_content_length", error.code)
        }
    }

    @Test
    fun rejectsControlCharactersInHeaderValues() {
        val raw = (
            "GET /api/v1/tasks HTTP/1.1\r\n" +
                "Host: localhost\nInjected: yes\r\n\r\n"
            ).toByteArray()
        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }
        assertEquals("bad_header", error.code)
    }

    @Test
    fun rejectsHeaderBlocksOverTheLimit() {
        val raw = ByteArrayInputStream(ByteArray(AgentHttpRequestParser.MAX_HEADER_BYTES + 1) { 65 })
        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(raw)
        }
        assertEquals("headers_too_large", error.code)
    }

    @Test
    fun rejectsRequestWithTooManyHeaders() {
        val raw = buildString {
            append("GET /api/v1/tasks HTTP/1.1\r\n")
            repeat(AgentHttpRequestParser.MAX_HEADERS + 1) { append("X-$it: a\r\n") }
            append("\r\n")
        }.toByteArray()
        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }
        assertEquals("too_many_headers", error.code)
    }

    @Test
    fun rejectsBodyTruncatedBeforeDeclaredLength() {
        val raw = (
            "POST /api/v1/tasks HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Length: 5\r\n\r\nabc"
            ).toByteArray()
        val error = assertThrows(AgentHttpProtocolException::class.java) {
            AgentHttpRequestParser.read(ByteArrayInputStream(raw))
        }
        assertEquals("truncated_body", error.code)
    }
}
