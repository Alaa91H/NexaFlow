package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression contract for a 22:00–06:00-style range: time END uses the exact
 * same ActionRegistry dispatcher as a main action, including a ringer's
 * configured SET_VALUE payload. The handler is deliberately fake so this test
 * verifies orchestration without depending on host DND/audio policy.
 */
@RunWith(RobolectricTestRunner::class)
class TimeRangeRingerEndDispatchTest {

    private lateinit var context: Context
    private lateinit var store: AutomationRuntimeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { store = AutomationRuntimeStore(context).also { it.clear(AUTOMATION_ID) } }
    }

    @Test
    fun `time window end dispatches configured normal ringer value exactly once`() = runBlocking {
        val dispatched = mutableListOf<Action>()
        val automation = testAutomation(AUTOMATION_ID, emptyList()).copy(
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_RINGER_MODE,
                    config = mapOf("mode" to "SILENT"),
                    endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("mode" to "NORMAL"))
                )
            )
        )
        val history = RecordingHistory()
        val engine = testEngineWithRingerHandler(context, history, dispatched)
        val coordinator = ExitCoordinator(store, engine, FakeRepository(listOf(automation)), history)
        assertTrue(
            store.activate(
                AutomationRuntimeState(
                    automationId = AUTOMATION_ID,
                    occurrenceId = OCCURRENCE_ID,
                    source = "time-range",
                    sourceKey = OCCURRENCE_ID,
                    lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                    activatedAt = 22_000L,
                    expectedEndAt = 30_000L,
                    scheduleGeneration = "overnight-generation"
                )
            )
        )

        val outcome = coordinator.requestExit(
            automation = automation,
            reason = ExitReason.TIME_WINDOW_ENDED,
            occurrenceId = OCCURRENCE_ID
        )

        assertTrue(outcome is ExitCoordinatorResult.Executed)
        assertEquals(1, dispatched.size)
        assertEquals(ActionType.SYSTEM_RINGER_MODE, dispatched.single().type)
        assertEquals("NORMAL", dispatched.single().config["mode"])
        assertTrue("completed exit must consume its lifecycle", store.current(AUTOMATION_ID) == null)
    }

    private fun testEngineWithRingerHandler(
        context: Context,
        history: HistoryRepository,
        dispatched: MutableList<Action>
    ): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = history,
        notificationPreferences = com.nexaflow.core.datastore.NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(
            listOf(
                object : ActionHandler {
                    override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_RINGER_MODE)

                    override suspend fun execute(
                        action: Action,
                        ctx: ActionExecutionContext
                    ): SystemControlResult {
                        dispatched += action
                        return SystemControlResult.ok("ringer dispatched")
                    }
                }
            )
        )
    )

    private companion object {
        const val AUTOMATION_ID = "night-silence"
        const val OCCURRENCE_ID = "time:22:00-to-next-06:00"
    }
}
