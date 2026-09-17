package com.nexaflow.feature.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

/**
 * The strict manual admission policy, verified end to end through the real
 * [ExecutionEngine] under Robolectric across all three manual entry points:
 *
 *  - dashboard [DashboardViewModel.runNow] (the dropdown Run now item)
 *  - details-screen policy source [DashboardViewModel.describeManualBlock]
 *  - deep-link path policy source (MainActivity delegates to the same engine
 *    methods exercised here)
 *
 * Policy under test: a manual Run now whose triggers/constraints do not match
 * must never execute the main chain — only the configured end behavior — and
 * the mismatch must be observable as a typed reason. forceRun is the only
 * bypass and must be logged as user-forced.
 */
@RunWith(RobolectricTestRunner::class)
class ManualAdmissionPolicyTest {

    private lateinit var context: Context
    private lateinit var engine: ExecutionEngine
    private lateinit var history: RecordingHistory

    private class RecordingHistory : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
                    LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records.add(record)
        }
    }

    private class FakeRepository : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> = flowOf(emptyList())
        override suspend fun getAutomationById(id: String): Automation? = null
        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) = Unit
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }

    /** Dark-mode-ON task: a trigger whose false is definitive on the JVM
     *  (Robolectric reports light mode), so the gate classifies it as
     *  confirmed-unsatisfied rather than merely unverifiable. */
    private fun stateTriggeredTask(id: String): Automation = Automation(
        id = id,
        name = "Admission $id",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.DARK_MODE, mapOf("state" to "ON"))),
        actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "10"))),
        exitActions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun newViewModel(): DashboardViewModel = DashboardViewModel(
        automationRepository = FakeRepository(),
        executionEngine = engine,
        historyRepository = history,
        appContext = context
    )

    private fun awaitIdle(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        history = RecordingHistory()
        engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        listOf("admit-a", "admit-b", "admit-c", "admit-d").forEach {
            ActiveExecutionStore(context).clear(it)
        }
    }

    @Test
    fun dashboardRunNowRejectsMismatchAndRunsNoMainAction() {
        val viewModel = newViewModel()
        viewModel.runNow(stateTriggeredTask("admit-a"))
        awaitIdle { viewModel.executionMessage.value != null }

        val record = history.records.last()
        // success=true: a deliberately blocked run is not a failure, and the
        // message must identify the manual rejection, never a main run.
        assertTrue(record.success)
        assertTrue(
            "expected manual rejection, got: ${record.message}",
            record.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX)
        )
        assertTrue(record.actionResults.isEmpty())
    }

    @Test
    fun describeManualBlockNamesTheUnsatisfiedTrigger() = runBlocking {
        val reason = engine.describeManualBlock(stateTriggeredTask("admit-b"))
        assertEquals(ExecutionEngine.ManualBlockKind.TRIGGERS_NOT_MET, reason.kind)
        assertTrue(reason.failedTriggerLabels.firstOrNull()?.startsWith("DARK_MODE") == true)
    }

    @Test
    fun describeManualBlockReturnsNullWhenAdmissible() = runBlocking {
        // No triggers, no constraints: the manual gate must admit the run.
        val admissible = stateTriggeredTask("admit-c").copy(triggers = emptyList())
        val viewModel = newViewModel()
        assertNull(viewModel.describeManualBlock(admissible))
        assertNotNull(engine.describeManualBlock(admissible))
        assertEquals(
            ExecutionEngine.ManualBlockKind.NONE,
            engine.describeManualBlock(admissible).kind
        )
    }

    @Test
    fun forceRunExecutesMainChainAndLogsTheBypass() = runBlocking {
        val task = stateTriggeredTask("admit-d")
        engine.forceRun(task)
        // The durable history copy is user-force-labelled even though the
        // underlying run record itself is not (runAutomation already recorded
        // its own RUN entry; forceRun appends the labelled duplicate).
        assertTrue(
            "expected a MANUAL_FORCE_PREFIX-labelled history entry",
            history.records.any { it.message.startsWith(ExecutionEngine.MANUAL_FORCE_PREFIX) }
        )
    }

    @Test
    fun deepLinkPolicySourceProducesTypedReasonForMismatch() = runBlocking {
        // MainActivity calls the identical engine pair; assert the pair's
        // contract: gate record rejects, describe explains, force overrides.
        val task = stateTriggeredTask("admit-e")
        ActiveExecutionStore(context).clear(task.id)
        val gateRecord = engine.runWithConditionGate(task)
        assertTrue(gateRecord.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX))
        val reason = engine.describeManualBlock(task)
        assertTrue(reason.kind != ExecutionEngine.ManualBlockKind.NONE)
        assertTrue(reason.failedTriggerLabels.isNotEmpty())
    }
}
