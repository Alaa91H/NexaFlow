package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Step 5 end-to-end through [ExecutionEngine.runAutomation]: node A publishes
 * its outcome to the shared [WorkflowRunContext], node B references it with a
 * `%CTX.<jsonpath>` selector, and the engine resolves the selector against the
 * context (after A ran, before B dispatches) so B receives the actual value.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineContextSelectorTest {

    private lateinit var context: Context

    /** Records every received config; publishes to context when asked. */
    private class NodeHandler : ActionHandler {
        val receivedConfigs = mutableListOf<Map<String, String>>()
        override val supportedTypes: Set<ActionType> =
            setOf(ActionType.SYSTEM_HTTP_REQUEST, ActionType.SYSTEM_SEND_NOTIFICATION)

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            receivedConfigs += action.config
            if (action.config["publish"] == "true") {
                val path = action.config["outputPath"] ?: "$.fetch.result"
                ctx.runContext?.put(
                    path,
                    mapOf("status" to 200, "body" to """{"temp":21.4}""")
                )
            }
            return SystemControlResult.ok("ok")
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private fun automationWith(vararg actions: Action): Automation = Automation(
        id = "auto-ctx-sel",
        name = "Context selector task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = actions.toList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(handler: NodeHandler): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = FakeHistoryRepository(),
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(listOf(handler))
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun nodeBReadsNodeAOutputViaCtxSelector() = runBlocking {
        val handler = NodeHandler()
        val engine = engine(handler)
        // Node A publishes at $.fetch.result; node B's body references it.
        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_HTTP_REQUEST,
                    mapOf("url" to "https://a.example", "outputPath" to "$.fetch.result", "publish" to "true")
                ),
                Action(
                    ActionType.SYSTEM_HTTP_REQUEST,
                    mapOf(
                        "url" to "https://b.example",
                        "body" to """{"prev": %CTX.$.fetch.result.body}"""
                    )
                )
            )
        )

        assertEquals(2, handler.receivedConfigs.size)
        // Node A's config passes through untouched.
        assertEquals("$.fetch.result", handler.receivedConfigs[0]["outputPath"])
        // Node B's %CTX selector resolved against what node A wrote.
        assertEquals(
            """{"prev": {"temp":21.4}}""",
            handler.receivedConfigs[1]["body"]
        )
    }

    @Test
    fun ctxSelectorResolvesScalarStatusForBranching() = runBlocking {
        val handler = NodeHandler()
        val engine = engine(handler)
        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_HTTP_REQUEST,
                    mapOf("url" to "https://a.example", "outputPath" to "$.fetch.result", "publish" to "true")
                ),
                Action(
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    mapOf("title" to "HTTP %CTX.$.fetch.result.status", "text" to "ok")
                )
            )
        )

        assertEquals(2, handler.receivedConfigs.size)
        assertEquals("HTTP 200", handler.receivedConfigs[1]["title"])
    }

    @Test
    fun missingContextPathKeepsPlaceholderForDownstreamGrace() = runBlocking {
        val handler = NodeHandler()
        val engine = engine(handler)
        // Node B references a path node A never wrote.
        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_HTTP_REQUEST,
                    mapOf("url" to "https://a.example", "outputPath" to "$.other", "publish" to "true")
                ),
                Action(
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    mapOf("title" to "prev=%CTX.$.fetch.result.body", "text" to "ok")
                )
            )
        )

        assertEquals(2, handler.receivedConfigs.size)
        assertEquals("prev=%CTX.$.fetch.result.body", handler.receivedConfigs[1]["title"])
    }
}
