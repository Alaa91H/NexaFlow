package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Atomic tests for the [HttpRequestHandler] retry layer: retryable failures
 * (connection, 5xx, 429) retry with backoff up to maxAttempts, permanent
 * failures (4xx) fail immediately, the Idempotency-Key is stable across every
 * attempt of one logical call, and per-action config tunes the policy.
 */
@RunWith(RobolectricTestRunner::class)
class HttpRequestHandlerRetryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Replays a scripted sequence of HTTP codes; captures each attempt's headers. */
    private class ScriptedTransport(vararg codes: Int) : HttpTransport {
        val attempts = mutableListOf<Map<String, String>>()
        private val script = codes.toMutableList()

        override fun execute(
            url: String,
            method: String,
            body: String,
            timeoutMs: Int,
            headers: Map<String, String>,
        ): HttpAttempt {
            attempts += headers
            val code = script.removeAt(0)
            return HttpAttempt(code, if (code == 0) "connection-error" else "resp-$code")
        }
    }

    private fun handler(vararg codes: Int): Pair<HttpRequestHandler, ScriptedTransport> {
        val transport = ScriptedTransport(*codes)
        return HttpRequestHandler(transport = transport) to transport
    }

    private fun controller(): SystemController = SystemController(
        context,
        RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun ctx(automationId: String? = "auto-http") = ActionExecutionContext(
        appContext = context,
        controller = controller(),
        notificationSettings = NotificationSettings(enabled = true, executionEnabled = true),
        automationId = automationId
    )

    private fun action(
        url: String = "https://example.com/api",
        body: String = "",
        extra: Map<String, String> = emptyMap()
    ) = Action(
        type = ActionType.SYSTEM_HTTP_REQUEST,
        config = mapOf("url" to url, "body" to body) + extra
    )

    @Test
    fun successOnFirstAttemptSendsOneRequest() = runBlocking {
        val (handler, transport) = handler(200)
        val result = handler.execute(action(), ctx())
        assertTrue(result.success)
        assertTrue(result.message.startsWith("HTTP 200"))
        assertEquals(1, transport.attempts.size)
    }

    @Test
    fun retriesOn5xxThenSucceeds() = runBlocking {
        val (handler, transport) = handler(500, 503, 200)
        val result = handler.execute(action(), ctx())
        assertTrue(result.success)
        assertTrue(result.message.startsWith("HTTP 200"))
        assertEquals(3, transport.attempts.size)
    }

    @Test
    fun retriesOn429ThenSucceeds() = runBlocking {
        val (handler, transport) = handler(429, 200)
        val result = handler.execute(action(), ctx())
        assertTrue(result.success)
        assertTrue(result.message.startsWith("HTTP 200"))
        assertEquals(2, transport.attempts.size)
    }

    @Test
    fun retriesOnConnectionFailureThenSucceeds() = runBlocking {
        val (handler, transport) = handler(0, 0, 200)
        val result = handler.execute(action(), ctx())
        assertTrue(result.success)
        assertEquals(3, transport.attempts.size)
    }

    @Test
    fun givesUpAfterMaxAttemptsOnPersistent5xx() = runBlocking {
        val (handler, transport) = handler(503, 503, 503)
        val result = handler.execute(action(), ctx())
        assertFalse(result.success)
        assertEquals(3, transport.attempts.size)
        assertTrue(result.message.contains("HTTP 503"))
        assertTrue(result.message.contains("after 3 attempts"))
    }

    @Test
    fun permanent4xxFailsImmediatelyWithoutRetry() = runBlocking {
        val (handler, transport) = handler(404)
        val result = handler.execute(action(), ctx())
        assertFalse(result.success)
        assertEquals(1, transport.attempts.size)
        assertTrue(result.message.startsWith("HTTP 404"))
    }

    @Test
    fun idempotencyKeyIsStableAcrossRetries() = runBlocking {
        val (handler, transport) = handler(500, 500, 200)
        handler.execute(action(), ctx("auto-http"))
        assertEquals(3, transport.attempts.size)
        val keys = transport.attempts.map { it["Idempotency-Key"] }
        assertEquals(1, keys.distinct().size)
        assertTrue(keys.first().isNullOrBlank().not())
    }

    @Test
    fun idempotencyKeyDiffersForDifferentInputs() = runBlocking {
        val transportA = ScriptedTransport(200)
        val transportB = ScriptedTransport(200)
        val handlerA = HttpRequestHandler(transport = transportA)
        val handlerB = HttpRequestHandler(transport = transportB)
        handlerA.execute(action(url = "https://a.example/x"), ctx())
        handlerB.execute(action(url = "https://b.example/y"), ctx())
        assertNotEquals(
            transportA.attempts.first()["Idempotency-Key"],
            transportB.attempts.first()["Idempotency-Key"]
        )
    }

    @Test
    fun noUrlFailsFastWithoutAnyAttempt() = runBlocking {
        val (handler, transport) = handler(200)
        val result = handler.execute(action(url = ""), ctx())
        assertFalse(result.success)
        assertEquals("No URL configured", result.message)
        assertEquals(0, transport.attempts.size)
    }

    @Test
    fun configOverridesMaxAttempts() = runBlocking {
        val (handler, transport) = handler(503, 503, 503, 503, 503)
        val result = handler.execute(action(extra = mapOf("retryAttempts" to "5")), ctx())
        assertFalse(result.success)
        assertEquals(5, transport.attempts.size)
        assertTrue(result.message.contains("after 5 attempts"))
    }

    @Test
    fun singleAttemptConfiguredNeverRetries() = runBlocking {
        val (handler, transport) = handler(503)
        val result = handler.execute(action(extra = mapOf("retryAttempts" to "1")), ctx())
        assertFalse(result.success)
        assertEquals(1, transport.attempts.size)
        // A single configured attempt reports the plain failure, no "(after N)".
        assertTrue(result.message.contains("after 1 attempts").not())
    }
}
