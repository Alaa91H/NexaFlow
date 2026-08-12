package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Step 4 (Appendix A.4.1): when the HTTP action configures `outputPath`, the
 * terminal outcome is published to the shared [WorkflowRunContext] as
 * `{status, body}` — on success **and** failure — so a downstream node can
 * read it via `WorkflowRunContext.get(outputPath)` and branch on `status`.
 */
@RunWith(RobolectricTestRunner::class)
class HttpRequestHandlerOutputTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Returns one canned attempt; captures the request headers. */
    private class FixedTransport(
        private val code: Int,
        private val body: String,
    ) : HttpTransport {
        val attempts = mutableListOf<Map<String, String>>()

        override fun execute(
            url: String,
            method: String,
            body: String,
            timeoutMs: Int,
            headers: Map<String, String>,
        ): HttpAttempt {
            attempts += headers
            return HttpAttempt(code, this.body)
        }
    }

    /** A downstream node: reads whatever its predecessor published. */
    private class ReaderHandler(
        private val path: String,
        private val onRead: (Any?) -> Unit,
    ) : ActionHandler {
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            onRead(ctx.runContext?.get(path))
            return SystemControlResult.ok("read")
        }
    }

    private fun controller(): SystemController = SystemController(
        context,
        RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun ctx(runContext: WorkflowRunContext? = null, automationId: String? = "auto-http") =
        ActionExecutionContext(
            appContext = context,
            controller = controller(),
            notificationSettings = NotificationSettings(enabled = true, executionEnabled = true),
            automationId = automationId,
            runContext = runContext
        )

    private fun action(
        url: String = "https://example.com/api",
        outputPath: String = "",
        extra: Map<String, String> = emptyMap(),
    ) = Action(
        type = ActionType.SYSTEM_HTTP_REQUEST,
        config = mapOf("url" to url, "outputPath" to outputPath) + extra
    )

    @Test
    fun publishesStatusAndBodyAtOutputPathOnSuccess() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        val transport = FixedTransport(200, """{"temp":21.4}""")
        val handler = HttpRequestHandler(transport = transport)

        val result = handler.execute(
            action(outputPath = "$.fetch.result"),
            ctx(runContext)
        )

        assertTrue("result: ${result.message}", result.success)
        val published = runContext.get("$.fetch.result") as? Map<*, *>
        assertNotNull(published)
        assertEquals(200, published!!["status"])
        assertEquals("""{"temp":21.4}""", published["body"])
        // paths() emits leaf paths only — the published map appears as its keys.
        assertTrue(runContext.paths().contains("$.fetch.result.status"))
        assertTrue(runContext.paths().contains("$.fetch.result.body"))
    }

    @Test
    fun publishesStatusAndBodyOnFailureSoDownstreamCanBranch() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        val transport = FixedTransport(404, "not found")
        val handler = HttpRequestHandler(transport = transport)

        val result = handler.execute(
            action(outputPath = "$.fetch.result"),
            ctx(runContext)
        )

        assertFalse(result.success)
        val published = runContext.get("$.fetch.result") as? Map<*, *>
        assertNotNull(published)
        assertEquals(404, published!!["status"])
        assertEquals("not found", published["body"])
    }

    @Test
    fun fullBodyIsPublishedEvenWhenMessageIsTruncated() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        // 200 chars — longer than the 80-char display truncation.
        val longBody = (1..200).joinToString("") { "x" }
        val transport = FixedTransport(200, longBody)
        val handler = HttpRequestHandler(transport = transport)

        val result = handler.execute(
            action(outputPath = "$.fetch.result"),
            ctx(runContext)
        )

        assertTrue(result.success)
        assertTrue(result.message.length < longBody.length)
        val published = runContext.get("$.fetch.result") as? Map<*, *>
        assertEquals(longBody, published!!["body"])
    }

    @Test
    fun noOutputPathLeavesContextUntouched() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        val handler = HttpRequestHandler(transport = FixedTransport(200, "ok"))

        handler.execute(action(outputPath = ""), ctx(runContext))

        assertTrue(runContext.paths().isEmpty())
        assertNull(runContext.get("$.fetch.result"))
    }

    @Test
    fun noRunContextIsTolerated() = runBlocking {
        val handler = HttpRequestHandler(transport = FixedTransport(200, "ok"))
        val result = handler.execute(action(outputPath = "$.fetch.result"), ctx(runContext = null))
        assertTrue(result.success)
    }

    @Test
    fun nodeBReadsNodeAOutputThroughTheSharedContext() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        // Node A: HTTP request publishing its outcome at $.fetch.result.
        val nodeA = HttpRequestHandler(transport = FixedTransport(200, """{"temp":21.4}"""))
        nodeA.execute(action(outputPath = "$.fetch.result"), ctx(runContext))

        // Node B: a downstream handler reads node A's output via the same path.
        var seen: Any? = null
        val nodeB = ReaderHandler("$.fetch.result") { seen = it }
        nodeB.execute(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap()), ctx(runContext))

        val read = seen as? Map<*, *>
        assertNotNull(read)
        assertEquals(200, read!!["status"])
        assertEquals("""{"temp":21.4}""", read["body"])
        // Node B can branch on the status node A published.
        assertEquals(200, read["status"])
    }

    @Test
    fun nodeBReadsRetriedNodesFinalOutcome() = runBlocking {
        val runContext = WorkflowRunContext.create("auto-http", 42L)
        // Node A retries on 5xx then succeeds — only the terminal outcome lands.
        val nodeA = HttpRequestHandler(transport = FixedTransport(200, "after-retry"))
        nodeA.execute(
            action(
                outputPath = "$.fetch.result",
                extra = mapOf("retryAttempts" to "3")
            ),
            ctx(runContext)
        )

        val published = runContext.get("$.fetch.result") as? Map<*, *>
        assertEquals(200, published!!["status"])
        assertEquals("after-retry", published["body"])
    }
}
