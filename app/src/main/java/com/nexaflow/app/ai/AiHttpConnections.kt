package com.nexaflow.app.ai

import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * The single place where provider transports open an HTTPS connection.
 *
 * Production uses the platform implementation. JVM tests replace [opener] so
 * status handling, error bodies and `Retry-After` propagation can be exercised
 * without a network or a custom TLS trust store.
 */
internal object AiHttpConnections {

    @Volatile
    var opener: (URI) -> HttpsURLConnection = { endpoint ->
        endpoint.toURL().openConnection() as HttpsURLConnection
    }

    fun open(endpoint: URI): HttpsURLConnection = opener(endpoint)
}
