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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExecutionEngineControlFlowTest {

    private lateinit var context: Context

    private class TestActionHandler : ActionHandler {
        override val supportedTypes: Set<ActionType> = setOf(
            ActionType.SYSTEM_SEND_NOTIFICATION,
            ActionType.SYSTEM_BRIGHTNESS,
            ActionType.SYSTEM_VOLUME
        )
        val executions = mutableListOf<String>()
        val attemptsForType = mutableMapOf<ActionType, Int>()
        val failUntilAttempt = mutableMapOf<ActionType, Int>()

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            executions.add(action.type.name)
            val current = (attemptsForType[action.type] ?: 0) + 1
            attemptsForType[action.type] = current
            val threshold = failUntilAttempt[action.type] ?: 0
            return if (current <= threshold) {
                SystemControlResult.fail("Simulated failure on attempt $current")
            } else {
                SystemControlResult.ok("Success on attempt $current")
            }
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private fun testAutomation(actions: List<Action>): Automation = Automation(
        id = "auto-test",
        name = "Control flow task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = actions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun testEngine(handler: ActionHandler): ExecutionEngine = ExecutionEngine(
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
    fun `action is executed when condition evaluates to true`() = runBlocking {
        val handler = TestActionHandler()
        val engine = testEngine(handler)

        val automation = testAutomation(
            listOf(
                Action(
                    type = ActionType.SYSTEM_SEND_NOTIFICATION,
                    config = mapOf("condition" to "10 < 20")
                )
            )
        )

        val record = engine.runAutomation(automation)
        assertTrue(record.success)
        assertEquals(listOf("SYSTEM_SEND_NOTIFICATION"), handler.executions)
    }

    @Test
    fun `action is skipped when condition evaluates to false`() = runBlocking {
        val handler = TestActionHandler()
        val engine = testEngine(handler)

        val automation = testAutomation(
            listOf(
                Action(
                    type = ActionType.SYSTEM_SEND_NOTIFICATION,
                    config = mapOf("condition" to "10 > 20")
                )
            )
        )

        val record = engine.runAutomation(automation)
        assertTrue(record.success)
        assertTrue(handler.executions.isEmpty())
        assertEquals(1, record.actionResults.size)
        assertTrue(record.actionResults[0].message.contains("Skipped: condition not satisfied"))
    }

    @Test
    fun `action retries on failure up to configured retryCount`() = runBlocking {
        val handler = TestActionHandler()
        handler.failUntilAttempt[ActionType.SYSTEM_BRIGHTNESS] = 2 // Fails attempts 1 and 2, succeeds on attempt 3
        val engine = testEngine(handler)

        val automation = testAutomation(
            listOf(
                Action(
                    type = ActionType.SYSTEM_BRIGHTNESS,
                    config = mapOf(
                        "retryCount" to "2",
                        "retryDelayMs" to "5"
                    )
                )
            )
        )

        val record = engine.runAutomation(automation)
        assertTrue(record.success)
        assertEquals(3, handler.attemptsForType[ActionType.SYSTEM_BRIGHTNESS])
    }

    @Test
    fun `onError ABORT stops remaining actions when action fails`() = runBlocking {
        val handler = TestActionHandler()
        handler.failUntilAttempt[ActionType.SYSTEM_BRIGHTNESS] = 99 // Always fails
        val engine = testEngine(handler)

        val automation = testAutomation(
            listOf(
                Action(
                    type = ActionType.SYSTEM_BRIGHTNESS,
                    config = mapOf("onError" to "ABORT")
                ),
                Action(
                    type = ActionType.SYSTEM_VOLUME,
                    config = emptyMap()
                )
            )
        )

        val record = engine.runAutomation(automation)
        assertFalse(record.success)
        assertEquals(listOf("SYSTEM_BRIGHTNESS"), handler.executions)
        assertEquals(1, record.actionResults.size)
    }

    @Test
    fun `onError CONTINUE proceeds with remaining actions when action fails`() = runBlocking {
        val handler = TestActionHandler()
        handler.failUntilAttempt[ActionType.SYSTEM_BRIGHTNESS] = 99 // Always fails
        val engine = testEngine(handler)

        val automation = testAutomation(
            listOf(
                Action(
                    type = ActionType.SYSTEM_BRIGHTNESS,
                    config = mapOf("onError" to "CONTINUE")
                ),
                Action(
                    type = ActionType.SYSTEM_VOLUME,
                    config = emptyMap()
                )
            )
        )

        val record = engine.runAutomation(automation)
        assertFalse(record.success) // Overall record reflects that at least one action failed
        assertEquals(listOf("SYSTEM_BRIGHTNESS", "SYSTEM_VOLUME"), handler.executions)
        assertEquals(2, record.actionResults.size)
    }
}
