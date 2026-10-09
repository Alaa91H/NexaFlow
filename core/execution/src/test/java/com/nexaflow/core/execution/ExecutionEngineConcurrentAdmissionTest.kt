package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the process-local single-flight contract at the ExecutionEngine boundary.
 * Monitors may race, but one automation must never have two action chains live
 * at the same time in the singleton engine.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineConcurrentAdmissionTest {

    private class BlockingHandler : ActionHandler {
        private val count = AtomicInteger()
        val calls: Int get() = count.get()
        val entered = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<Unit>()

        override val supportedTypes: Set<ActionType> =
            setOf(ActionType.SYSTEM_SEND_NOTIFICATION)

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext,
        ): SystemControlResult {
            count.incrementAndGet()
            entered.complete(Unit)
            release.await()
            return SystemControlResult.ok("ok")
        }

        fun unblock() {
            release.complete(Unit)
        }
    }

    private class CountingHandler : ActionHandler {
        private val count = AtomicInteger()
        val calls: Int get() = count.get()

        override val supportedTypes: Set<ActionType> =
            setOf(ActionType.SYSTEM_SEND_NOTIFICATION)

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext,
        ): SystemControlResult {
            count.incrementAndGet()
            return SystemControlResult.ok("ok")
        }
    }

    private class RecordingHistory : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()

        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    private fun automation(id: String = "single-flight"): Automation = Automation(
        id = id,
        name = "Single flight",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(
            Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "run"))
        ),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun configuredCooldownSkipsBeforeActionAndCheckpoint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = automation("temporal-cooldown").copy(
            triggers = listOf(Trigger(TriggerType.SMS, mapOf("cooldownMs" to "60000")))
        )
        val store = ActiveExecutionStore(context)
        store.clear(task.id)
        val handler = CountingHandler()
        val blockedRunId = "temporal-cooldown-blocked"
        var monotonicNow = 10L
        val engine = ExecutionEngine(
            context = context,
            historyRepository = RecordingHistory(),
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = store,
        )
        engine.temporalFilterPolicy.clock = { monotonicNow }
        val first = engine.runAutomation(task, triggerOccurrence = TriggerOccurrence.single(0, 100L, "sms", "first"))
        val second = engine.runAutomation(
            task,
            runContext = WorkflowRunContext(blockedRunId, task.id, 101L),
            triggerOccurrence = TriggerOccurrence.single(0, 101L, "sms", "second"),
        )
        monotonicNow = 60_010L
        val afterCooldown = engine.runAutomation(
            task,
            triggerOccurrence = TriggerOccurrence.single(0, 60_100L, "sms", "third"),
        )
        assertTrue(first.success)
        assertTrue(second.message.contains("COOLDOWN"))
        assertTrue("afterCooldown: ${afterCooldown.message}", afterCooldown.success)
        assertFalse("afterCooldown did not execute actions: ${afterCooldown.message}", afterCooldown.actionResults.isEmpty())
        assertEquals(2, handler.calls)
        assertNull(store.checkpoint(blockedRunId))
        store.clear(task.id)
    }

    @Test
    fun concurrentCallbackForSameAutomationIsSkippedAndLeaseIsReleasedAfterCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = automation()
        val activeStore = ActiveExecutionStore(context)
        activeStore.clear(task.id)

        val handler = BlockingHandler()
        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = activeStore,
        )

        val first = async { engine.runAutomation(task) }
        handler.entered.await()

        val concurrent = engine.runAutomation(task)

        assertEquals(1, handler.calls)
        assertTrue(concurrent.success)
        assertTrue(concurrent.actionResults.isEmpty())
        assertTrue(concurrent.message.contains("already running"))

        handler.unblock()
        val firstRecord = first.await()
        assertTrue(firstRecord.success)

        val afterCompletion = engine.runAutomation(task)

        assertEquals(2, handler.calls)
        assertFalse(afterCompletion.message.contains("already running"))
        assertTrue(afterCompletion.success)

        activeStore.clear(task.id)
    }

    @Test
    fun burstOfConcurrentCallbacksExecutesOnlyOneActionChain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = automation("single-flight-burst")
        val activeStore = ActiveExecutionStore(context)
        activeStore.clear(task.id)

        val handler = BlockingHandler()
        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = activeStore,
        )

        val first = async { engine.runAutomation(task) }
        handler.entered.await()

        val contenders = List(32) {
            async { engine.runAutomation(task) }
        }.awaitAll()

        assertEquals(1, handler.calls)
        assertTrue(contenders.all { it.message.contains("already running") })
        assertTrue(contenders.all { it.actionResults.isEmpty() })

        handler.unblock()
        assertTrue(first.await().success)

        activeStore.clear(task.id)
    }


    @Test
    fun sequentialReplayOfSameIdentifiedOccurrenceDoesNotRepeatActions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = automation("occurrence-replay").copy(
            triggers = listOf(Trigger(TriggerType.SMS, mapOf("contains" to "go")))
        )
        val activeStore = ActiveExecutionStore(context)
        activeStore.clear(task.id)

        val handler = CountingHandler()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = RecordingHistory(),
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = activeStore,
        )
        val occurrence = TriggerOccurrence.single(
            triggerIndex = 0,
            occurredAtEpochMs = 100L,
            sourceId = "test-event-source",
            eventId = "physical-event-1",
        )

        val first = engine.runAutomation(task, triggerOccurrence = occurrence)
        val replay = engine.runAutomation(task, triggerOccurrence = occurrence)
        val next = engine.runAutomation(
            task,
            triggerOccurrence = occurrence.copy(
                occurredAtEpochMs = 101L,
                eventId = "physical-event-2",
            ),
        )
        val restartedEngine = ExecutionEngine(
            context = context,
            historyRepository = RecordingHistory(),
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = activeStore,
        )
        val replayAfterEngineRecreation = restartedEngine.runAutomation(
            task,
            triggerOccurrence = occurrence.copy(
                occurredAtEpochMs = 102L,
                eventId = "physical-event-2",
            ),
        )
        val distinctAfterEngineRecreation = restartedEngine.runAutomation(
            task,
            triggerOccurrence = occurrence.copy(
                occurredAtEpochMs = 103L,
                eventId = "physical-event-3",
            ),
        )

        assertTrue(first.success)
        assertTrue(replay.message.contains("already processed"))
        assertTrue(replay.actionResults.isEmpty())
        assertTrue(next.success)
        assertTrue(replayAfterEngineRecreation.message.contains("already admitted"))
        assertTrue(replayAfterEngineRecreation.actionResults.isEmpty())
        assertTrue(distinctAfterEngineRecreation.success)
        assertEquals(3, handler.calls)

        activeStore.clear(task.id)
    }

}
