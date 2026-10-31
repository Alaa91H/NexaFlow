package com.nexaflow.app.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/**
 * Answers a provider request with a fixed status, body and headers, and records
 * the request so tests can assert on the URL and headers the transport sent.
 */
internal class FakeHttpsConnection(
    val uri: URI,
    private val status: Int,
    private val body: String,
    private val headers: Map<String, String> = emptyMap(),
    private val contentType: String? = "application/json"
) : HttpsURLConnection(null) {

    val requestHeaders = linkedMapOf<String, String>()
    val requestBody = ByteArrayOutputStream()

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun getCipherSuite(): String = "TLS_FAKE"

    override fun getLocalCertificates(): Array<Certificate>? = null

    override fun getServerCertificates(): Array<Certificate>? = null

    override fun getResponseCode(): Int = status

    override fun getContentType(): String? = contentType

    override fun getContentLengthLong(): Long = body.toByteArray(Charsets.UTF_8).size.toLong()

    override fun getHeaderField(name: String?): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value

    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null) requestHeaders[key] = value.orEmpty()
    }

    override fun getRequestProperty(key: String?): String? = requestHeaders[key]

    override fun setRequestMethod(method: String) = Unit

    override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    override fun getErrorStream(): InputStream? =
        if (status in 200..299) null else ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    override fun getOutputStream(): OutputStream = requestBody
}
