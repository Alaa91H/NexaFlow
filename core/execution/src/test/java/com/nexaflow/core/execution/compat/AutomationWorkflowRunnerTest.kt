package com.nexaflow.core.execution.compat

import androidx.paging.PagingSource
import com.nexaflow.core.compat.DeviceProfile
import com.nexaflow.core.execution.emptyPagingSource
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.compat.ExecutionProviderType
import com.nexaflow.core.execution.state.StateTransactionStore
import com.nexaflow.core.execution.workflow.ActionExecutor
import com.nexaflow.core.logging.InMemoryLogStore
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationWorkflowRunnerTest {

    private class FakeHistoryRepository : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records.toList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    private class FakeStateStore : StateTransactionStore {
        val captured = mutableListOf<String>()
        val rolledBack = mutableListOf<String>()
        val cleared = mutableListOf<String>()
        override fun capture(automationId: String): Boolean {
            captured += automationId
            return true
        }
        override fun rollback(automationId: String): SystemControlResult {
            rolledBack += automationId
            return SystemControlResult.ok("Restored original state")
        }
        override fun clear(automationId: String) {
            cleared += automationId
        }
    }

    private fun automation(
        actions: List<Action> = emptyList(),
        exitActions: List<Action> = emptyList(),
        revertOnExit: Boolean = false
    ) = Automation(
        id = "auto-1",
        name = "Test task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 2,
        enabled = true,
        triggers = emptyList(),
        actions = actions,
        exitActions = exitActions,
        revertOnExit = revertOnExit,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun runAutomation_executesActionsThroughWorkflowEngine() = runBlocking {
        val log = mutableListOf<String>()
        val history = FakeHistoryRepository()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ ->
                ActionExecutor { action ->
                    log += action.type.name
                    SystemControlResult.ok("ok:${action.type}")
                }
            },
            historyRepository = history,
            stateStore = FakeStateStore()
        )
        val record = runner.runAutomation(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_BRIGHTNESS, emptyMap()),
                    Action(ActionType.SYSTEM_VOLUME, emptyMap())
                )
            )
        )
        assertEquals(listOf("SYSTEM_BRIGHTNESS", "SYSTEM_VOLUME"), log)
        assertTrue(record.success)
        assertTrue(record.message.contains("SYSTEM_BRIGHTNESS"))
        assertEquals(1, history.records.size)
        assertEquals("auto-1", history.records.first().automationId)
    }

    @Test
    fun runAutomation_recordsTimeline() = runBlocking {
        val logStore = InMemoryLogStore()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = FakeHistoryRepository(),
            stateStore = FakeStateStore(),
            logStore = logStore
        )
        runner.runAutomation(
            automation(actions = listOf(Action(ActionType.SYSTEM_GO_HOME, emptyMap())))
        )
        val timeline = logStore.timeline().first()
        assertEquals(1, timeline.size)
        assertEquals("RUN", timeline.first().kind)
        assertTrue(timeline.first().success)
    }

    @Test
    fun runAutomation_failureMarksRecordFailed() = runBlocking {
        val history = FakeHistoryRepository()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ ->
                ActionExecutor { action ->
                    if (action.type == ActionType.SYSTEM_WAIT) SystemControlResult.fail("boom")
                    else SystemControlResult.ok("ok")
                }
            },
            historyRepository = history,
            stateStore = FakeStateStore()
        )
        val record = runner.runAutomation(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_GO_HOME, emptyMap()),
                    Action(ActionType.SYSTEM_WAIT, emptyMap())
                )
            )
        )
        assertFalse(record.success)
        assertTrue(record.message.contains("boom"))
    }

    @Test
    fun runAutomation_recordsSelectedChannel() = runBlocking {
        val history = FakeHistoryRepository()
        val executorChannels = mutableListOf<String?>()
        val runner = AutomationWorkflowRunner(
            executorProvider = { channel ->
                executorChannels += channel?.type?.name
                ActionExecutor { SystemControlResult.ok("ok") }
            },
            historyRepository = history,
            stateStore = FakeStateStore(),
            channelProvider = {
                object : ExecutionProvider {
                    override val type = ExecutionProviderType.ROOT
                    override val baseScore: Int = 80
                    override val supportedCapabilities: Set<RomCapability> = emptySet()
                    override fun isAvailable(profile: DeviceProfile): Boolean = true
                    override fun execute(command: String): SystemControlResult =
                        SystemControlResult.ok("root")
                }
            }
        )
        val record = runner.runAutomation(
            automation(actions = listOf(Action(ActionType.SYSTEM_GO_HOME, emptyMap())))
        )
        assertEquals("ROOT", record.channel)
        assertEquals("ROOT", history.records.first().channel)
        // The channel the executor ran with MUST equal the recorded one
        // (single selection per run — no divergence possible).
        assertEquals(listOf("ROOT"), executorChannels)
    }

    @Test
    fun runAutomation_recordsPerActionResultsWithDuration() = runBlocking {
        val history = FakeHistoryRepository()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ ->
                ActionExecutor { action ->
                    if (action.type == ActionType.SYSTEM_WAIT) SystemControlResult.fail("boom")
                    else SystemControlResult.ok("ok:${action.type}")
                }
            },
            historyRepository = history,
            stateStore = FakeStateStore()
        )
        val record = runner.runAutomation(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_BRIGHTNESS, emptyMap()),
                    Action(ActionType.SYSTEM_WAIT, emptyMap())
                )
            )
        )
        assertEquals(2, record.actionResults.size)
        assertEquals("SYSTEM_BRIGHTNESS", record.actionResults[0].actionType)
        assertTrue(record.actionResults[0].success)
        assertTrue(record.actionResults[0].durationMs >= 0)
        assertEquals("SYSTEM_WAIT", record.actionResults[1].actionType)
        assertFalse(record.actionResults[1].success)
        // Persisted record carries the same timeline.
        assertEquals(record.actionResults, history.records.first().actionResults)
    }

    @Test
    fun runAutomation_noChannel_keepsNull() = runBlocking {
        val history = FakeHistoryRepository()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = history,
            stateStore = FakeStateStore()
        )
        val record = runner.runAutomation(
            automation(actions = listOf(Action(ActionType.SYSTEM_GO_HOME, emptyMap())))
        )
        assertEquals(null, record.channel)
    }

    @Test
    fun runAutomation_revertOnExitCapturesState() = runBlocking {
        val stateStore = FakeStateStore()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = FakeHistoryRepository(),
            stateStore = stateStore
        )
        runner.runAutomation(automation(revertOnExit = true, actions = listOf(Action(ActionType.SYSTEM_GO_HOME, emptyMap()))))
        assertEquals(listOf("auto-1"), stateStore.captured)
    }

    @Test
    fun runExit_revertOnExitRollsBack() = runBlocking {
        val stateStore = FakeStateStore()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = FakeHistoryRepository(),
            stateStore = stateStore
        )
        val record = runner.runExit(automation(revertOnExit = true))
        assertEquals(listOf("auto-1"), stateStore.rolledBack)
        assertTrue(record.success)
        assertTrue(record.message.contains("Restored"))
    }

    @Test
    fun runExit_executesExitActionsWhenNotReverting() = runBlocking {
        val log = mutableListOf<String>()
        val history = FakeHistoryRepository()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ ->
                ActionExecutor { action ->
                    log += action.type.name
                    SystemControlResult.ok("ok:${action.type}")
                }
            },
            historyRepository = history,
            stateStore = FakeStateStore()
        )
        val record = runner.runExit(
            automation(exitActions = listOf(Action(ActionType.SYSTEM_DARK_MODE, emptyMap())))
        )
        assertEquals(listOf("SYSTEM_DARK_MODE"), log)
        assertTrue(record.success)
        assertEquals(1, history.records.size)
    }

    @Test
    fun runExit_nothingToDoRecordsNoExitBehavior() = runBlocking {
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = FakeHistoryRepository(),
            stateStore = FakeStateStore()
        )
        val record = runner.runExit(automation())
        assertTrue(record.success)
        assertEquals("No exit behavior configured", record.message)
    }

    @Test
    fun clearSnapshot_delegatesToStore() {
        val stateStore = FakeStateStore()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ -> ActionExecutor { SystemControlResult.ok("ok") } },
            historyRepository = FakeHistoryRepository(),
            stateStore = stateStore
        )
        runner.clearSnapshot("auto-1")
        assertEquals(listOf("auto-1"), stateStore.cleared)
    }

    @Test
    fun e2e_createRunHistory_smoke() = runBlocking {
        // End-to-end smoke (Phase 4 gate): create -> run -> history via the
        // compatibility mapper + workflow interpreter + history repository.
        val log = mutableListOf<String>()
        val history = FakeHistoryRepository()
        val logStore = InMemoryLogStore()
        val runner = AutomationWorkflowRunner(
            executorProvider = { _ ->
                ActionExecutor { action ->
                    log += action.type.name
                    SystemControlResult.ok("ok")
                }
            },
            historyRepository = history,
            stateStore = FakeStateStore(),
            logStore = logStore
        )
        val automation = automation(
            actions = listOf(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "80")),
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "Hi"))
            )
        )
        val record = runner.runAutomation(automation)
        assertEquals(listOf("SYSTEM_BRIGHTNESS", "SYSTEM_SEND_NOTIFICATION"), log)
        assertTrue(record.success)
        assertEquals(1, history.records.size)
        assertEquals(1, logStore.timeline().first().size)
    }
}
